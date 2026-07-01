package com.jobai.backend.domain.crawler.spec;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * metadata 배열에서 name 매칭으로 value 를 추출하는 설정 (토스 커스텀 필드).
 * primary_job.metadata = [{name, value}, ...] 구조에서, mappings 의 name 과 일치하는
 * 항목의 value 를 해당 컬럼에 채운다.
 */
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class MetadataExtractionSpec {

    /** metadata 배열이 있는 필드 경로 (예: "metadata"). */
    private String sourceField;

    /** 매칭 기준 필드명 (예: "name"). */
    private String matchBy;

    /** 추출할 값 필드명 (예: "value"). */
    private String valueFrom;

    /** 컬럼 → 매칭할 name 값. */
    private Map<String, String> mappings;
}
