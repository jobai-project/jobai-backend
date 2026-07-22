package com.jobai.backend.domain.search.dto;

/**
 * 검색 결과의 조건 일치 수준.
 *
 * <p>후보 그룹이 어느 단계까지 완화되었는지를 나타낸다.
 * UI에서 "조건을 완화한 결과가 포함되었습니다" 안내에 활용한다.
 */
public enum MatchLevel {
    /** 모든 required 조건 충족 */
    STRICT,
    /**
     * 구조 조건은 유지하되 미확인 구조 정보를 허용.
     * 현재: 경력='미확인' 공고를 추가로 포함.
     * (지역 null은 STRICT에도 포함, 고용형태는 미확인/null 없음)
     */
    UNKNOWN_STRUCTURAL,
    /** semanticRequired 그룹 제거 (exactRequired는 유지) */
    RELAXED_SEMANTIC,
    /** exactRequired 그룹까지 제거 (구조 조건만 유지) */
    RELAXED_EXACT
}
