package com.jobai.backend.domain.home.service;

import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.dto.LatestJobsResponse;
import com.jobai.backend.domain.home.dto.LatestJobsResponse.LatestJob;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

class LatestJobsServiceTest {

    private HomeJobCandidateRepository candidateRepository;
    private LatestJobsService service;

    @BeforeEach
    void setUp() {
        candidateRepository = Mockito.mock(HomeJobCandidateRepository.class);
        service = new LatestJobsService(candidateRepository);

        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of());
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("companyTypes 미지정 시 공기업/사기업 둘 다 조회한다")
    void companyTypes_미지정시_전체조회() {
        service.getLatestJobs(null, null, null, 0, 18);

        Mockito.verify(candidateRepository).findPublicCandidates(any(), any(), anyInt());
        Mockito.verify(candidateRepository).findPrivateCandidates(any(), any(), anyInt());
    }

    @Test
    @DisplayName("companyTypes=PRIVATE만 지정하면 공기업 리포지토리는 호출하지 않는다")
    void companyTypes_PRIVATE만_지정() {
        service.getLatestJobs(List.of("PRIVATE"), null, null, 0, 18);

        Mockito.verify(candidateRepository).findPrivateCandidates(any(), any(), anyInt());
        Mockito.verify(candidateRepository, Mockito.never()).findPublicCandidates(any(), any(), anyInt());
    }

    @Test
    @DisplayName("공기업/사기업 후보를 최신순(createdAt DESC)으로 병합 정렬하고 matchScore 없이 반환한다")
    void 최신순_병합정렬_점수없음() {
        JobCandidate old = candidate(1L, "PUBLIC", LocalDateTime.of(2026, 1, 1, 0, 0));
        JobCandidate mid = candidate(2L, "PRIVATE", LocalDateTime.of(2026, 3, 1, 0, 0));
        JobCandidate newest = candidate(3L, "PUBLIC", LocalDateTime.of(2026, 6, 1, 0, 0));

        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(List.of(old, newest));
        when(candidateRepository.findPrivateCandidates(any(), any(), anyInt())).thenReturn(List.of(mid));

        LatestJobsResponse response = service.getLatestJobs(null, null, null, 0, 10);

        assertThat(response.jobs()).extracting(LatestJob::id).containsExactly(3L, 2L, 1L);
        assertThat(response.totalCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("offset/size로 페이지네이션하고 hasMore를 계산한다")
    void 페이지네이션() {
        List<JobCandidate> candidates = java.util.stream.LongStream.rangeClosed(1, 20)
                .mapToObj(id -> candidate(id, "PUBLIC", LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(id)))
                .toList();
        when(candidateRepository.findPublicCandidates(any(), any(), anyInt())).thenReturn(candidates);

        LatestJobsResponse firstPage = service.getLatestJobs(null, null, null, 0, 18);
        LatestJobsResponse secondPage = service.getLatestJobs(null, null, null, 18, 18);

        assertThat(firstPage.jobs()).hasSize(18);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(secondPage.jobs()).hasSize(2);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(firstPage.totalCount()).isEqualTo(20L);
    }

    private static JobCandidate candidate(Long id, String source, LocalDateTime createdAt) {
        return new JobCandidate(id, source, "회사" + id, "제목" + id, "서울", "신입", "백엔드", null, createdAt);
    }
}
