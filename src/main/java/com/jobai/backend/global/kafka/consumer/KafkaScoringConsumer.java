package com.jobai.backend.global.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.service.BatchNotificationHelper;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.privatejobposting.entity.PrivateJobPosting;
import com.jobai.backend.domain.privatejobposting.repository.PrivateJobPostingRepository;
import com.jobai.backend.global.ai.client.AiScoringClient;
import com.jobai.backend.global.ai.dto.ScorePrivateRequest;
import com.jobai.backend.global.ai.dto.ScorePrivateResponse;
import com.jobai.backend.global.kafka.event.ScoringRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaScoringConsumer {

    private final AiScoringClient aiScoringClient;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PrivateJobPostingRepository privateJobPostingRepository;
    private final ResumesRepository resumesRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final BatchNotificationHelper batchNotificationHelper;

    /**
     * 스코어링 요청 이벤트를 수신하여 AI 호출 → 점수 저장을 처리한다.
     * 멱등성 보장: 이미 점수가 존재하면 스킵한다.
     * 성공/실패 관계없이 finally에서 trackCompletion을 호출하여 완료 카운터를 증가시킨다.
     * 알림은 전체 완료 시 BatchNotificationHelper로 배치 발송한다 (동기 경로와 동일한 계약).
     */
    @KafkaListener(
            topics = "jobai.scoring.request",
            groupId = "jobai-scoring-group",
            concurrency = "6",
            properties = {
                    "spring.json.value.default.type=com.jobai.backend.global.kafka.event.ScoringRequestEvent"
            }
    )
    public void consume(ScoringRequestEvent event) {
        log.info("[Kafka 스코어링] 수신: resumeId={}, postingId={}, source={}",
                event.resumeId(), event.postingId(), event.postingSource());

        // 멱등성: 이미 점수가 존재하면 스킵
        if ("PRIVATE".equals(event.postingSource())
                && privateMatchScoreRepository.existsByResumeIdAndPrivateJobPostingId(
                        event.resumeId(), event.postingId())) {
            log.info("[Kafka 스코어링] 이미 점수 존재, 스킵: resumeId={}, postingId={}",
                    event.resumeId(), event.postingId());
            trackCompletion(event, true);
            return;
        }

        boolean success = false;
        try {
            // AI 스코어링 호출
            ScorePrivateRequest request = new ScorePrivateRequest(
                    event.jdText(),
                    event.jdVector(),
                    event.resumeVector(),
                    event.resumeSkills(),
                    event.experienceYears()
            );

            ScorePrivateResponse response = aiScoringClient.scorePrivate(request).block();
            if (response == null) {
                throw new IllegalStateException(
                        "AI 점수 응답 null: resumeId=" + event.resumeId() + ", postingId=" + event.postingId());
            }

            int score = (int) Math.round(response.score());

            // DB 저장
            PrivateJobPosting posting = privateJobPostingRepository.getReferenceById(event.postingId());
            Resumes resume = resumesRepository.getReferenceById(event.resumeId());
            Member member = memberRepository.getReferenceById(event.memberId());

            privateMatchScoreRepository.save(PrivateMatchScore.builder()
                    .member(member)
                    .resume(resume)
                    .privateJobPosting(posting)
                    .score(score)
                    .scoreReason(response.scoreReason())
                    .matchedSkills(toJson(response.matchedSkills()))
                    .missingSkills(toJson(response.missingSkills()))
                    .careerMet(response.careerMet())
                    .modelVersion(response.modelVersion())
                    .build());

            log.info("[Kafka 스코어링] 저장 완료: resumeId={}, postingId={}, score={}",
                    event.resumeId(), event.postingId(), score);

            success = true;
        } finally {
            trackCompletion(event, success);
        }
    }

    /**
     * Redis 카운터로 완료 건수 추적.
     * 성공/실패 모두 completed를 증가시키고, 실패 시 failed도 별도 증가.
     * completed >= total이면 배치 완료로 판정한다. result는 setIfAbsent로 1회만 기록.
     * 최초 완료 판정 스레드가 배치 알림을 발송한다 (동기 경로와 동일한 알림 계약).
     */
    private void trackCompletion(ScoringRequestEvent event, boolean success) {
        if (event.pipelineRunId() == null) return;

        String prefix = "jobai:scoring:" + event.pipelineRunId();
        String completedKey = prefix + ":completed";
        Long completed = stringRedisTemplate.opsForValue().increment(completedKey);
        stringRedisTemplate.expire(completedKey, Duration.ofHours(24));

        if (!success) {
            String failedKey = prefix + ":failed";
            stringRedisTemplate.opsForValue().increment(failedKey);
            stringRedisTemplate.expire(failedKey, Duration.ofHours(24));
        }

        String totalKey = prefix + ":total";
        String totalStr = stringRedisTemplate.opsForValue().get(totalKey);

        if (totalStr != null && completed != null && completed >= Long.parseLong(totalStr)) {
            String startKey = prefix + ":startMs";
            String startMsStr = stringRedisTemplate.opsForValue().get(startKey);
            long elapsedMs = 0;
            if (startMsStr != null) {
                elapsedMs = System.currentTimeMillis() - Long.parseLong(startMsStr);
            }

            String failedKey = prefix + ":failed";
            String failedStr = stringRedisTemplate.opsForValue().get(failedKey);
            long failedCount = failedStr != null ? Long.parseLong(failedStr) : 0;

            String resultKey = prefix + ":result";
            String result = String.format(
                    "Kafka 스코어링 전체 완료: %s건 처리, 실패 %d건 (총 소요: %dms)",
                    totalStr, failedCount, elapsedMs);
            // setIfAbsent: 여러 스레드가 동시에 도달해도 1회만 기록 + 알림 발송
            Boolean isFirst = stringRedisTemplate.opsForValue().setIfAbsent(resultKey, result, Duration.ofHours(24));

            log.info("[벤치마크] {}", result);
            log.info("[Kafka 스코어링] 파이프라인 완료: pipelineRunId={}, total={}",
                    event.pipelineRunId(), totalStr);

            // 최초 완료 판정 스레드만 배치 알림 발송 (동기 경로의 sendIfNeeded와 동일한 계약)
            if (Boolean.TRUE.equals(isFirst)) {
                try {
                    batchNotificationHelper.sendNotificationsForExistingScores();
                    log.info("[Kafka 스코어링] 배치 알림 발송 완료");
                } catch (Exception e) {
                    log.warn("[Kafka 스코어링] 배치 알림 발송 실패: {}", e.getMessage(), e);
                }
            }
        }
    }

    private String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
