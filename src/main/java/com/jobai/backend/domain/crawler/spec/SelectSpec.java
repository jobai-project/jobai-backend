package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

/**
 * embedded_json 전용 선택자. Python collect_embedded_json 의 select 대응.
 *
 * <p>그리팅은 __NEXT_DATA__ 안에 React Query 캐시(queries 배열)가 있고,
 * 그중 queryKey == ["openings"] 인 항목의 state.data 가 공고 배열이다.
 * 단순 경로로는 "조건 맞는 항목 고르기"가 안 되므로 이 선택자를 쓴다.
 *
 * <ul>
 *   <li>array: 탐색할 배열 경로 (예: props.pageProps.dehydratedState.queries)</li>
 *   <li>matchField: 각 항목에서 비교할 필드 (예: queryKey)</li>
 *   <li>matchValue: 일치해야 할 값 (예: ["openings"])</li>
 *   <li>take: 일치한 항목에서 꺼낼 경로 (예: state.data)</li>
 * </ul>
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SelectSpec {

    private String array;
    private String matchField;
    private Object matchValue;   // ["openings"] 처럼 리스트일 수 있어 Object
    private String take;
}