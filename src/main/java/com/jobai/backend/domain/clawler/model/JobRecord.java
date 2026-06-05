package com.jobai.backend.domain.clawler.model;


import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 수집된 공고 1건.
 *
 * <p>매핑되는 컬럼은 YAML(fields)이 동적으로 정하므로 내부적으로 Map 으로 보관하고,
 * 자주 쓰는 컬럼(job_id, title, description ...)은 편의 게터를 제공한다.
 */
public class JobRecord {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public void put(String key, Object value) { values.put(key, value); }
    public Object get(String key) { return values.get(key); }
    public boolean has(String key) {
        Object v = values.get(key);
        return v != null && !"".equals(v);
    }
    public Map<String, Object> asMap() { return values; }

    // 편의 게터 (표준 컬럼) — 없으면 null
    public Object getJobId() { return values.get("job_id"); }
    public Object getTitle() { return values.get("title"); }
    public Object getDescription() { return values.get("description"); }
    public Object getApplyUrl() { return values.get("apply_url"); }

    @Override
    public String toString() { return values.toString(); }
}
