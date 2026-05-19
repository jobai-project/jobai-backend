package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDetailSyncService {

    private final JobPostingRepository jobPostingRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 고유 번호(sn)를 받아 개별 채용공고의 상세 정보를 조회하고 DB에 최종 저장합니다.
     */
    @Transactional
    public void fetchAndSaveJobDetail(WebClient webClient, Long sn, String serviceKey) {
        try {
            PublicJobDetailResponse detailResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/1051000/recruitment/detail")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("recrutPblntSn", sn)
                            .build())
                    .retrieve()
                    .bodyToMono(PublicJobDetailResponse.class)
                    .block();

            // TODO 공고 상세 데이터가 모두 빈 상테로 반환됨. 수정필요
            if (detailResponse == null || detailResponse.result() == null) {
                log.warn("공고 일련번호 [{}] 상세 데이터가 비어있어 스킵합니다.", sn);
                return;
            }

            PublicJobDetailResponse.DetailItem detail = detailResponse.result();

            // PDF 파일 필터링 추출
            String pdfUrl = "없음";
            if (detail.files() != null) {
                pdfUrl = detail.files().stream()
                        .filter(f -> f.atchFileNm().contains("직무기술서") || "C".equals(f.atchFileType()))
                        .map(PublicJobDetailResponse.FileItem::url)
                        .findFirst()
                        .orElse("없음");
            }

            LocalDate startDate = parseLocalDate(detail.pbancBgngYmd());
            LocalDate endDate = parseLocalDate(detail.pbancEndYmd());
            String pblntfNoStr = String.valueOf(detail.recrutPblntSn());

            // DB Upsert 처리
            String finalPdfUrl = pdfUrl;
            jobPostingRepository.findByPblntfNo(pblntfNoStr)
                    .ifPresentOrElse(
                            existingPost -> {
                                existingPost.updateDetailedInfo(
                                        detail.recrutPbancTtl(), detail.recrutSeNm(), detail.workRgnNmLst(), endDate,
                                        detail.scrnprcdrMthdExpln(), detail.ncsCdNmLst(), // 접수방법, 모집직무 매핑
                                        detail.aplyQlfcCn(), detail.disqlfcRsn(), finalPdfUrl
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
                                        .build();
                                jobPostingRepository.save(newPost);
                                log.info("신규 공고 상세 저장 완료: {}", detail.recrutPbancTtl());
                            }
                    );

        } catch (Exception e) {
            log.error("공고 일련번호 [{}] 처리 중 치명적 에러 발생 (해당 건 스킵): {}", sn, e.getMessage());
        }
    }

    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.replaceAll("-", ""), DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}