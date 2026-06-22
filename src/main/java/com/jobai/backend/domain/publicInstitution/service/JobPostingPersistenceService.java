package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.PublicJobPosting;
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

        jobPostingRepository.findByPblntfNo(pblntfNoStr)
                .ifPresentOrElse(
                        existingPost -> {
                            if (existingPost instanceof PublicJobPosting publicPost) {
                                publicPost.updatePublicInfo(
                                        pblntfNoStr,
                                        detail.srcUrl(),
                                        detail.scrnprcdrMthdExpln(),
                                        detail.ncsCdNmLst(),
                                        detail.aplyQlfcCn(),
                                        detail.disqlfcRsn(),
                                        extractedText,
                                        false,
                                        LocalDate.now(),
                                        LocalDate.now(),
                                        detail.recrutPbancTtl(),
                                        detail.instNm(),
                                        startDate,
                                        endDate,
                                        detail.recrutSeNm(),
                                        detail.workRgnNmLst(),
                                        detail.hireTypeNmLst()
                                );
                                log.info("기존 공공기관 공고 업데이트 완료: {}", detail.recrutPbancTtl());
                            }
                        },
                        () -> {
                            PublicJobPosting newPost = PublicJobPosting.builder()
                                    .pblntfNo(pblntfNoStr)
                                    .title(detail.recrutPbancTtl())
                                    .recrutType(detail.hireTypeNmLst())
                                    .companyName(detail.instNm())
                                    .workExperience(detail.recrutSeNm())
                                    .workRegion(detail.workRgnNmLst())
                                    .beginDate(startDate)
                                    .endDate(endDate)
                                    .applyLink(detail.srcUrl())
                                    .applicationMethod(detail.scrnprcdrMthdExpln())
                                    .jobRole(detail.ncsCdNmLst())
                                    .applyQualification(detail.aplyQlfcCn())
                                    .disqualificationReason(detail.disqlfcRsn())
                                    .htmlContent(extractedText)
                                    .isClosed(false)
                                    .lastCollectedAt(LocalDate.now())
                                    .lastSyncedDate(LocalDate.now())
                                    .build();
                            jobPostingRepository.save(newPost);
                            log.info("신규 공공기관 공고 저장 완료: {}", detail.recrutPbancTtl());
                        }
                );
    }
}
