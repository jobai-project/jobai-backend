package com.jobai.backend.domain.techcard.service;

import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.entity.PublicMatchScore;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.techcard.dto.TechCardResponse;
import com.jobai.backend.domain.techcard.dto.TechCardResponse.CardItem;
import com.jobai.backend.domain.techcard.dto.TechCardResponse.RelatedJob;
import com.jobai.backend.domain.techcard.entity.ContentSource;
import com.jobai.backend.domain.techcard.entity.TechCard;
import com.jobai.backend.domain.techcard.repository.TechCardRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
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

    /** 홈 화면에 표시할 테크 카드 목록을 조회한다. */
    public TechCardResponse getTechCards(String email) {
        List<CardItem> cards = new ArrayList<>();

        // 1. 신규 공고 카드
        CardItem newJobsCard = generateNewJobsCard(email);
        if (newJobsCard != null) {
            cards.add(newJobsCard);
        }

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

    /**
     * 오늘 수집된 신규 공고를 집계하여 카드를 생성한다.
     * <p>민간 공고와 공공 공고를 모두 포함하며, 비대상/미분류 카테고리를 제외한다.</p>
     *
     * @return 신규 공고 카드 (오늘 공고가 없으면 {@code null})
     */
    private CardItem generateNewJobsCard(String email) {
        @SuppressWarnings("unchecked")
        List<Tuple> jobResults = entityManager.createNativeQuery("""
                SELECT id, source, company_name, title, deadline, employment_type FROM (
                    SELECT id, 'PRIVATE' AS source, company AS company_name, title, created_at,
                           deadline, employment_type
                    FROM private_job_postings
                    WHERE created_at >= CURRENT_DATE
                      AND created_at < CURRENT_DATE + 1
                      AND (job_category IS NULL OR job_category NOT IN ('비대상', '미분류'))
                    UNION ALL
                    SELECT jp.id, 'PUBLIC' AS source, jp.company_name, jp.title, jp.created_at,
                           jp.end_date AS deadline, jp.recrut_type AS employment_type
                    FROM public_job_postings pjp
                    JOIN job_postings jp ON pjp.id = jp.id
                    WHERE jp.created_at >= CURRENT_DATE
                      AND jp.created_at < CURRENT_DATE + 1
                ) AS all_jobs
                ORDER BY created_at DESC
                """, Tuple.class)
                .getResultList();

        if (jobResults.isEmpty()) {
            return null;
        }

        // 매칭 점수 로딩
        Map<Long, Integer> privateScores = Map.of();
        Map<Long, Integer> publicScores = Map.of();

        if (email != null && !"anonymousUser".equals(email)) {
            Resumes activeResume = resumesRepository.findByMemberEmailAndIsActiveTrue(email).orElse(null);
            if (activeResume != null) {
                List<Long> privateIds = jobResults.stream()
                        .filter(row -> "PRIVATE".equals(row.get("source")))
                        .map(row -> ((Number) row.get("id")).longValue())
                        .toList();
                List<Long> publicIds = jobResults.stream()
                        .filter(row -> "PUBLIC".equals(row.get("source")))
                        .map(row -> ((Number) row.get("id")).longValue())
                        .toList();

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
            }
        }

        Map<Long, Integer> finalPrivateScores = privateScores;
        Map<Long, Integer> finalPublicScores = publicScores;

        List<RelatedJob> relatedJobs = jobResults.stream()
                .map(row -> {
                    Long id = ((Number) row.get("id")).longValue();
                    String source = (String) row.get("source");
                    Integer score = "PRIVATE".equals(source)
                            ? finalPrivateScores.get(id)
                            : finalPublicScores.get(id);
                    LocalDate deadline = row.get("deadline", LocalDate.class);
                    return new RelatedJob(
                            id,
                            source,
                            (String) row.get("company_name"),
                            (String) row.get("title"),
                            score,
                            deadline,
                            (String) row.get("employment_type")
                    );
                })
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
}
