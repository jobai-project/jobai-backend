package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 상세(detail) 단계 설정. Python {@code spec["detail"]} 대응.
 *
 * <p>목록 단계만으로 본문(description)이 안 채워지는 회사(그리팅 계열·우아한형제들 등)는
 * 공고별로 상세 페이지를 한 번 더 호출해 본문을 보강한다. 상세 소스 타입은 세 가지다.
 *
 * <ul>
 *   <li>{@code json} — 상세 API(JSON)에서 {@link #fields} 경로의 값을 추출
 *       (우아한형제들: {@code data.recruitContents})</li>
 *   <li>{@code embedded_json} — 상세 HTML 의 {@code __NEXT_DATA__} 에서 {@link #select} 로
 *       항목을 고른 뒤 {@link #fields} 경로의 값을 추출 (그리팅)</li>
 *   <li>{@code html} — 상세 페이지를 셀렉터({@link #bodySelector})로 파싱</li>
 * </ul>
 *
 * <p>상세 URL 은 {@link #urlTemplate}(레코드 필드로 조립, 예 {@code .../o/{job_id}}) 우선,
 * 없으면 {@link #urlFrom} 필드값(기본 {@code apply_url})을 쓴다. Python fetch_detail 과 동일.
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DetailSpec {
    private boolean enabled;
    private String sourceType;     // json | embedded_json | html
    private String urlTemplate;
    private String urlFrom;
    private String scriptId;
    private SelectSpec select;
    private Map<String, Object> fields;
    private String bodySelector;

}
