package com.jobai.backend.domain.home.repository;

import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
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
    private TypedQuery<Object[]> scoredQuery;
    private TypedQuery<PublicJobPosting> publicQuery;
    private HomeJobCandidateRepository repository;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        entityManager = Mockito.mock(EntityManager.class);
        countQuery = Mockito.mock(TypedQuery.class);
        scoredQuery = Mockito.mock(TypedQuery.class);
        publicQuery = Mockito.mock(TypedQuery.class);
        repository = new HomeJobCandidateRepository(entityManager);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(countQuery);
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(scoredQuery);
        when(entityManager.createQuery(anyString(), eq(PublicJobPosting.class))).thenReturn(publicQuery);
        when(countQuery.setParameter(anyString(), Mockito.any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);
        when(scoredQuery.setParameter(anyString(), Mockito.any())).thenReturn(scoredQuery);
        when(scoredQuery.setMaxResults(Mockito.anyInt())).thenReturn(scoredQuery);
        when(scoredQuery.getResultList()).thenReturn(List.of());
        when(publicQuery.setMaxResults(Mockito.anyInt())).thenReturn(publicQuery);
        when(publicQuery.getResultList()).thenReturn(List.of());
    }

    @Test
    @DisplayName("민간 점수 후보는 실제 점수와 공통 유효 카테고리를 조건으로 조회하고 적합도 기준은 적용하지 않는다")
    void countsOnlyPersistedPrivateScoresInValidCategories() {
        repository.countScoredPrivateCandidates(10L, List.of("서울"), List.of("신입"));

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Long.class));
        assertThat(jpql.getValue())
                .contains("FROM PrivateMatchScore s JOIN s.privateJobPosting p")
                .contains("s.resume.id = :resumeId")
                .doesNotContain("s.score >= :threshold")
                .contains("p.jobCategory IN :validCategories")
                .contains("(p.deadline IS NULL OR p.deadline >= CURRENT_DATE)")
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
                .contains("(p.endDate IS NULL OR p.endDate >= CURRENT_DATE)")
                .contains("LOWER(p.workRegion) LIKE :loc0")
                .contains("LOWER(p.jobRole) LIKE :prefJob0")
                .contains("LOWER(p.workRegion) LIKE :prefLocation0");
    }

    @Test
    @DisplayName("점수 후보 조회는 점수, 생성시각, ID 순으로 정렬하고 요청 범위만 조회한다")
    void ordersScoredCandidatesDeterministically() {
        repository.findScoredPrivateCandidates(10L, null, null, 1_018);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Object[].class));
        assertThat(jpql.getValue()).contains("ORDER BY s.score DESC, p.createdAt DESC, p.id DESC");
        assertThat(jpql.getValue()).contains("(p.deadline IS NULL OR p.deadline >= CURRENT_DATE)");
        verify(scoredQuery).setMaxResults(1_018);
    }

    @Test
    @DisplayName("공기업 점수 추천은 마감일이 지난 공고를 제외한다")
    void excludesExpiredPublicPostingsFromScoredCandidates() {
        repository.findScoredPublicCandidates(10L, null, null, 18);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(Object[].class));
        assertThat(jpql.getValue())
                .contains("(p.isClosed IS NULL OR p.isClosed = false)")
                .contains("(p.endDate IS NULL OR p.endDate >= CURRENT_DATE)");
    }

    @Test
    @DisplayName("홈 최신 공고 목록은 마감일이 지난 공공기관 공고를 제외한다")
    void excludesExpiredPublicPostingsFromLatestFeed() {
        repository.findPublicCandidates(null, null, 18);

        ArgumentCaptor<String> jpql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(jpql.capture(), eq(PublicJobPosting.class));
        assertThat(jpql.getValue())
                .contains("(p.isClosed IS NULL OR p.isClosed = false)")
                .contains("(p.endDate IS NULL OR p.endDate >= CURRENT_DATE)");
    }
}
