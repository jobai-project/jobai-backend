package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 임베딩이 아직 생성되지 않은 공고를 주기적으로 찾아 배치로 임베딩을 생성하는 서비스.
 * 스케줄러로 동작하며, 설정된 batch-size만큼 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingBatchService {

    private final EmbeddingService embeddingService;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final PrivateJobPostingRepository privateJobPostingRepository;
    private final JobPostingRepository jobPostingRepository;

    @Value("${search.embedding.enabled:true}")
    private boolean embeddingEnabled;

    @Value("${search.embedding.batch-size:50}")
    private int batchSize;

    /** 임베딩이 없는 공고를 찾아 배치로 임베딩을 생성한다. */
    @Scheduled(fixedDelayString = "${search.embedding.batch-interval-ms:60000}")
    public void generateMissingEmbeddings() {
        if (!embeddingEnabled) return;

        processPrivatePostings();
        processPublicPostings();
    }

    private void processPrivatePostings() {
        List<Long> ids = jobEmbeddingRepository.findPrivateIdsWithoutEmbedding(batchSize);
        if (ids.isEmpty()) return;

        log.info("임베딩 미생성 Private 공고 {} 건 처리 시작", ids.size());
        int success = 0;
        for (Long id : ids) {
            try {
                PrivateJobPosting posting = privateJobPostingRepository.findById(id).orElse(null);
                if (posting == null) continue;
                embeddingService.embedPrivatePosting(posting);
                success++;
            } catch (Exception e) {
                log.warn("Private 공고 임베딩 실패: id={}", id, e);
            }
        }
        log.info("Private 공고 임베딩 완료: {}/{} 성공", success, ids.size());
    }

    private void processPublicPostings() {
        List<Long> ids = jobEmbeddingRepository.findPublicIdsWithoutEmbedding(batchSize);
        if (ids.isEmpty()) return;

        log.info("임베딩 미생성 Public 공고 {} 건 처리 시작", ids.size());
        int success = 0;
        for (Long id : ids) {
            try {
                PublicJobPosting posting = jobPostingRepository.findById(id)
                        .filter(jp -> jp instanceof PublicJobPosting)
                        .map(jp -> (PublicJobPosting) jp)
                        .orElse(null);
                if (posting == null) continue;
                embeddingService.embedPublicPosting(posting);
                success++;
            } catch (Exception e) {
                log.warn("Public 공고 임베딩 실패: id={}", id, e);
            }
        }
        log.info("Public 공고 임베딩 완료: {}/{} 성공", success, ids.size());
    }
}
