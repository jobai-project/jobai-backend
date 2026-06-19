package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * <ul>
 *   <li>{@code in}: field 값이 목록에 든 것만 남김(포함).</li>
 *   <li>{@code excludeContains}: field 값에 이 문자열 중 하나가 들어가면 제외(인재풀/Talent Pool 등).</li>
 * </ul>
 */
@Getter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class FilterSpec {

    @Setter private String field = "title";
    @Setter private List<Object> in;
    private List<String> excludeContains;

    public void setExcludeContains(List<String> excludeContains) {
        this.excludeContains = (excludeContains == null) ? null :
                excludeContains.stream().filter(s -> s != null).toList();
    }
}

