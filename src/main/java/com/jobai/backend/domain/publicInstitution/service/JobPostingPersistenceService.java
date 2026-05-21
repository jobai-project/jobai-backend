package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingPersistenceService {

    private final JobPostingRepository jobPostingRepository;

    @Transactional
    public void saveOrUpdateJobPosting(PublicJobDetailResponse.DetailItem detail, String pdfUrl, String extractedText, String ncsSubCategory) {
        LocalDate startDate = PublicJobDateParser.parseLocalDate(detail.pbancBgngYmd());
        LocalDate endDate = PublicJobDateParser.parseLocalDate(detail.pbancEndYmd());
        String pblntfNoStr = String.valueOf(detail.recrutPblntSn());

        String finalPdfUrl = (pdfUrl != null) ? pdfUrl : "없음";

        jobPostingRepository.findByPblntfNo(pblntfNoStr)
                .ifPresentOrElse(
                        existingPost -> {
                            existingPost.updateDetailedInfo(
                                    detail.recrutPbancTtl(), detail.recrutSeNm(), detail.workRgnNmLst(), endDate,
                                    detail.scrnprcdrMthdExpln(), detail.ncsCdNmLst(),
                                    detail.aplyQlfcCn(), detail.disqlfcRsn(), finalPdfUrl,
                                    extractedText, ncsSubCategory
                            );
                            log.info("기존 공고 상세 업데이트 완료: {}", detail.recrutPbancTtl());
                        },
                        () -> {
                            JobPosting newPost = JobPosting.builder()
                                    .pblntfNo(pblntfNoStr)
                                    .pbancNm(detail.recrutPbancTtl())
                                    .instNm(detail.instNm())
                                    .recrutSeNm(detail.recrutSeNm())
                                    .workRgnNm(detail.workRgnNmLst())
                                    .pbancBgngDt(startDate)
                                    .pbancEndDt(endDate)
                                    .applicationMethod(detail.scrnprcdrMthdExpln())
                                    .jobRole(detail.ncsCdNmLst())
                                    .applyQualification(detail.aplyQlfcCn())
                                    .disqualificationReason(detail.disqlfcRsn())
                                    .jobDescriptionPdfUrl(finalPdfUrl)
                                    .extractedHtmlText(extractedText)
                                    .ncsSubCategory(ncsSubCategory)
                                    .build();
                            jobPostingRepository.save(newPost);
                            log.info("신규 공고 상세 저장 완료: {}", detail.recrutPbancTtl());
                        }
                );
    }
}
