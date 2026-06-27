package com.jobai.backend.domain.publicInstitution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;


/*
    쿠키 수집, 리다이렉트 처리 등 복잡한 PDF 다운로드 로직을 전담하는 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlioPdfDownloader {

    private final org.springframework.web.reactive.function.client.WebClient webClient;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER_PAGE = "https://opendata.alio.go.kr/new/odaApiMng/recrutInquiryDetail.do";

    public Mono<byte[]> downloadFile(String fileUrl) {
        log.info("직무기술서 파일 실시간 다운로드 프로세스 시작: {}", fileUrl);

        // 1. 세션 쿠키 확보를 위한 요청
        return webClient.get()
                .uri(REFERER_PAGE)
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .exchangeToMono(response -> {
                    MultiValueMap<String, String> cookies = new LinkedMultiValueMap<>();
                    response.cookies().forEach((name, cookieList) -> {
                        cookieList.forEach(cookie -> cookies.add(name, cookie.getValue()));
                    });

                    // 2. 수집된 쿠키를 가지고 실제 파일 다운로드
                    return webClient.get()
                            .uri(fileUrl)
                            .header(HttpHeaders.USER_AGENT, USER_AGENT)
                            .header(HttpHeaders.REFERER, REFERER_PAGE)
                            .cookies(c -> c.addAll(cookies))
                            .retrieve()
                            .bodyToMono(byte[].class);
                })
                .timeout(Duration.ofSeconds(20))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2)))
                .filter(bytes -> {
                    if (bytes == null || bytes.length == 0) {
                        log.error("빈 응답을 수신했습니다: {}", fileUrl);
                        return false;
                    }
                    return true;
                })
                .doOnError(e -> log.error("파일 다운로드 중 예외 발생: {}", e.getMessage()));
    }
}
