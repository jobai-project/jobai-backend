package com.jobai.backend.domain.home.constant;

import java.util.List;
import java.util.Map;

/**
 * 프론트엔드 고용형태 필터 라벨(인턴/신입/경력직/계약직)을 실제 DB 컬럼 LIKE 키워드로 매핑한다.
 *
 * <p>공기업(job_postings)은 고용형태가 recrutType(인턴/계약직 계열)과
 * workExperience(신입/경력직 계열) 두 컬럼에 나뉘어 저장되어 있어 컬럼별로 다른 키워드를 쓴다.
 * 사기업(private_job_postings)은 employmentType 하나에 자유 문자열로 들어있어 동일 키워드를 그대로 적용한다.
 * 사기업 데이터는 현재(2026-06 기준) 수집분이 없어 크롤러 데이터가 쌓이면 재검증이 필요하다.
 */
public final class EmploymentTypeAlias {

    private EmploymentTypeAlias() {
    }

    public static final Map<String, List<String>> PUBLIC_RECRUT_TYPE_KEYWORDS = Map.of(
            "인턴", List.of("인턴"),
            "계약직", List.of("계약직", "비정규직")
    );

    public static final Map<String, List<String>> PUBLIC_WORK_EXPERIENCE_KEYWORDS = Map.of(
            "신입", List.of("신입"),
            "경력직", List.of("경력")
    );

    public static final Map<String, List<String>> PRIVATE_EMPLOYMENT_TYPE_KEYWORDS = Map.of(
            "인턴", List.of("인턴"),
            "계약직", List.of("계약직"),
            "신입", List.of("신입"),
            "경력직", List.of("경력")
    );
}
