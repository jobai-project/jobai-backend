package com.jobai.backend.domain.search.dto;

import java.util.List;

/**
 * 검색 필수/선호 조건의 개념 그룹.
 *
 * <p>같은 개념의 유의어는 OR, 서로 다른 개념 그룹은 AND로 처리한다.
 *
 * <pre>
 * 예: REMOTE_WORK 그룹 = [재택근무, 원격근무, 리모트, wfh, remote]
 * SQL: (desc LIKE '%재택근무%' OR desc LIKE '%원격근무%' OR ...)
 *
 * KAFKA 그룹 AND REMOTE_WORK 그룹
 * → (title/desc LIKE '%kafka%') AND (desc LIKE '%재택근무%' OR ...)
 * </pre>
 *
 * @param concept 개념 식별자 (KAFKA, REMOTE_WORK, WORK_LIFE_BALANCE 등)
 * @param terms   해당 개념의 유의어/별칭 목록 (OR 처리)
 */
public record RequirementGroup(String concept, List<String> terms) {}
