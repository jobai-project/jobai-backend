package com.jobai.backend.domain.publicInstitution.service;


import com.jobai.backend.domain.publicInstitution.dto.PublicJobDetailResponse;
import com.jobai.backend.domain.publicInstitution.entity.JobPosting;
import com.jobai.backend.domain.publicInstitution.repository.JobPostingRepository;
import com.jobai.backend.global.util.PdfParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDetailSyncService {

    private final JobPostingRepository jobPostingRepository;
    private final PdfParserUtil pdfParserUtil;
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
                            .queryParam("sn", sn)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .bodyToMono(PublicJobDetailResponse.class)
                    .block();

            if (detailResponse == null || detailResponse.result() == null) {
                log.warn("공고 일련번호 [{}] 상세 데이터가 비어있어 스킵합니다.", sn);
                return;
            }

            PublicJobDetailResponse.DetailItem detail = detailResponse.result();

            // PDF 파일 필터링 추출
            String pdfUrl = null;
            if (detail.files() != null) {
                pdfUrl = detail.files().stream()
                        .filter(f -> f.atchFileNm().contains("직무기술서") || "C".equals(f.atchFileType()))
                        .map(PublicJobDetailResponse.FileItem::url)
                        .findFirst()
                        .orElse(null);
            }
            /*
            PDF 텍스트 추출 작동 구역
             */
            String extractedText = "";
            String ncsSubCategory = "일반무구분";

            if (pdfUrl != null) {
                try {
                    log.info("직무기술서 PDF 파일 실시간 다운로드 프로세스 시작: {}", pdfUrl);

                    // 리다이렉트(302) 자동 추적을 활성화한 Netty HttpClient를 생성합니다.
                    HttpClient httpClient = HttpClient.create().followRedirect(false);

                    String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
                    String refererPage = "https://opendata.alio.go.kr/new/odaApiMng/recrutInquiryDetail.do";

                    // 파일 다운로드 전용 익명 WebClient 스트림 생성
                    WebClient baseClient = WebClient.builder()
                            .clientConnector(new ReactorClientHttpConnector(httpClient))
                            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                            .build();

                    // ── STEP 1: 세션 쿠키 확보를 위해 상세 페이지(또는 메인 페이지)를 먼저 가볍게 찌릅니다. ──
                    ClientResponse initialResponse = baseClient.get()
                            .uri(refererPage) // 대시보드 상세 페이지 방문
                            .exchangeToMono(reactor.core.publisher.Mono::just)
                            .block();

                    // 서버가 발급해 준 세션 쿠키(JSESSIONID 등)들을 통째로 수집합니다.
                    MultiValueMap<String, ResponseCookie> serverCookies = initialResponse.cookies();
                    log.info("▶️ [디버깅] 알리오 서버로부터 웹 세션 쿠키 수집 완료");

                    HttpClient downloadHttp = HttpClient.create().followRedirect(true);

                    WebClient downloadClient = WebClient.builder()
                            .clientConnector(new ReactorClientHttpConnector(downloadHttp))
                            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                            .defaultHeader(HttpHeaders.REFERER, refererPage) // 핵심: 파일이 있던 상세 페이지 주소를 레퍼러로 박음
                            .build();

                    byte[] pdfBytes = downloadClient.get()
                            .uri(pdfUrl)
                            .cookies(cookies -> {
                                // STEP 1에서 가저온 세션 쿠키들을 다운로드 요청 헤더에 그대로 이식합니다.
                                serverCookies.forEach((key, valueList) -> {
                                    for (ResponseCookie cookie : valueList) {
                                        cookies.add(key, cookie.getValue());
                                    }
                                });
                            })
                            .retrieve()
                            .bodyToMono(byte[].class)
                            .block();


//                    byte[] pdfBytes = downloadClient.get()
//                            .uri(pdfUrl)
//                            .retrieve()
//                            .bodyToMono(byte[].class) // 바이너리 스트림 변환
//                            .block();

                    // TODO 디버깅 로그 해결후 삭제
                    log.info("▶️ [디버깅] 다운로드된 PDF 바이트 크기: {} bytes", pdfBytes != null ? pdfBytes.length : 0);

                    if (pdfBytes != null && pdfBytes.length == 8511) {
                        log.error("🚨 방어막 우회 실패: 여전히 메인화면 HTML이 다운로드되었습니다.");
                        return;
                    }

                    // 유틸리티를 호출하여 복잡한 PDF 레이아웃 해제 후 텍스트 추출
                    extractedText = pdfParserUtil.extractText(pdfBytes);
                    log.info("▶️ [디버깅] PDF에서 추출된 총 텍스트 글자 수: {}자", extractedText.length());

                    ncsSubCategory = pdfParserUtil.parseNcsSubCategory(extractedText);

                    log.info("직무기술서 파싱 완료! 매칭된 소분류: [{}]", ncsSubCategory);
                } catch (Exception pdfEx) {
                    log.error("공고 [{}]의 PDF 다운로드 혹은 파싱 중 예외 발생 (기본 텍스트 데이터 보존): {}", sn, pdfEx.getMessage());
                }
            }


            LocalDate startDate = parseLocalDate(detail.pbancBgngYmd());
            LocalDate endDate = parseLocalDate(detail.pbancEndYmd());
            String pblntfNoStr = String.valueOf(detail.recrutPblntSn());

            String finalPdfUrl = (pdfUrl != null) ? pdfUrl : "없음";
            String finalExtractedText = extractedText;
            String finalNcsSub = ncsSubCategory;
            
            jobPostingRepository.findByPblntfNo(pblntfNoStr)
                    .ifPresentOrElse(
                            existingPost -> {
                                existingPost.updateDetailedInfo(
                                        detail.recrutPbancTtl(), detail.recrutSeNm(), detail.workRgnNmLst(), endDate,
                                        detail.scrnprcdrMthdExpln(), detail.ncsCdNmLst(), // 접수방법, 모집직무 매핑
                                        detail.aplyQlfcCn(), detail.disqlfcRsn(), finalPdfUrl,
                                        finalExtractedText, finalNcsSub // PDF 텍스트추출 구역
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
                                        .extractedHtmlText(finalExtractedText) // PDF
                                        .ncsSubCategory(finalNcsSub)    // PDF
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