package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * 목록 요청 설정.
 *
 * <p>이번 단계: url(api 주소), method(어떤 방식으로 요청할지), responsePath, params, headers, recordPath.
 * body(POST JSON) 는 후속 이슈에서 추가.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ListSpec {

    private String url;
    private String method = "GET";

    /** 응답에서 공고 배열까지의 경로. 예: "result", "data.list". 비면 응답 자체가 배열. */
    private String responsePath = "";

    /** 배열의 각 항목에서 한 단계 더 들어갈 때(토스 primary_job 등). */
    private String recordPath;

    private Map<String, Object> params;
    private Map<String, String> headers;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getResponsePath() { return responsePath; }
    public void setResponsePath(String responsePath) { this.responsePath = responsePath; }

    public String getRecordPath() { return recordPath; }
    public void setRecordPath(String recordPath) { this.recordPath = recordPath; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
}