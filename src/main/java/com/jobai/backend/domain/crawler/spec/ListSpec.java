package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 목록 요청 설정.
 *
 * <p>이번 단계: url(api 주소), method(어떤 방식으로 요청할지), responsePath, params, headers, recordPath.
 * body(POST JSON) 는 후속 이슈에서 추가.
 */
@Getter
@Setter
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

    /** embedded_json: 추출할 <script> 태그의 id (기본 __NEXT_DATA__). */
    private String scriptId;

    /** embedded_json: 배열에서 조건 맞는 항목 고르기 (그리팅 React Query 캐시). */
    private SelectSpec select;

    public String getScriptId() { return scriptId; }
    public void setScriptId(String scriptId) { this.scriptId = scriptId; }

    public SelectSpec getSelect() { return select; }
    public void setSelect(SelectSpec select) { this.select = select; }
}