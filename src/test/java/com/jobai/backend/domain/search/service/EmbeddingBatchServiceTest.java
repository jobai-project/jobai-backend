package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmbeddingBatchServiceTest {

    private EmbeddingService embeddingService;
    private JobEmbeddingRepository jobEmbeddingRepository;
    private PrivateJobPostingRepository privateJobPostingRepository;
    private JobPostingRepository jobPostingRepository;
    private ResumesRepository resumesRepository;
    private EmbeddingBatchService batchService;

    @BeforeEach
    void setUp() throws Exception {
        embeddingService = Mockito.mock(EmbeddingService.class);
        jobEmbeddingRepository = Mockito.mock(JobEmbeddingRepository.class);
        privateJobPostingRepository = Mockito.mock(PrivateJobPostingRepository.class);
        jobPostingRepository = Mockito.mock(JobPostingRepository.class);
        resumesRepository = Mockito.mock(ResumesRepository.class);

        batchService = new EmbeddingBatchService(
                embeddingService, jobEmbeddingRepository, privateJobPostingRepository,
                jobPostingRepository, resumesRepository);

        setField("embeddingEnabled", true);
        setField("batchSize", 50);

        // 기본값: 두 소스 모두 대상 없음 (개별 테스트에서 필요한 쪽만 override)
        when(jobEmbeddingRepository.findPrivateIdsWithoutEmbedding(anyInt())).thenReturn(List.of());
        when(jobEmbeddingRepository.findPublicIdsWithoutEmbedding(anyInt())).thenReturn(List.of());
    }

    private void setField(String name, Object value) throws Exception {
        var field = EmbeddingBatchService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(batchService, value);
    }

    private PrivateJobPosting privatePosting(Long id) {
        return PrivateJobPosting.builder().id(id).build();
    }

    private PublicJobPosting publicPosting(Long id) {
        return PublicJobPosting.builder().id(id).build();
    }

    // -------------------------------------------------------
    // enabled 토글
    // -------------------------------------------------------

    @Test
    @DisplayName("embeddingEnabled=false 면 아무 것도 하지 않는다")
    void disabled_doesNothing() throws Exception {
        setField("embeddingEnabled", false);

        batchService.generateMissingEmbeddings();

        verifyNoInteractions(jobEmbeddingRepository, privateJobPostingRepository, jobPostingRepository, embeddingService);
    }

    // -------------------------------------------------------
    // Private 처리
    // -------------------------------------------------------

    @Test
    @DisplayName("Private: 대상 id가 없으면 조회/임베딩을 시도하지 않는다")
    void private_emptyIds_noFurtherCalls() {
        batchService.generateMissingEmbeddings();

        verifyNoInteractions(privateJobPostingRepository);
        verify(embeddingService, never()).embedPrivatePosting(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Private: 대상 id 전부 정상 임베딩된다")
    void private_allSuccess() {
        when(jobEmbeddingRepository.findPrivateIdsWithoutEmbedding(anyInt())).thenReturn(List.of(1L, 2L));
        when(privateJobPostingRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(privatePosting(1L), privatePosting(2L)));

        batchService.generateMissingEmbeddings();

        verify(embeddingService, times(1)).embedPrivatePosting(argThatId(1L));
        verify(embeddingService, times(1)).embedPrivatePosting(argThatId(2L));
    }

    @Test
    @DisplayName("Private: 일부 항목이 실패해도 나머지는 계속 처리되고 예외가 전파되지 않는다")
    void private_partialFailure_continues() {
        when(jobEmbeddingRepository.findPrivateIdsWithoutEmbedding(anyInt())).thenReturn(List.of(1L, 2L));
        when(privateJobPostingRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(privatePosting(1L), privatePosting(2L)));
        doThrow(new RuntimeException("ai-server 연결 실패"))
                .when(embeddingService).embedPrivatePosting(argThatId(1L));

        batchService.generateMissingEmbeddings();

        verify(embeddingService, times(1)).embedPrivatePosting(argThatId(1L));
        verify(embeddingService, times(1)).embedPrivatePosting(argThatId(2L));
    }

    @Test
    @DisplayName("Private: id로 조회되지 않으면 스킵한다")
    void private_notFound_skipped() {
        when(jobEmbeddingRepository.findPrivateIdsWithoutEmbedding(anyInt())).thenReturn(List.of(1L));
        when(privateJobPostingRepository.findAllById(List.of(1L))).thenReturn(List.of());

        batchService.generateMissingEmbeddings();

        verify(embeddingService, never()).embedPrivatePosting(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------
    // Public 처리
    // -------------------------------------------------------

    @Test
    @DisplayName("Public: 대상 id가 없으면 조회/임베딩을 시도하지 않는다")
    void public_emptyIds_noFurtherCalls() {
        batchService.generateMissingEmbeddings();

        verifyNoInteractions(jobPostingRepository);
        verify(embeddingService, never()).embedPublicPosting(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Public: 대상 id 전부 정상 임베딩된다")
    void public_allSuccess() {
        when(jobEmbeddingRepository.findPublicIdsWithoutEmbedding(anyInt())).thenReturn(List.of(10L, 20L));
        when(jobPostingRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(List.of(publicPosting(10L), publicPosting(20L)));

        batchService.generateMissingEmbeddings();

        verify(embeddingService, times(1)).embedPublicPosting(argThatPublicId(10L));
        verify(embeddingService, times(1)).embedPublicPosting(argThatPublicId(20L));
    }

    @Test
    @DisplayName("Public: 일부 항목이 실패해도 나머지는 계속 처리되고 예외가 전파되지 않는다")
    void public_partialFailure_continues() {
        when(jobEmbeddingRepository.findPublicIdsWithoutEmbedding(anyInt())).thenReturn(List.of(10L, 20L));
        when(jobPostingRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(List.of(publicPosting(10L), publicPosting(20L)));
        doThrow(new RuntimeException("차원 불일치"))
                .when(embeddingService).embedPublicPosting(argThatPublicId(10L));

        batchService.generateMissingEmbeddings();

        verify(embeddingService, times(1)).embedPublicPosting(argThatPublicId(10L));
        verify(embeddingService, times(1)).embedPublicPosting(argThatPublicId(20L));
    }

    @Test
    @DisplayName("Public: id로 조회되지 않으면 스킵한다")
    void public_notFound_skipped() {
        when(jobEmbeddingRepository.findPublicIdsWithoutEmbedding(anyInt())).thenReturn(List.of(10L));
        when(jobPostingRepository.findAllById(List.of(10L))).thenReturn(List.of());

        batchService.generateMissingEmbeddings();

        verify(embeddingService, never()).embedPublicPosting(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Public: 조회된 엔티티가 PublicJobPosting이 아니면 스킵한다")
    void public_wrongType_skipped() {
        when(jobEmbeddingRepository.findPublicIdsWithoutEmbedding(anyInt())).thenReturn(List.of(10L));
        when(jobPostingRepository.findAllById(List.of(10L)))
                .thenReturn(List.of(JobPosting.builder().id(10L).build()));

        batchService.generateMissingEmbeddings();

        verify(embeddingService, never()).embedPublicPosting(org.mockito.ArgumentMatchers.any());
    }

    private PrivateJobPosting argThatId(Long id) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && id.equals(p.getId()));
    }

    private PublicJobPosting argThatPublicId(Long id) {
        return org.mockito.ArgumentMatchers.argThat(p -> p != null && id.equals(p.getId()));
    }
}
