package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.matching.entity.PublicMatchScore;
import com.jobai.backend.domain.matching.repository.PublicMatchScoreRepository;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.domain.member.repository.ResumesRepository;
import com.jobai.backend.domain.publicInstitution.dto.PublicJobPostingDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicJobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final ResumesRepository resumesRepository;
    private final PublicMatchScoreRepository publicMatchScoreRepository;

    public PublicJobPostingDetailResponse getDetail(Long id, String email) {
        PublicJobPosting posting = jobPostingRepository.findPublicJobPostingById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 채용공고를 찾을 수 없습니다."));

        ResolvedMatchScore matchScore = resolveMatchScore(id, email);

        return new PublicJobPostingDetailResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getCompanyName(),
                posting.getCompanyType(),
                posting.getRecrutType(),
                posting.getWorkExperience(),
                posting.getWorkRegion(),
                posting.getBeginDate(),
                posting.getEndDate(),
                posting.getJobRole(),
                posting.getApplyQualification(),
                posting.getDisqualificationReason(),
                posting.getApplicationMethod(),
                posting.getApplyLink(),
                Boolean.TRUE.equals(posting.getIsClosed()),
                matchScore.score(),
                matchScore.reason(),
                posting.getHtmlContent()
        );
    }

    private ResolvedMatchScore resolveMatchScore(Long jobPostingId, String email) {
        if (email == null || email.isBlank() || "anonymousUser".equals(email)) {
            return ResolvedMatchScore.empty();
        }

        Resumes activeResume = resumesRepository.findByMemberEmailAndIsActiveTrue(email).orElse(null);
        if (activeResume == null) {
            return ResolvedMatchScore.empty();
        }

        List<PublicMatchScore> scores = publicMatchScoreRepository
                .findByResumeIdAndPublicJobPostingIdIn(activeResume.getId(), List.of(jobPostingId));
        if (!scores.isEmpty()) {
            PublicMatchScore savedScore = scores.get(0);
            return new ResolvedMatchScore(savedScore.getScore(), savedScore.getScoreReason());
        }

        return ResolvedMatchScore.empty();
    }

    private record ResolvedMatchScore(Integer score, String reason) {
        private static ResolvedMatchScore empty() {
            return new ResolvedMatchScore(null, null);
        }
    }
}
