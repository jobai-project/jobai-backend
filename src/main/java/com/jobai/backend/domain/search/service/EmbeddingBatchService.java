package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 임베딩이 아직 생성되지 않은 공고/이력서를 배치로 처리하는 서비스.
 * DailyJobScheduler 또는 수동 API에서 호출된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingBatchService {

    private final EmbeddingService embeddingService;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final PrivateJobPostingRepository privateJobPostingRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ResumesRepository resumesRepository;

    @Value("${search.embedding.enabled:true}")
    private boolean embeddingEnabled;

    @Value("${search.embedding.batch-size:50}")
    private int batchSize;

    /** 미생성 건이 0이 될 때까지 반복 실행. max iteration으로 무한루프 방어. */
    private static final int MAX_ITERATIONS = 200;

    /**
     * 임베딩 미생성 공고를 batch-size 단위로 반복 처리하여 전체를 완료한다.
     * 미생성 건이 0이 되거나 MAX_ITERATIONS에 도달하면 종료한다.
     */
    public void generateAllMissingEmbeddings() {
        if (!embeddingEnabled) return;

        log.info("[임베딩 배치] 전체 미생성 공고 임베딩 처리 시작");
        int totalProcessed = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            int processed = processPrivatePostingsBatch() + processPublicPostingsBatch();
            if (processed == 0) break;
            totalProcessed += processed;
            log.info("[임베딩 배치] iteration {}: {}건 처리 (누적 {}건)", i + 1, processed, totalProcessed);
        }

        log.info("[임베딩 배치] 전체 미생성 공고 임베딩 처리 완료: 총 {}건", totalProcessed);
    }

    /** 1회 batch-size만큼 처리. 기존 수동 API 호환용. */
    public void generateMissingEmbeddings() {
        if (!embeddingEnabled) return;

        processPrivatePostingsBatch();
        processPublicPostingsBatch();
    }

    private int processPrivatePostingsBatch() {
        List<Long> ids = jobEmbeddingRepository.findPrivateIdsWithoutEmbedding(batchSize);
        if (ids.isEmpty()) return 0;

        log.info("임베딩 미생성 Private 공고 {} 건 처리 시작", ids.size());
        Map<Long, PrivateJobPosting> postingMap;
        try {
            postingMap = privateJobPostingRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(PrivateJobPosting::getId, Function.identity()));
        } catch (Exception e) {
            log.warn("Private 공고 일괄 조회 실패", e);
            return 0;
        }

        int success = 0;
        for (Long id : ids) {
            try {
                PrivateJobPosting posting = postingMap.get(id);
                if (posting == null) continue;
                embeddingService.embedPrivatePosting(posting);
                success++;
            } catch (Exception e) {
                log.warn("Private 공고 임베딩 실패: id={}", id, e);
            }
        }
        log.info("Private 공고 임베딩 완료: {}/{} 성공", success, ids.size());
        return success;
    }

    private int processPublicPostingsBatch() {
        List<Long> ids = jobEmbeddingRepository.findPublicIdsWithoutEmbedding(batchSize);
        if (ids.isEmpty()) return 0;

        log.info("임베딩 미생성 Public 공고 {} 건 처리 시작", ids.size());
        Map<Long, PublicJobPosting> postingMap;
        try {
            postingMap = jobPostingRepository.findAllById(ids).stream()
                    .filter(jp -> jp instanceof PublicJobPosting)
                    .map(jp -> (PublicJobPosting) jp)
                    .collect(Collectors.toMap(PublicJobPosting::getId, Function.identity()));
        } catch (Exception e) {
            log.warn("Public 공고 일괄 조회 실패", e);
            return 0;
        }

        int success = 0;
        for (Long id : ids) {
            try {
                PublicJobPosting posting = postingMap.get(id);
                if (posting == null) continue;
                embeddingService.embedPublicPosting(posting);
                success++;
            } catch (Exception e) {
                log.warn("Public 공고 임베딩 실패: id={}", id, e);
            }
        }
        log.info("Public 공고 임베딩 완료: {}/{} 성공", success, ids.size());
        return success;
    }

    /**
     * 활성 이력서 중 임베딩이 없는 이력서에 대해 임베딩을 생성한다.
     *
     * @return "성공/전체" 형태의 결과 문자열
     */
    public String generateMissingResumeEmbeddings() {
        if (!embeddingEnabled) {
            return "임베딩 비활성화 상태 — 처리 건너뜀";
        }
        List<Resumes> targets = resumesRepository.findActiveWithoutEmbedding();
        if (targets.isEmpty()) {
            return "임베딩 대상 이력서가 없습니다";
        }

        int success = 0;
        for (Resumes resume : targets) {
            try {
                float[] vector = embeddingService.embedResumeText(resume.getExtractedText());
                resume.updateEmbedding(vector);
                resumesRepository.save(resume);
                success++;
            } catch (Exception e) {
                log.warn("이력서 임베딩 실패: resumeId={}, error={}", resume.getId(), e.getMessage(), e);
            }
        }
        return "이력서 임베딩 완료: " + success + "/" + targets.size() + "건 성공";
    }
}
