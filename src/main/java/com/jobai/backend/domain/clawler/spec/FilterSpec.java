package com.jobai.backend.domain.clawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * <ul>
 *   <li>{@code in}: field 값이 목록에 든 것만 남김(포함).</li>
 *   <li>{@code excludeContains}: field 값에 이 문자열 중 하나가 들어가면 제외(인재풀/Talent Pool 등).</li>
 * </ul>
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FilterSpec {

    private String field = "title";
    private List<Object> in;
    private List<String> excludeContains;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public List<Object> getIn() { return in; }
    public void setIn(List<Object> in) { this.in = in; }

    public List<String> getExcludeContains() { return excludeContains; }
    public void setExcludeContains(List<String> excludeContains) { this.excludeContains = excludeContains; }
}

