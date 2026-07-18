package com.jobai.backend.domain.home.repository;

import com.jobai.backend.global.enums.JobCategory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeJobCandidateRepositoryTest {

    private EntityManager entityManager;
    private TypedQuery<Long> countQuery;
    private HomeJobCandidateRepository repository;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        entityManager = Mockito.mock(EntityManager.class);
        countQuery = Mockito.mock(TypedQuery.class);
        repository = new HomeJobCandidateRepository(entityManager);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), Mockito.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
    }

    @Test
    @DisplayName("민간 점수 후보는 실제 점수, 임계값, 공통 유효 카테고리를 모두 조건으로 조회한다")
    void countsOnlyPersistedPrivateScoresInValidCategories() {
        repository.countScoredPrivateCandidates(10L, List.of("서울"), List.of("신입"), 70);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Long.class));
        assertThat(jpql.getValue())
                .contains("FROM PrivateMatchScore s JOIN s.privateJobPosting p")
                .contains("s.resume.id = :resumeId")
                .contains("s.score >= :threshold")
                .contains("p.jobCategory IN :validCategories")
                .contains("LOWER(p.location) LIKE :loc0")
                .contains("LOWER(p.employmentType) LIKE :empKw0");
        verify(countQuery).setParameter("validCategories", JobCategory.matchTargetLabels());
    }

    @Test
    @DisplayName("최신 공기업 후보 개수는 요청 필터와 회원 희망 조건을 DB 쿼리에 함께 적용한다")
    void countsLatestPublicCandidatesWithPreferences() {
        repository.countLatestPublicCandidates(
                List.of("서울"), List.of("신입"), List.of("백엔드"), List.of("수도권")
        );

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Long.class));
        assertThat(jpql.getValue())
                .contains("(p.isClosed IS NULL OR p.isClosed = false)")
                .contains("LOWER(p.workRegion) LIKE :loc0")
                .contains("LOWER(p.jobRole) LIKE :prefJob0")
                .contains("LOWER(p.workRegion) LIKE :prefLocation0");
    }
}
