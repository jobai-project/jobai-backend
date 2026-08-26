package com.jobai.backend.global.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
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
import com.jobai.backend.global.kafka.event.ScoringResultEvent;
import com.jobai.backend.global.kafka.producer.KafkaNotificationProducer;
import com.jobai.backend.global.kafka.event.NotificationDispatchEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
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
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaNotificationProducer kafkaNotificationProducer;

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
            trackCompletion(event);
            return;
        }

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

        // 임계값 이상이면 알림 발행
        if (score >= event.scoreThreshold()) {
            String message = String.format("[%s] %s (매칭 %d점)",
                    event.postingCompany(), event.postingTitle(), score);
            kafkaNotificationProducer.send(new NotificationDispatchEvent(
                    event.userEmail(), "MATCH", "새 추천 공고", message,
                    "/jobs/private/" + event.postingId(), Instant.now()));
        }

        trackCompletion(event);
    }

    /**
     * Redis 카운터로 완료 건수 추적.
     * pipelineRunId별로 completed를 증가시키고, total과 같아지면 배치 완료.
     */
    private void trackCompletion(ScoringRequestEvent event) {
        if (event.pipelineRunId() == null) return;

        String completedKey = "jobai:scoring:" + event.pipelineRunId() + ":completed";
        Long completed = stringRedisTemplate.opsForValue().increment(completedKey);
        stringRedisTemplate.expire(completedKey, Duration.ofHours(24));

        String totalKey = "jobai:scoring:" + event.pipelineRunId() + ":total";
        String totalStr = stringRedisTemplate.opsForValue().get(totalKey);

        if (totalStr != null && completed != null && completed == Long.parseLong(totalStr)) {
            String startKey = "jobai:scoring:" + event.pipelineRunId() + ":startMs";
            String startMsStr = stringRedisTemplate.opsForValue().get(startKey);
            long elapsedMs = 0;
            if (startMsStr != null) {
                elapsedMs = System.currentTimeMillis() - Long.parseLong(startMsStr);
            }

            String resultKey = "jobai:scoring:" + event.pipelineRunId() + ":result";
            String result = String.format(
                    "Kafka 스코어링 전체 완료: %s건 처리 (총 소요: %dms)", totalStr, elapsedMs);
            stringRedisTemplate.opsForValue().set(resultKey, result, Duration.ofHours(24));

            log.info("[벤치마크] {}", result);
            log.info("[Kafka 스코어링] 파이프라인 완료: pipelineRunId={}, total={}",
                    event.pipelineRunId(), totalStr);
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
