package com.jobai.backend.domain.scrap.service;

import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.home.dto.JobCandidate;
import com.jobai.backend.domain.home.entity.PrivateMatchScore;
import com.jobai.backend.domain.home.entity.PublicMatchScore;
import com.jobai.backend.domain.home.repository.HomeJobCandidateRepository;
import com.jobai.backend.domain.home.repository.PrivateMatchScoreRepository;
import com.jobai.backend.domain.home.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Member;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.MemberRepository;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.domain.scrap.dto.ScrapResponseDTO;
import com.jobai.backend.domain.scrap.dto.ScrapResponseDTO.AddResultDTO;
import com.jobai.backend.domain.scrap.dto.ScrapResponseDTO.ScrapItemDTO;
import com.jobai.backend.domain.scrap.entity.MemberScrapHistory;
import com.jobai.backend.domain.scrap.repository.MemberScrapHistoryRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScrapService {

    private static final String SOURCE_PUBLIC = "PUBLIC";
    private static final String SOURCE_PRIVATE = "PRIVATE";

    private final MemberRepository memberRepository;
    private final MemberScrapHistoryRepository scrapHistoryRepository;
    private final JobPostingRepository jobPostingRepository;
    private final PrivateJobPostingRepository privateJobPostingRepository;
    private final HomeJobCandidateRepository candidateRepository;
    private final ResumesRepository resumesRepository;
    private final PrivateMatchScoreRepository privateMatchScoreRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;

    @Transactional
    public AddResultDTO addScrap(String email, String source, Long sourceId) {
        Member member = getMember(email);
        String normalizedSource = validateSource(source);
        validateTargetExists(normalizedSource, sourceId);

        MemberScrapHistory existing = scrapHistoryRepository
                .findByMemberEmailAndSourceAndSourceId(email, normalizedSource, sourceId)
                .orElse(null);
        if (existing != null) {
            return AddResultDTO.builder().scrapId(existing.getId()).build();
        }

        MemberScrapHistory saved = scrapHistoryRepository.save(
                MemberScrapHistory.builder()
                        .member(member)
                        .source(normalizedSource)
                        .sourceId(sourceId)
                        .build()
        );
        return AddResultDTO.builder().scrapId(saved.getId()).build();
    }

    @Transactional
    public void removeScrap(String email, String source, Long sourceId) {
        getMember(email);
        String normalizedSource = validateSource(source);
        scrapHistoryRepository.deleteByMemberEmailAndSourceAndSourceId(email, normalizedSource, sourceId);
    }

    public ScrapResponseDTO.ScrapListDTO getMyScraps(String email) {
        getMember(email);

        List<MemberScrapHistory> histories = scrapHistoryRepository.findByMemberEmailOrderByScrappedAtDesc(email);
        if (histories.isEmpty()) {
            return ScrapResponseDTO.ScrapListDTO.builder().scraps(List.of()).build();
        }

        List<Long> publicIds = histories.stream()
                .filter(h -> SOURCE_PUBLIC.equals(h.getSource()))
                .map(MemberScrapHistory::getSourceId)
                .toList();
        List<Long> privateIds = histories.stream()
                .filter(h -> SOURCE_PRIVATE.equals(h.getSource()))
                .map(MemberScrapHistory::getSourceId)
                .toList();

        Map<Long, JobCandidate> publicById = candidateRepository.findPublicCandidatesByIds(publicIds).stream()
                .collect(Collectors.toMap(JobCandidate::id, c -> c));
        Map<Long, JobCandidate> privateById = candidateRepository.findPrivateCandidatesByIds(privateIds).stream()
                .collect(Collectors.toMap(JobCandidate::id, c -> c));
        MatchScores matchScores = loadMatchScores(email, publicIds, privateIds);

        List<ScrapItemDTO> items = histories.stream()
                .map(h -> toScrapItem(
                        h,
                        SOURCE_PUBLIC.equals(h.getSource())
                                ? publicById.get(h.getSourceId())
                                : privateById.get(h.getSourceId()),
                        matchScores.get(h.getSource(), h.getSourceId())
                ))
                .filter(java.util.Objects::nonNull) // 원본 공고가 삭제된 경우 등은 제외
                .sorted(Comparator.comparing(ScrapItemDTO::getScrappedAt).reversed())
                .toList();

        return ScrapResponseDTO.ScrapListDTO.builder().scraps(items).build();
    }

    public ScrapResponseDTO.ScrapListDTO getUpcomingDeadlineScraps(String email) {
        getMember(email);

        LocalDate today = LocalDate.now();
        PageRequest limit = PageRequest.of(0, 3);
        List<ScrapItemDTO> items = java.util.stream.Stream.concat(
                        scrapHistoryRepository.findUpcomingPublicDeadlineScraps(email, today, limit).stream(),
                        scrapHistoryRepository.findUpcomingPrivateDeadlineScraps(email, today, limit).stream()
                )
                .map(this::toUpcomingScrapItem)
                .sorted(Comparator
                        .comparing(ScrapItemDTO::getDeadline)
                        .thenComparing(ScrapItemDTO::getScrappedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ScrapItemDTO::getSourceId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .toList();

        return ScrapResponseDTO.ScrapListDTO.builder().scraps(items).build();
    }

    private ScrapItemDTO toUpcomingScrapItem(Object[] row) {
        LocalDate deadline = (LocalDate) row[7];
        Integer dDay = (int) ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        return ScrapItemDTO.builder()
                .scrapId((Long) row[0])
                .source((String) row[1])
                .sourceId((Long) row[2])
                .companyName((String) row[3])
                .title((String) row[4])
                .location((String) row[5])
                .employmentType((String) row[6])
                .deadline(deadline)
                .dDay(dDay)
                .scrappedAt((LocalDateTime) row[8])
                .build();
    }

    private ScrapItemDTO toScrapItem(MemberScrapHistory history, JobCandidate candidate, Integer matchScore) {
        if (candidate == null) {
            return null;
        }
        Integer dDay = candidate.deadline() == null
                ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(), candidate.deadline());
        return ScrapItemDTO.builder()
                .scrapId(history.getId())
                .source(history.getSource())
                .sourceId(history.getSourceId())
                .companyName(candidate.companyName())
                .title(candidate.title())
                .location(candidate.location())
                .employmentType(candidate.employmentType())
                .matchScore(matchScore)
                .deadline(candidate.deadline())
                .dDay(dDay)
                .scrappedAt(history.getScrappedAt())
                .build();
    }

    private MatchScores loadMatchScores(String email, List<Long> publicIds, List<Long> privateIds) {
        Resumes activeResume = resumesRepository.findByMemberEmailAndIsActiveTrue(email).orElse(null);
        if (activeResume == null) {
            return MatchScores.empty();
        }

        Map<Long, Integer> publicScores = publicIds.isEmpty()
                ? Map.of()
                : publicMatchScoreRepository
                        .findByResumeIdAndPublicJobPostingIdIn(activeResume.getId(), publicIds)
                        .stream()
                        .collect(Collectors.toMap(
                                score -> score.getPublicJobPosting().getId(),
                                PublicMatchScore::getScore
                        ));
        Map<Long, Integer> privateScores = privateIds.isEmpty()
                ? Map.of()
                : privateMatchScoreRepository
                        .findByResumeIdAndPrivateJobPostingIdIn(activeResume.getId(), privateIds)
                        .stream()
                        .collect(Collectors.toMap(
                                score -> score.getPrivateJobPosting().getId(),
                                PrivateMatchScore::getScore
                        ));
        return new MatchScores(publicScores, privateScores);
    }

    private record MatchScores(Map<Long, Integer> publicScores, Map<Long, Integer> privateScores) {
        private static MatchScores empty() {
            return new MatchScores(Map.of(), Map.of());
        }

        private Integer get(String source, Long sourceId) {
            return SOURCE_PUBLIC.equals(source) ? publicScores.get(sourceId) : privateScores.get(sourceId);
        }
    }

    private Member getMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND, "해당 이메일은 존재하지 않는 회원입니다."));
    }

    private String validateSource(String source) {
        String normalized = source == null ? "" : source.trim().toUpperCase();
        if (!SOURCE_PUBLIC.equals(normalized) && !SOURCE_PRIVATE.equals(normalized)) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "source는 PUBLIC 또는 PRIVATE만 가능합니다.");
        }
        return normalized;
    }

    private void validateTargetExists(String source, Long sourceId) {
        boolean exists = SOURCE_PUBLIC.equals(source)
                ? jobPostingRepository.existsById(sourceId)
                : privateJobPostingRepository.existsById(sourceId);
        if (!exists) {
            throw new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 채용공고를 찾을 수 없습니다.");
        }
    }
}
