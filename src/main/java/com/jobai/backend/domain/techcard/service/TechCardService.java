package com.jobai.backend.domain.techcard.service;

import com.jobai.backend.domain.matching.entity.PrivateMatchScore;
import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import com.jobai.backend.domain.matching.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.techcard.dto.TechCardResponse;
import com.jobai.backend.domain.techcard.dto.TechCardResponse.CardItem;
import com.jobai.backend.domain.techcard.dto.TechCardResponse.RelatedJob;
import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import com.jobai.backend.domain.techcard.repository.TechCardRepository;
import com.jobai.backend.global.cache.CacheNames;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 홈 화면 테크 카드 조회 서비스.
 * <p>신규 공고 1장 + 오늘 수집된 테크 뉴스 중 랜덤 2장을 조합하여 반환한다.
 * 데이터가 없는 카드는 생략되므로 최대 3장, 최소 0장이 반환될 수 있다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TechCardService {

    private final TechCardRepository techCardRepository;
    private final EntityManager entityManager;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;

    @Lazy
    @Autowired
    private TechCardService self;

    /** 홈 화면에 표시할 테크 카드 목록을 조회한다. */
    public TechCardResponse getTechCards(String email) {
        List<CardItem> cards = new ArrayList<>();

        // 1. 신규 공고 카드 (0건이어도 항상 포함)
        cards.add(generateNewJobsCard(email));

        // 2, 3. 테크 뉴스 카드 (오늘 수집분에서 랜덤 2건)
        List<TechCard> newsCards = techCardRepository.findTodayNewsCardsRandom();
        for (TechCard card : newsCards) {
            cards.add(new CardItem(
                    card.getId(),
                    card.getSource().name(),
                    "테크 뉴스",
                    card.getHeadline(),
                    card.getSubtext(),
                    card.getOriginalUrl(),
                    card.getPublishedAt(),
                    card.getCreatedAt(),
                    null
            ));
        }

        return new TechCardResponse(cards);
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj == null) return null;
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof java.sql.Date d) return d.toLocalDate();
        return null;
    }

    /**
     * 오늘 수집된 신규 공고를 집계하여 카드를 생성한다 (매칭 점수 제외, 캐시 대상).
     * <p>민간 공고와 공공 공고를 모두 포함하며, 비대상/미분류 카테고리를 제외한다.</p>
     */
    @Cacheable(cacheNames = CacheNames.NEW_JOBS_CARD, key = "'base'")
    public CardItem generateNewJobsCardBase() {
        @SuppressWarnings("unchecked")
        List<Tuple> jobResults = entityManager.createNativeQuery("""
                SELECT id, source, company_name, title, deadline, employment_type FROM (
                    SELECT id, 'PRIVATE' AS source, company AS company_name, title, created_at,
                           deadline, employment_type
                    FROM private_job_postings
                    WHERE created_at >= (NOW() AT TIME ZONE 'Asia/Seoul')::date
                      AND created_at < (NOW() AT TIME ZONE 'Asia/Seoul')::date + INTERVAL '1 day'
                      AND (job_category IS NULL OR job_category NOT IN ('비대상', '미분류'))
                    UNION ALL
                    SELECT jp.id, 'PUBLIC' AS source, jp.company_name, jp.title, jp.created_at,
                           jp.end_date AS deadline, jp.recrut_type AS employment_type
                    FROM public_job_postings pjp
                    JOIN job_postings jp ON pjp.id = jp.id
                    WHERE jp.created_at >= (NOW() AT TIME ZONE 'Asia/Seoul')::date
                      AND jp.created_at < (NOW() AT TIME ZONE 'Asia/Seoul')::date + INTERVAL '1 day'
                ) AS all_jobs
                ORDER BY created_at DESC
                """, Tuple.class)
                .getResultList();

        if (jobResults.isEmpty()) {
            return new CardItem(
                    null,
                    ContentSource.INTERNAL.name(),
                    "신규 공고",
                    "오늘 새로 올라온 공고가 0건 있어요",
                    "새로운 채용 기회를 확인해보세요",
                    null,
                    null,
                    LocalDateTime.now(),
                    null
            );
        }

        List<RelatedJob> relatedJobs = jobResults.stream()
                .map(row -> new RelatedJob(
                        ((Number) row.get("id")).longValue(),
                        (String) row.get("source"),
                        (String) row.get("company_name"),
                        (String) row.get("title"),
                        null,
                        toLocalDate(row.get("deadline")),
                        (String) row.get("employment_type")
                ))
                .toList();

        return new CardItem(
                null,
                ContentSource.INTERNAL.name(),
                "신규 공고",
                "오늘 새로 올라온 공고가 %d건 있어요".formatted(relatedJobs.size()),
                "새로운 채용 기회를 확인해보세요",
                null,
                null,
                LocalDateTime.now(),
                relatedJobs
        );
    }

    /** 캐시된 신규 공고 카드에 사용자별 매칭 점수를 오버레이한다. */
    private CardItem generateNewJobsCard(String email) {
        CardItem base = self.generateNewJobsCardBase();

        if (base.relatedJobs() == null || base.relatedJobs().isEmpty()
                || email == null || "anonymousUser".equals(email)) {
            return base;
        }

        Resumes activeResume = resumesRepository.findByMemberEmailAndIsActiveTrue(email).orElse(null);
        if (activeResume == null) {
            return base;
        }

        List<Long> privateIds = base.relatedJobs().stream()
                .filter(j -> "PRIVATE".equals(j.source())).map(RelatedJob::id).toList();
        List<Long> publicIds = base.relatedJobs().stream()
                .filter(j -> "PUBLIC".equals(j.source())).map(RelatedJob::id).toList();

        Map<Long, Integer> privateScores = Map.of();
        Map<Long, Integer> publicScores = Map.of();

        if (!privateIds.isEmpty()) {
            privateScores = privateMatchScoreRepository
                    .findByResumeIdAndPrivateJobPostingIdIn(activeResume.getId(), privateIds)
                    .stream()
                    .collect(Collectors.toMap(
                            s -> s.getPrivateJobPosting().getId(),
                            PrivateMatchScore::getScore,
                            (existing, replacement) -> existing));
        }
        if (!publicIds.isEmpty()) {
            publicScores = publicMatchScoreRepository
                    .findByResumeIdAndPublicJobPostingIdIn(activeResume.getId(), publicIds)
                    .stream()
                    .collect(Collectors.toMap(
                            s -> s.getPublicJobPosting().getId(),
                            PublicMatchScore::getScore,
                            (existing, replacement) -> existing));
        }

        Map<Long, Integer> finalPrivateScores = privateScores;
        Map<Long, Integer> finalPublicScores = publicScores;

        List<RelatedJob> withScores = base.relatedJobs().stream()
                .map(j -> {
                    Integer score = "PRIVATE".equals(j.source())
                            ? finalPrivateScores.get(j.id())
                            : finalPublicScores.get(j.id());
                    return new RelatedJob(
                            j.id(), j.source(), j.companyName(), j.title(),
                            score, j.deadline(), j.employmentType());
                })
                .toList();

        return new CardItem(
                base.id(), base.source(), base.badge(), base.headline(),
                base.subtext(), base.originalUrl(), base.publishedAt(),
                base.createdAt(), withScores
        );
    }
}
