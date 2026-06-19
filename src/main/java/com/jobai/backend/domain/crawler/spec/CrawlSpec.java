package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 크롤링 스펙(YAML) 루트 모델.
 *
 * <p>검증된 YAML(쿠팡·그리팅 등)을 그대로 읽기 위해, YAML 은 snake_case 를 유지하고
 * Jackson 의 {@link PropertyNamingStrategies.SnakeCaseStrategy} 로 camelCase 필드에 매핑한다.
 * (예: YAML {@code source_type} → 필드 {@code sourceType})
 *
 * <p>이번 1단계 범위: source_type=json 목록 수집. detail/embedded/html/POST 는 후속 이슈.
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CrawlSpec {

    private String company;
    private String companyNameKo;
    private String sourceType = "json";   // json | html | embedded_json (현재 json 만 처리)
    private String crawler = "declarative";

    private ListSpec list;
    private Pagination pagination;
    private FilterSpec filter;
    private DetailSpec detail;

    private List<String> required;

    /** 컬럼명 → 소스 경로. 값이 문자열이면 단일 경로, 리스트면 여러 경로를 이어붙임(Lever 본문). */
    private Map<String, Object> fields;

    /** 부가 정보(컬럼 → 경로). 결과의 extra 맵에 담김. */
    private Map<String, Object> extra;

    /** apply_url 조립용. template(레코드 필드로 포맷) 또는 base+field. */
    private Map<String, Object> applyUrl;
}
