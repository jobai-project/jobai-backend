package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobPostingDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicJobPostingService {

    private final JobPostingRepository jobPostingRepository;

    public PublicJobPostingDetailResponse getDetail(Long id) {
        PublicJobPosting posting = jobPostingRepository.findPublicJobPostingById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 채용공고를 찾을 수 없습니다."));

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
                posting.getHtmlContent()
        );
    }
}
