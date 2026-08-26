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
     * 활성 이력서 × 활성 공고 중 신규/변경분을 필터링하여
     * Kafka 스코어링 요청 이벤트로 발행한다.
     *
     * @return 발행 결과 (pipelineRunId + 발행 건수)
     */
    public DispatchResult dispatchPrivateScoring() {
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

        String pipelineRunId = UUID.randomUUID().toString();
        int dispatched = 0;

        for (Resumes resume : activeResumes) {
            String email = resume.getMember().getEmail();
            int threshold = notificationRepository.findByMemberEmail(email)
                    .map(Notification::getMatchScoreThreshold)
                    .orElse(70);

            // 기존 점수 조회 → 신규만 필터
            Set<Long> existingPostingIds = privateMatchScoreRepository.findByResumeId(resume.getId())
                    .stream()
                    .map(s -> s.getPrivateJobPosting().getId())
                    .collect(Collectors.toSet());

            List<String> resumeSkills = parseSkills(resume.getResumeSkills());
            List<Double> resumeVec = toDoubleList(resume.getEmbedding());
            int experienceYears = resume.getExperienceYears() != null ? resume.getExperienceYears() : 0;

            for (PrivateJobPosting posting : activePostings) {
                // 이미 점수 존재 → 스킵
                if (existingPostingIds.contains(posting.getId())) continue;

                // 임베딩 없는 공고 → 스킵
                JobEmbedding embedding = embeddingMap.get(posting.getId());
                if (embedding == null) continue;

                String jdText = (posting.getTitle() != null ? posting.getTitle() : "")
                        + "\n"
                        + (posting.getDescription() != null ? posting.getDescription() : "");

                kafkaScoringProducer.send(new ScoringRequestEvent(
                        pipelineRunId,
                        resume.getId(),
                        resume.getMember().getId(),
                        email,
                        posting.getId(),
                        "PRIVATE",
                        jdText,
                        toDoubleList(embedding.getEmbedding()),
                        resumeVec,
                        resumeSkills,
                        experienceYears,
                        threshold,
                        posting.getTitle(),
                        posting.getCompany(),
                        posting.getLocation(),
                        posting.getEmploymentType(),
                        posting.getJobCategory(),
                        posting.getDeadline() != null ? posting.getDeadline().toString() : null
                ));
                dispatched++;
            }
        }

        // Redis에 총 건수 + 시작 시간 저장 (완료 추적 + 벤치마크용)
        if (dispatched > 0) {
            String totalKey = "jobai:scoring:" + pipelineRunId + ":total";
            String startKey = "jobai:scoring:" + pipelineRunId + ":startMs";
            stringRedisTemplate.opsForValue().set(totalKey, String.valueOf(dispatched), Duration.ofHours(24));
            stringRedisTemplate.opsForValue().set(startKey, String.valueOf(System.currentTimeMillis()), Duration.ofHours(24));
        }

        log.info("[ScoringDispatcher] 사기업 스코어링 이벤트 발행 완료: pipelineRunId={}, 건수={}",
                pipelineRunId, dispatched);
        return new DispatchResult(pipelineRunId, dispatched);
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
