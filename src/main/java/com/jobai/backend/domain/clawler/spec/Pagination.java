package com.jobai.backend.domain.clawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 페이지네이션. 여러 페이지에 걸친 공고 목록 처리
 *
 * <p>type: "page_number" | "offset" | "none"
 * <ul>
 *   <li>page_number: param 에 페이지 번호(start 부터 +1). 빈 페이지면 종료.</li>
 *   <li>offset: param 에 오프셋(start 부터 +size). 빈 페이지면 종료.</li>
 *   <li>none: 한 번만 요청.</li>
 * </ul>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Pagination {

    private String type = "none";
    private String param;
    private Integer start;     // 기본: page_number=1, offset=0
    private Integer size = 100;
    private Integer maxPages = 50;   // 무한루프 방지 상한

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param; }

    public Integer getStart() { return start; }
    public void setStart(Integer start) { this.start = start; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public Integer getMaxPages() { return maxPages; }
    public void setMaxPages(Integer maxPages) { this.maxPages = maxPages; }

    /** start 기본값: page_number 면 1, offset 이면 0. */
    public int effectiveStart() {
        if (start != null) return start;
        return "offset".equals(type) ? 0 : 1;
    }
}

