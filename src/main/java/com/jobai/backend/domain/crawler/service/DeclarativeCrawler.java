package com.jobai.backend.domain.crawler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.spec.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 선언적 크롤러 — YAML(CrawlSpec) 을 받아 공고 목록을 수집한다.
 *
 * <p>Python {@code collector/engine.py} 의 collect_json / map_record_json / _apply_url /
 * _apply_filter 를 1:1 로 옮긴 것. "Python 엔진 = 실행 가능한 명세"이며, 같은 YAML 로
 * 같은 결과를 내는 것이 목표(검증 테스트로 대조).
 *
 * <p>현재 범위: source_type=json/embedded_json 목록 수집 + 매핑 + extra + apply_url +
 * pagination + filter + detail(json/embedded_json/html) 보강. html 목록·POST body 는 후속.
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

    /** 진입점. json / embedded_json 목록 수집 후, detail 이 켜져 있으면 공고별 상세 보강. */
    public List<JobRecord> collect(CrawlSpec spec) {
        String st = spec.getSourceType();
        List<JobRecord> records;
        if ("json".equals(st)) {
            records = collectJson(spec);
        } else if ("embedded_json".equals(st)) {
            records = collectEmbeddedJson(spec);
        } else {
            throw new UnsupportedOperationException("source_type=" + st + " 은 아직 미지원");
        }
        records = applyFilter(records, spec);

        // 상세 단계: 공고마다 상세 페이지를 한 번 더 호출해 본문 보강.
        // Python collect() 의 detail 루프와 동일 — 공고별 에러는 격리(목록은 보존).
        DetailSpec detail = spec.getDetail();
        if (detail != null && detail.isEnabled()) {
            for (JobRecord rec : records) {
                try {
                    fetchDetail(rec, spec);
                } catch (Exception e) {
                    rec.put("detail_error", e.getMessage());
                }
            }
        }
        return records;
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
        applyMetadataExtraction(rec, raw, spec);
        applyNullValues(rec, spec);
        applyUrl(rec, spec);
        return rec;
    }

    // ---------- embedded_json 목록 수집 (Python collect_embedded_json) ----------
    @SuppressWarnings("unchecked")
    private List<JobRecord> collectEmbeddedJson(CrawlSpec spec) {
        ListSpec ls = spec.getList();

        // 1) HTML 페이지 받기 (GET)
        String html = fetch(ls.getUrl(),
                ls.getParams() != null ? ls.getParams() : Map.of(), ls.getHeaders());

        // 2) <script id="__NEXT_DATA__"> 안의 JSON 추출
        String scriptId = (ls.getScriptId() != null) ? ls.getScriptId() : "__NEXT_DATA__";
        Object data = extractEmbedded(html, scriptId);
        if (data == null) {
            return List.of();   // script 없음 → 빈 결과
        }

        // 3) 공고 배열 꺼내기: select(조건 매칭) 우선, 없으면 response_path
        List<Object> batch;
        if (ls.getSelect() != null) {
            batch = selectFromArray(data, ls.getSelect());
        } else {
            Object b = JsonPathResolver.resolve(data, ls.getResponsePath());
            batch = (b instanceof List) ? (List<Object>) b : List.of();
        }

        // 4) 매핑 (기존 mapRecord 재사용 — JSON 목록과 동일. record_path 도 동일 처리)
        List<JobRecord> out = new ArrayList<>();
        for (Object raw : batch) {
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

    // ---------- __NEXT_DATA__ script 추출 (Python _extract_embedded, Jsoup 사용) ----------
    private Object extractEmbedded(String html, String scriptId) {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        org.jsoup.nodes.Element script = doc.getElementById(scriptId);
        if (script == null) {
            return null;
        }
        String json = script.data();   // <script> 안의 텍스트(JSON)
        try {
            return objectMapper.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            throw new IllegalStateException("__NEXT_DATA__ JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    // ---------- select: 배열에서 조건 맞는 항목의 take 경로 꺼내기 (Python select 분기) ----------
    @SuppressWarnings("unchecked")
    private List<Object> selectFromArray(Object data, SelectSpec sel) {
        Object arrObj = JsonPathResolver.resolve(data, sel.getArray());
        if (!(arrObj instanceof List)) {
            return List.of();
        }
        for (Object item : (List<Object>) arrObj) {
            Object fieldVal = JsonPathResolver.resolve(item, sel.getMatchField());
            if (matchesSelect(fieldVal, sel)) {
                Object taken = JsonPathResolver.resolve(item, sel.getTake());
                return (taken instanceof List) ? (List<Object>) taken : List.of();
            }
        }
        return List.of();
    }

    /**
     * select 매칭: matchPrefix 가 있으면 접두 일치(상세 단계), 없으면 matchValue 정확 일치(목록 단계).
     * Python collect 의 match_prefix / match_value 분기와 동일.
     */
    private boolean matchesSelect(Object fieldVal, SelectSpec sel) {
        if (sel.getMatchPrefix() != null) {
            return matchPrefix(fieldVal, sel.getMatchPrefix());
        }
        return matchEquals(fieldVal, sel.getMatchValue());
    }

    /** queryKey 가 ["openings"] 처럼 리스트라, 리스트끼리도 비교되게(정확 일치). */
    private boolean matchEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);   // Jackson 이 JSON 배열을 List 로 파싱하므로 List.equals 로 비교됨
    }

    /**
     * 접두 일치: fieldVal(리스트)이 prefix(리스트)로 시작하는지.
     * 예: queryKey=["career","getOpeningById",123] 가 prefix=["career","getOpeningById"] 로 시작 → true.
     * Python 의 queryKey[:len(prefix)] == prefix 와 동일.
     */
    @SuppressWarnings("unchecked")
    private boolean matchPrefix(Object fieldVal, Object prefix) {
        if (!(fieldVal instanceof List) || !(prefix instanceof List)) {
            return false;
        }
        List<Object> fv = (List<Object>) fieldVal;
        List<Object> pf = (List<Object>) prefix;
        if (fv.size() < pf.size()) {
            return false;
        }
        for (int i = 0; i < pf.size(); i++) {
            Object a = fv.get(i);
            Object b = pf.get(i);
            if (a == null ? b != null : !a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    // ---------- 상세 단계 (Python fetch_detail) ----------

    /**
     * 공고 1건의 상세 페이지를 호출해 본문 등을 보강한다. record 를 직접 수정한다.
     *
     * <p>URL 결정: detail.url_template(레코드 필드로 조립) 우선, 없으면 url_from 필드값(기본 apply_url).
     * 템플릿에 필요한 필드가 없으면 보강을 건너뛴다(Python KeyError 처리와 동일).
     *
     * <p>분기: source_type 이 json → JSON 응답에서 fields 경로 추출,
     * embedded_json → 상세 HTML 의 __NEXT_DATA__ 에서 select 후 fields 추출(그리팅),
     * 그 외 → html 본문 셀렉터 파싱.
     */
    @SuppressWarnings("unchecked")
    private void fetchDetail(JobRecord rec, CrawlSpec spec) {
        DetailSpec d = spec.getDetail();
        ListSpec ls = spec.getList();
        Map<String, String> headers = (ls != null) ? ls.getHeaders() : null;

        // 1) 상세 URL 결정
        String url;
        if (d.getUrlTemplate() != null && !d.getUrlTemplate().isEmpty()) {
            url = formatTemplate(d.getUrlTemplate(), rec);   // 누락 필드 → null
            if (url == null) {
                return;   // 템플릿에 필요한 필드 없음 → 보강 스킵
            }
        } else {
            String urlFromKey = (d.getUrlFrom() != null) ? d.getUrlFrom() : "apply_url";
            Object v = rec.get(urlFromKey);
            if (v == null || "".equals(v)) {
                return;
            }
            url = String.valueOf(v);
        }

        // 2) 상세 페이지 호출
        String dst = d.getSourceType();
        if ("json".equals(dst)) {
            // JSON 상세: 응답에서 fields 경로의 값을 뽑아 채움 (빈 값은 덮어쓰지 않음)
            String body = fetch(url, Map.of(), headers);
            Object data = parseJson(body);
            applyDetailFields(rec, data, d.getFields());
        } else if ("embedded_json".equals(dst)) {
            // embedded_json 상세: 상세 HTML 의 __NEXT_DATA__ → select(match_prefix) → fields
            String html = fetch(url, Map.of(), headers);
            String scriptId = (d.getScriptId() != null) ? d.getScriptId() : "__NEXT_DATA__";
            Object data = extractEmbedded(html, scriptId);
            if (data == null) {
                return;
            }
            Object target = data;
            if (d.getSelect() != null) {
                target = selectOneFromArray(data, d.getSelect());
                if (target == null) {
                    return;
                }
            }
            applyDetailFields(rec, target, d.getFields());
        } else if ("html".equals(dst) || dst == null || dst.isBlank()) {
            // html 상세: 본문 셀렉터로 description 추출 (source_type 생략 시 기본값)
            String html = fetch(url, Map.of(), headers);
            extractDetailHtml(rec, html, d);
        } else {
            // 오타 등 미지원 값은 조용히 html 로 처리하지 않고 즉시 실패시킨다(collect() 와 동일).
            throw new UnsupportedOperationException("detail.source_type=" + dst + " 은(는) 미지원");
        }
    }

    /** fields(컬럼→경로)를 data 에서 뽑아 레코드에 채움. 빈 값(null/"")은 덮어쓰지 않음(Python 과 동일). */
    private void applyDetailFields(JobRecord rec, Object data, Map<String, Object> fields) {
        if (fields == null) {
            return;
        }
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            Object val = JsonPathResolver.resolveField(data, e.getValue());
            if (val != null && !"".equals(val)) {
                rec.put(e.getKey(), val);
            }
        }
    }

    /**
     * embedded_json 상세용 select: 배열에서 조건 맞는 <b>항목 자체</b>의 take 경로를 꺼낸다.
     * 목록용 selectFromArray 는 배열(공고 목록)을 반환하지만, 상세는 단일 객체(공고 1건의 상세)를 반환한다.
     */
    @SuppressWarnings("unchecked")
    private Object selectOneFromArray(Object data, SelectSpec sel) {
        Object arrObj = JsonPathResolver.resolve(data, sel.getArray());
        if (!(arrObj instanceof List)) {
            return null;
        }
        for (Object item : (List<Object>) arrObj) {
            Object fieldVal = JsonPathResolver.resolve(item, sel.getMatchField());
            if (matchesSelect(fieldVal, sel)) {
                return (sel.getTake() != null && !sel.getTake().isEmpty())
                        ? JsonPathResolver.resolve(item, sel.getTake())
                        : item;
            }
        }
        return null;
    }

    /** html 상세: body_selector 로 본문 전체 텍스트를 description 에 채움 (Python extract_detail). */
    private void extractDetailHtml(JobRecord rec, String html, DetailSpec d) {
        if (d.getBodySelector() == null || d.getBodySelector().isEmpty()) {
            return;
        }
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html);
        org.jsoup.nodes.Element el = doc.selectFirst(d.getBodySelector());
        if (el != null) {
            rec.put("description", el.text());
        }
    }

    // ---------- null_values: 특정 값을 null 로 치환 (예: deadline "2999" → 상시채용) ----------
    private void applyNullValues(JobRecord rec, CrawlSpec spec) {
        Map<String, String> nv = spec.getNullValues();
        if (nv == null || nv.isEmpty()) return;
        for (Map.Entry<String, String> e : nv.entrySet()) {
            Object val = rec.get(e.getKey());
            if (val != null && String.valueOf(val).contains(e.getValue())) {
                rec.put(e.getKey(), null);
            }
        }
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
            sb.append(e.getKey()).append('=').append(String.valueOf(e.getValue()));
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

    /**
     * metadata 배열에서 name 매칭으로 value 를 추출해 레코드에 채운다 (토스).
     * fields 매핑 이후 실행되어, metadata 의 값이 우선 반영된다(빈 값은 안 덮어씀).
     */
    @SuppressWarnings("unchecked")
    private void applyMetadataExtraction(JobRecord rec, Object raw, CrawlSpec spec) {
        MetadataExtractionSpec me = spec.getMetadataExtraction();
        if (me == null || me.getMappings() == null) {
            return;
        }
        Object metaObj = JsonPathResolver.resolve(raw, me.getSourceField());
        if (!(metaObj instanceof List)) {
            return;
        }
        String matchBy = (me.getMatchBy() != null) ? me.getMatchBy() : "name";
        String valueFrom = (me.getValueFrom() != null) ? me.getValueFrom() : "value";

        // name → value 인덱스
        Map<Object, Object> index = new LinkedHashMap<>();
        for (Object item : (List<Object>) metaObj) {
            Object key = JsonPathResolver.resolve(item, matchBy);
            Object val = JsonPathResolver.resolve(item, valueFrom);
            if (key != null) {
                index.put(key, val);
            }
        }
        // 매핑 적용 (빈 값은 덮어쓰지 않음)
        for (Map.Entry<String, String> m : me.getMappings().entrySet()) {
            Object val = index.get(m.getValue());
            if (val != null && !"".equals(val)) {
                rec.put(m.getKey(), val);
            }
        }
    }
}