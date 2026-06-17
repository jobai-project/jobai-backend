package com.jobai.backend.domain.crawler.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import com.jobai.backend.domain.crawler.spec.FilterSpec;
import com.jobai.backend.domain.crawler.spec.ListSpec;
import com.jobai.backend.domain.crawler.spec.Pagination;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 선언적 크롤러 — YAML(CrawlSpec) 을 받아 공고 목록을 수집한다.
 *
 * <p>이번 1단계: source_type=json 목록 수집 + 매핑 + extra + apply_url + pagination + filter + check.
 * detail / embedded_json / html / POST body 는 후속 이슈.
 */
@Component
public class DeclarativeCrawler {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DeclarativeCrawler(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "Mozilla/5.0 (jobai-collector/0.1)")
                .build();
        this.objectMapper = objectMapper;
    }

    /** 진입점. 현재는 json 만 처리. (html/embedded 는 후속) */
    public List<JobRecord> collect(CrawlSpec spec) {
        String st = spec.getSourceType();
        if (!"json".equals(st)) {
            throw new UnsupportedOperationException("source_type=" + st + " 은 아직 미지원(1단계는 json)");
        }
        List<JobRecord> records = collectJson(spec);
        return applyFilter(records, spec);
    }

    // ---------- JSON 목록 수집 (Python collect_json) ----------
    @SuppressWarnings("unchecked")
    private List<JobRecord> collectJson(CrawlSpec spec) {
        ListSpec ls = spec.getList();
        Pagination pg = (spec.getPagination() != null) ? spec.getPagination() : new Pagination();
        String ptype = pg.getType();

        List<Object> rawItems = new ArrayList<>();
        int page = pg.effectiveStart();
        int offset = pg.effectiveStart();
        int guard = 0;
        int maxPages = (pg.getMaxPages() != null) ? pg.getMaxPages() : 50;

        while (true) {
            Map<String, Object> params = new LinkedHashMap<>();
            if (ls.getParams() != null) params.putAll(ls.getParams());
            if ("page_number".equals(ptype)) {
                params.put(pg.getParam(), page);
            } else if ("offset".equals(ptype)) {
                params.put(pg.getParam(), offset);
            }

            String body = fetch(ls.getUrl(), params, ls.getHeaders());
            Object root = parseJson(body);

            Object batchObj = JsonPathResolver.resolve(root, ls.getResponsePath());
            List<Object> batch = (batchObj instanceof List) ? (List<Object>) batchObj : List.of();
            if (batch.isEmpty()) {
                break;
            }
            rawItems.addAll(batch);

            if ("none".equals(ptype) || pg.getParam() == null) {
                break;
            }
            guard++;
            if (guard >= maxPages) break;
            page += 1;
            offset += (pg.getSize() != null ? pg.getSize() : batch.size());
        }

        List<JobRecord> out = new ArrayList<>();
        for (Object raw : rawItems) {
            // record_path: 각 항목에서 한 단계 더(토스 primary_job 등)
            Object eff = raw;
            if (ls.getRecordPath() != null && !ls.getRecordPath().isEmpty()) {
                eff = JsonPathResolver.resolve(raw, ls.getRecordPath());
            }
            if (eff != null) {
                out.add(mapRecord(eff, spec));
            }
        }
        return out;
    }

    // ---------- 레코드 매핑 (Python map_record_json) ----------
    @SuppressWarnings("unchecked")
    private JobRecord mapRecord(Object raw, CrawlSpec spec) {
        JobRecord rec = new JobRecord();
        if (spec.getFields() != null) {
            for (Map.Entry<String, Object> e : spec.getFields().entrySet()) {
                rec.put(e.getKey(), JsonPathResolver.resolveField(raw, e.getValue()));
            }
        }
        if (spec.getExtra() != null && !spec.getExtra().isEmpty()) {
            Map<String, Object> extra = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : spec.getExtra().entrySet()) {
                extra.put(e.getKey(), JsonPathResolver.resolveField(raw, e.getValue()));
            }
            rec.put("extra", extra);
        }
        applyUrl(rec, spec);
        return rec;
    }

    // ---------- apply_url (Python _apply_url) ----------
    @SuppressWarnings("unchecked")
    private void applyUrl(JobRecord rec, CrawlSpec spec) {
        Map<String, Object> au = spec.getApplyUrl();
        if (au == null) return;
        Object template = au.get("template");
        if (template != null) {
            // {field} 를 레코드 값으로 치환. 누락 필드 있으면 건너뜀(Python 과 동일).
            String t = String.valueOf(template);
            String url = formatTemplate(t, rec);
            if (url != null) rec.put("apply_url", url);
            return;
        }
        // base + field 방식
        Object base = au.get("base");
        Object field = au.get("field");
        if (base != null && field != null) {
            Object v = rec.get(String.valueOf(field));
            if (v != null) rec.put("apply_url", base + String.valueOf(v));
        }
    }

    /** "{job_id}" 형태 템플릿을 레코드 값으로 치환. 필요한 키가 없으면 null(스킵). */
    private String formatTemplate(String template, JobRecord rec) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '{') {
                int end = template.indexOf('}', i);
                if (end < 0) { sb.append(template.substring(i)); break; }
                String key = template.substring(i + 1, end);
                Object v = rec.get(key);
                if (v == null) return null;     // 누락 → 스킵(Python 과 동일)
                sb.append(String.valueOf(v));
                i = end + 1;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ---------- filter (Python _apply_filter) ----------
    private List<JobRecord> applyFilter(List<JobRecord> records, CrawlSpec spec) {
        FilterSpec f = spec.getFilter();
        if (f == null) return records;

        List<JobRecord> out = records;
        if (f.getIn() != null) {
            String field = f.getField();
            out = out.stream()
                    .filter(r -> f.getIn().contains(r.get(field)))
                    .toList();
        }
        if (f.getExcludeContains() != null && !f.getExcludeContains().isEmpty()) {
            String field = (f.getField() != null) ? f.getField() : "title";
            out = out.stream()
                    .filter(r -> {
                        Object val = r.get(field);
                        String s = (val instanceof List)
                                ? String.join(" ", ((List<?>) val).stream().map(String::valueOf).toList())
                                : String.valueOf(val == null ? "" : val);
                        String lower = s.toLowerCase();
                        return f.getExcludeContains().stream()
                                .noneMatch(nd -> lower.contains(nd.toLowerCase()));
                    })
                    .toList();
        }
        return out;
    }

    // ---------- HTTP / JSON ----------
    private String fetch(String url, Map<String, Object> params, Map<String, String> headers) {
        String full = appendParams(url, params);
        RestClient.RequestHeadersSpec<?> req = restClient.get().uri(full);
        if (headers != null) {
            for (Map.Entry<String, String> h : headers.entrySet()) {
                req = req.header(h.getKey(), h.getValue());
            }
        }
        return req.retrieve().body(String.class);
    }

    private String appendParams(String url, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return url;
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, Object> e : params.entrySet()) {
            if (!first) sb.append('&');
            String k = URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8);
            String v = URLEncoder.encode(e.getValue() == null ? "" : String.valueOf(e.getValue()), StandardCharsets.UTF_8);
            sb.append(k).append('=').append(v);
            first = false;
        }
        return sb.toString();
    }

    /** 응답을 Object(Map 또는 List 또는 scalar)로 파싱. resolve 가 path="" 면 root 를 그대로 반환. */
    private Object parseJson(String body) {
        try {
            return objectMapper.readValue(body, new TypeReference<Object>() {});
        } catch (Exception e) {
            throw new IllegalStateException("JSON 파싱 실패: " + e.getMessage(), e);
        }
    }
}

