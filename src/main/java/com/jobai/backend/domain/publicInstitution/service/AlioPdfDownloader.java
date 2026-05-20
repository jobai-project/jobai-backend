package com.jobai.backend.domain.publicInstitution.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;


/*
    쿠키 수집, 리다이렉트 처리 등 복잡한 PDF 다운로드 로직을 전담하는 컴포넌트
 */
@Slf4j
@Component
public class AlioPdfDownloader {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER_PAGE = "https://opendata.alio.go.kr/new/odaApiMng/recrutInquiryDetail.do";

    public byte[] downloadPdf(String pdfUrl) {
        try {
            log.info("직무기술서 PDF 파일 실시간 다운로드 프로세스 시작: {}", pdfUrl);

            // 리다이렉트(302) 자동 추적을 활성화한 Netty HttpClient를 생성합니다.
            HttpClient httpClient = HttpClient.create().followRedirect(false);

            // 파일 다운로드 전용 익명 WebClient 스트림 생성
            WebClient baseClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                    .build();

            // ── STEP 1: 세션 쿠키 확보를 위해 상세 페이지(또는 메인 페이지)를 먼저 찌름. ──
            ClientResponse initialResponse = baseClient.get()
                    .uri(REFERER_PAGE)
                    .exchangeToMono(reactor.core.publisher.Mono::just)
                    .block();

            if (initialResponse == null) {
                log.warn("상세 페이지 초기 응답이 없습니다.");
                return null;
            }

            // 서버가 발급해 준 세션 쿠키(JSESSIONID 등)들을 통째로 수집합니다.
            MultiValueMap<String, ResponseCookie> serverCookies = initialResponse.cookies();
            log.info("▶️ [디버깅] 알리오 서버로부터 웹 세션 쿠키 수집 완료");

            HttpClient downloadHttp = HttpClient.create().followRedirect(true);

            WebClient downloadClient = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(downloadHttp))
                    .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                    .defaultHeader(HttpHeaders.REFERER, REFERER_PAGE)
                    .build();

            byte[] pdfBytes = downloadClient.get()
                    .uri(pdfUrl)
                    .cookies(cookies -> {
                        serverCookies.forEach((key, valueList) -> {
                            for (ResponseCookie cookie : valueList) {
                                cookies.add(key, cookie.getValue());
                            }
                        });
                    })
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            log.info("▶️ [디버깅] 다운로드된 PDF 바이트 크기: {} bytes", pdfBytes != null ? pdfBytes.length : 0);

            if (pdfBytes != null && pdfBytes.length == 8511) {
                log.error("🚨 방어막 우회 실패: 여전히 메인화면 HTML이 다운로드되었습니다.");
                return null;
            }

            return pdfBytes;
        } catch (Exception e) {
            log.error("PDF 다운로드 중 예외 발생: {}", e.getMessage());
            return null;
        }
    }
}
