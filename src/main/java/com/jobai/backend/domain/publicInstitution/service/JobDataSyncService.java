package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
    공공기관에서 현재 채용중인 채용공고의 목록을 가져옴.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDataSyncService {

    private final JobDetailSyncService jobDetailSyncService;
    private final WebClient webClient;

    @Value("${api.data-go-kr.service-key}")
    private String serviceKey;

    private static final String BASE_URL = "https://apis.data.go.kr";
    private static final int MAX_CONCURRENCY = 5; // 동시에 처리할 항목 수

    public void syncPublicJobOpenings() {
        // 1. 공공데이터 서비스키 중복 인코딩 방지를 위한 URI 팩토리 설정 (WebClient 재생성 대신 속성만 변경하여 사용)
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(BASE_URL);
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        
        WebClient syncClient = webClient.mutate()
                .uriBuilderFactory(factory)
                .baseUrl(BASE_URL)
                .build();

        // 2. 외부 공공 API 호출 (기재부 공공기관 채용정보 API)
        PublicJobListResponse apiResponse = syncClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/1051000/recruitment/list")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", "1")
                        .queryParam("numOfRows", "5")
                        .queryParam("ongoingYn", "Y")
                        .queryParam("ncsCdLst", "R600020")
                        .queryParam("_type", "json")
                        .build())
                .retrieve()
                .bodyToMono(PublicJobListResponse.class)
                .timeout(Duration.ofSeconds(15)) // 전체 요청 타임아웃
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)) // 3회 재시도 (지수 백오프)
                        .filter(throwable -> !(throwable instanceof IllegalArgumentException))) // 특정 예외 제외 가능
                .block();

        if (apiResponse == null || apiResponse.result() == null) {
            log.error("공공데이터 API 호출 실패 혹은 데이터 파싱 불가능");
            return;
        }

        List<PublicJobListResponse.Item> apiItems = apiResponse.result();
        log.info("공공기관 채용 공고 데이터 {}건 수집 완료. 수집 데이터 저장 프로세스를 시작합니다.", apiItems.size());

        // 3. Flux를 활용한 병렬 처리 (스레드 블로킹 최소화 및 속도 향상)
        Flux.fromIterable(apiItems)
                .flatMap(briefItem -> {
                    Long sn = briefItem.recrutPblntSn();
                    // 개별 상세 조회 및 저장을 별도 Mono로 래핑하여 flatMap에서 비동기 실행
                    return jobDetailSyncService.fetchAndSaveJobDetailAsync(syncClient, sn, serviceKey)
                            .onErrorResume(e -> {
                                log.error("공고 일련번호 [{}] 처리 중 에러 발생: {}", sn, e.getMessage());
                                return reactor.core.publisher.Mono.empty();
                            });
                }, MAX_CONCURRENCY) // 최대 5개까지 동시 실행 (API 서버 부하 조절)
                .collectList()
                .block(); // 전체 배치 완료를 대기

        log.info("공공기관 채용 공고 DB 동기화가 성공적으로 완료되었습니다.");
    }
}