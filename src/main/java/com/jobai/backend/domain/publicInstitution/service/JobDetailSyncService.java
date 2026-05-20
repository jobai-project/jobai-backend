package com.jobai.backend.domain.publicInstitution.service;


import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.global.util.PdfParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;


/**
    채용공고 상세정보를 가져오고, pdf 파일을 다운받은 후 파싱하는 로직을 총괄함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDetailSyncService {

    private final JobPostingRepository jobPostingRepository;
    private final PdfParserUtil pdfParserUtil;
    private final PublicJobDetailApiClient publicJobDetailApiClient;
    private final AlioPdfDownloader alioPdfDownloader;

    /**
     * 고유 번호(sn)를 받아 개별 채용공고의 상세 정보를 조회하고 DB에 최종 저장합니다.
     */
    @Transactional
    public void fetchAndSaveJobDetail(WebClient webClient, Long sn, String serviceKey) {
        try {
            PublicJobDetailResponse detailResponse = publicJobDetailApiClient.fetchJobDetail(webClient, sn, serviceKey);

            if (detailResponse == null || detailResponse.result() == null) {
                log.warn("공고 일련번호 [{}] 상세 데이터가 비어있어 스킵합니다.", sn);
                return;
            }

            PublicJobDetailResponse.DetailItem detail = detailResponse.result();

            // PDF 파일 필터링 추출
            String pdfUrl = extractPdfUrl(detail);

            /*
            PDF 텍스트 추출 작동 구역
             */
            String extractedText = "";
            String ncsSubCategory = "일반무구분";

            if (pdfUrl != null) {
                byte[] pdfBytes = alioPdfDownloader.downloadPdf(pdfUrl);
                if (pdfBytes != null) {
                    try {
                        // 유틸리티를 호출하여 복잡한 PDF 레이아웃 해제 후 텍스트 추출
                        extractedText = pdfParserUtil.extractText(pdfBytes);
                        log.info("▶️ [디버깅] PDF에서 추출된 총 텍스트 글자 수: {}자", extractedText.length());

                        ncsSubCategory = pdfParserUtil.parseNcsSubCategory(extractedText);

                        log.info("직무기술서 파싱 완료! 매칭된 소분류: [{}]", ncsSubCategory);
                    } catch (Exception pdfEx) {
                        log.error("공고 [{}]의 PDF 파싱 중 예외 발생 (기본 텍스트 데이터 보존): {}", sn, pdfEx.getMessage());
                    }
                }
            }

            saveOrUpdateJobPosting(detail, pdfUrl, extractedText, ncsSubCategory);

        } catch (Exception e) {
            log.error("공고 일련번호 [{}] 처리 중 치명적 에러 발생 (해당 건 스킵): {}", sn, e.getMessage());
        }
    }

    // PDF URL 추출 로직
    private String extractPdfUrl(PublicJobDetailResponse.DetailItem detail) {
        if (detail.files() == null) return null;
        return detail.files().stream()
                .filter(f -> f.atchFileNm().contains("직무기술서") || "C".equals(f.atchFileType()))
                .map(PublicJobDetailResponse.FileItem::url)
                .findFirst()
                .orElse(null);
    }

    // DB 저장 및 업데이트 로직
    private void saveOrUpdateJobPosting(PublicJobDetailResponse.DetailItem detail, String pdfUrl, String extractedText, String ncsSubCategory) {
        LocalDate startDate = PublicJobDateParser.parseLocalDate(detail.pbancBgngYmd());
        LocalDate endDate = PublicJobDateParser.parseLocalDate(detail.pbancEndYmd());
        String pblntfNoStr = String.valueOf(detail.recrutPblntSn());

        String finalPdfUrl = (pdfUrl != null) ? pdfUrl : "없음";

        jobPostingRepository.findByPblntfNo(pblntfNoStr)
                .ifPresentOrElse(
                        existingPost -> {
                            existingPost.updateDetailedInfo(
                                    detail.recrutPbancTtl(), detail.recrutSeNm(), detail.workRgnNmLst(), endDate,
                                    detail.scrnprcdrMthdExpln(), detail.ncsCdNmLst(), // 접수방법, 모집직무 매핑
                                    detail.aplyQlfcCn(), detail.disqlfcRsn(), finalPdfUrl,
                                    extractedText, ncsSubCategory // PDF 텍스트추출 구역
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
                                    .applicationMethod(detail.scrnprcdrMthdExpln()) // 접수방법
                                    .jobRole(detail.ncsCdNmLst())                   // 모집직무
                                    .applyQualification(detail.aplyQlfcCn())
                                    .disqualificationReason(detail.disqlfcRsn())
                                    .jobDescriptionPdfUrl(finalPdfUrl)
                                    .extractedHtmlText(extractedText) // PDF
                                    .ncsSubCategory(ncsSubCategory)    // PDF
                                    .build();
                            jobPostingRepository.save(newPost);
                            log.info("신규 공고 상세 저장 완료: {}", detail.recrutPbancTtl());
                        }
                );
    }
}