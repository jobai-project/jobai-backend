package com.jobai.backend.domain.publicInstitution.service;


import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import com.jobai.backend.global.util.PdfParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


/**
    채용공고 상세정보를 가져오고, pdf 파일을 다운받은 후 파싱하는 로직을 총괄함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDetailSyncService {

    private final JobPostingPersistenceService jobPostingPersistenceService;
    private final PdfParserUtil pdfParserUtil;
    private final PublicJobDetailApiClient publicJobDetailApiClient;
    private final AlioPdfDownloader alioPdfDownloader;

    /**
     * 비동기 방식으로 개별 채용공고를 처리합니다.
     */
    public Mono<Void> fetchAndSaveJobDetailAsync(WebClient webClient, Long sn, String serviceKey) {
        return publicJobDetailApiClient.fetchJobDetail(webClient, sn, serviceKey)
                .flatMap(detailResponse -> {
                    if (detailResponse == null || detailResponse.result() == null) {
                        log.warn("공고 일련번호 [{}] 상세 데이터가 비어있어 스킵합니다.", sn);
                        return Mono.empty();
                    }

                    PublicJobDetailResponse.DetailItem detail = detailResponse.result();
                    String pdfUrl = extractPdfUrl(detail);

                    if (pdfUrl == null) {
                        jobPostingPersistenceService.saveOrUpdateJobPosting(detail, null, "", "일반무구분");
                        return Mono.empty();
                    }

                    // PDF 다운로드 및 파싱 (비동기 처리)
                    return alioPdfDownloader.downloadPdf(pdfUrl)
                            .flatMap(pdfBytes -> Mono.fromCallable(() -> {
                                        // CPU 집약적인 PDF 파싱 작업은 별도 스케줄러에서 실행
                                        String extractedText = pdfParserUtil.extractText(pdfBytes);
                                        String ncsSubCategory = pdfParserUtil.parseNcsSubCategory(extractedText);
                                        return new ParsedPdf(extractedText, ncsSubCategory);
                                    }).subscribeOn(Schedulers.boundedElastic())
                                    .doOnNext(parsed -> jobPostingPersistenceService.saveOrUpdateJobPosting(detail, pdfUrl, parsed.text, parsed.category))
                            )
                            .switchIfEmpty(Mono.fromRunnable(() -> 
                                    jobPostingPersistenceService.saveOrUpdateJobPosting(detail, pdfUrl, "", "일반무구분")
                            ))
                            .then();
                })
                .doOnError(e -> log.error("공고 일련번호 [{}] 처리 중 치명적 에러 발생: {}", sn, e.getMessage()))
                .then();
    }

    private record ParsedPdf(String text, String category) {}

    /**
     * 기존 동기 방식 유지 (필요 시 사용)
     */
    public void fetchAndSaveJobDetail(WebClient webClient, Long sn, String serviceKey) {
        fetchAndSaveJobDetailAsync(webClient, sn, serviceKey).block();
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
}