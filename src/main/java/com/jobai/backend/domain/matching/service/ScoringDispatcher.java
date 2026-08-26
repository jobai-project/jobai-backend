package com.jobai.backend.domain.matching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.notification.entity.Notification;
import com.jobai.backend.domain.notification.repository.NotificationRepository;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.search.entity.JobEmbedding;
import com.jobai.backend.domain.search.repository.JobEmbeddingRepository;
import com.jobai.backend.global.enums.JobCategory;
import com.jobai.backend.global.enums.JobSource;
import com.jobai.backend.global.kafka.event.ScoringRequestEvent;
import com.jobai.backend.global.kafka.producer.KafkaScoringProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Profile("kafka")
@RequiredArgsConstructor
public class ScoringDispatcher {

    private final ResumesRepository resumesRepository;
    private final PrivateJobPostingRepository privateJobPostingRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final NotificationRepository notificationRepository;
    private final KafkaScoringProducer kafkaScoringProducer;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 발행 결과를 담는 record. pipelineRunId로 완료 추적이 가능하다. */
    public record DispatchResult(String pipelineRunId, int dispatched) {}

    /**
     * 새 pipelineRunId를 생성하여 스코어링 이벤트를 발행한다.
     * 수동 트리거(벤치마크) 등 독립 실행 시 사용한다.
     */
    public DispatchResult dispatchPrivateScoring() {
        return dispatchPrivateScoring(UUID.randomUUID().toString());
    }

    /**
     * 활성 이력서 × 활성 공고 중 신규/변경분을 필터링하여
     * Kafka 스코어링 요청 이벤트로 발행한다.
     * 파이프라인 경로에서 호출 시 상위 pipelineRunId를 전달받아 추적을 유지한다.
     *
     * @param pipelineRunId 파이프라인 추적용 ID
     * @return 발행 결과 (pipelineRunId + 발행 건수)
     */
    public DispatchResult dispatchPrivateScoring(String pipelineRunId) {
        List<Resumes> activeResumes = resumesRepository.findAllActiveWithEmbedding();
        if (activeResumes.isEmpty()) {
            log.info("[ScoringDispatcher] 활성 이력서 없음 — 스킵");
            return new DispatchResult(null, 0);
        }

        List<PrivateJobPosting> activePostings =
                privateJobPostingRepository.findActiveByValidCategories(JobCategory.matchTargetLabels());
        if (activePostings.isEmpty()) {
            log.info("[ScoringDispatcher] 활성 공고 없음 — 스킵");
            return new DispatchResult(null, 0);
        }

        // 공고별 임베딩 미리 조회
        Map<Long, JobEmbedding> embeddingMap = jobEmbeddingRepository
                .findBySourceAndSourceIdIn(JobSource.PRIVATE,
                        activePostings.stream().map(PrivateJobPosting::getId).toList())
                .stream()
                .collect(Collectors.toMap(JobEmbedding::getSourceId, e -> e));

        // 1단계: 발행 대상을 먼저 수집
        record DispatchEntry(Resumes resume, PrivateJobPosting posting, JobEmbedding embedding,
                             String email, List<String> resumeSkills, List<Double> resumeVec,
                             int experienceYears, int threshold) {}

        List<DispatchEntry> entries = new ArrayList<>();

        for (Resumes resume : activeResumes) {
            String email = resume.getMember().getEmail();
            int threshold = notificationRepository.findByMemberEmail(email)
                    .map(Notification::getMatchScoreThreshold)
                    .orElse(70);

            Set<Long> existingPostingIds = privateMatchScoreRepository.findByResumeId(resume.getId())
                    .stream()
                    .map(s -> s.getPrivateJobPosting().getId())
                    .collect(Collectors.toSet());

            List<String> resumeSkills = parseSkills(resume.getResumeSkills());
            List<Double> resumeVec = toDoubleList(resume.getEmbedding());
            int experienceYears = resume.getExperienceYears() != null ? resume.getExperienceYears() : 0;

            for (PrivateJobPosting posting : activePostings) {
                if (existingPostingIds.contains(posting.getId())) continue;
                JobEmbedding embedding = embeddingMap.get(posting.getId());
                if (embedding == null) continue;
                entries.add(new DispatchEntry(resume, posting, embedding,
                        email, resumeSkills, resumeVec, experienceYears, threshold));
            }
        }

        if (entries.isEmpty()) {
            log.info("[ScoringDispatcher] 발행 대상 없음 — 스킵");
            return new DispatchResult(pipelineRunId, 0);
        }

        // 2단계: Redis에 total + startMs 저장 (이벤트 발행 전에 기록)
        String totalKey = "jobai:scoring:" + pipelineRunId + ":total";
        String startKey = "jobai:scoring:" + pipelineRunId + ":startMs";
        stringRedisTemplate.opsForValue().set(totalKey, String.valueOf(entries.size()), Duration.ofHours(24));
        stringRedisTemplate.opsForValue().set(startKey, String.valueOf(System.currentTimeMillis()), Duration.ofHours(24));

        // 3단계: 이벤트 발행
        for (DispatchEntry entry : entries) {
            String jdText = (entry.posting().getTitle() != null ? entry.posting().getTitle() : "")
                    + "\n"
                    + (entry.posting().getDescription() != null ? entry.posting().getDescription() : "");

            kafkaScoringProducer.send(new ScoringRequestEvent(
                    pipelineRunId,
                    entry.resume().getId(),
                    entry.resume().getMember().getId(),
                    entry.email(),
                    entry.posting().getId(),
                    "PRIVATE",
                    jdText,
                    toDoubleList(entry.embedding().getEmbedding()),
                    entry.resumeVec(),
                    entry.resumeSkills(),
                    entry.experienceYears(),
                    entry.threshold(),
                    entry.posting().getTitle(),
                    entry.posting().getCompany(),
                    entry.posting().getLocation(),
                    entry.posting().getEmploymentType(),
                    entry.posting().getJobCategory(),
                    entry.posting().getDeadline() != null ? entry.posting().getDeadline().toString() : null
            ));
        }

        log.info("[ScoringDispatcher] 사기업 스코어링 이벤트 발행 완료: pipelineRunId={}, 건수={}",
                pipelineRunId, entries.size());
        return new DispatchResult(pipelineRunId, entries.size());
    }

    private List<String> parseSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isBlank()) return List.of();
        try {
            return objectMapper.readValue(skillsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Double> toDoubleList(float[] floats) {
        List<Double> list = new ArrayList<>(floats.length);
        for (float f : floats) {
            list.add((double) f);
        }
        return list;
    }
}
