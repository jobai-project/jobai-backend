package com.jobai.backend.domain.publicInstitution.service;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.util.List;

/**
    공공기관에서 현재 채용중인 채용공고의 목록을 가져옴.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobDataSyncService {

    private final JobDetailSyncService jobDetailSyncService;

    @Value("${api.data-go-kr.service-key}")
    private String serviceKey;

    @Transactional
    public void syncPublicJobOpenings() {
        // 1. 공공데이터 서비스키 중복 인코딩 방지를 위한 URI 팩토리 설정
        DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory("https://apis.data.go.kr");
        factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);

        // WebClient의 인메모리 버퍼 최대 크기 설정
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        
        WebClient webClient = WebClient.builder()
                .uriBuilderFactory(factory)
                .baseUrl("https://apis.data.go.kr")
                .exchangeStrategies(exchangeStrategies) // 인메모리 버퍼 크기 설정
                .build();

        // 2. 외부 공공 API 호출 (기재부 공공기관 채용정보 API)
        PublicJobListResponse apiResponse = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/1051000/recruitment/list") // 오픈 API 상세 명세서 상의 Endpoint URI 기재
                        .queryParam("serviceKey", serviceKey) // 인코딩된 인증키 그대로 통과
                        .queryParam("pageNo", "1")
                        .queryParam("numOfRows", "50")       // 한번에 땡겨올 공고 데이터 수 TODO 필요한 값으로 수정
                        .queryParam("ongoingYn", "Y")
                        .queryParam("ncsCdLst", "R600020")  // NCS 대분류 정보통신에 포함되는 공고만 받아오도록 함
                        .queryParam("_type", "json")          // JSON 응답 포맷 강제 지정
                        .build())
                .retrieve()
                .bodyToMono(PublicJobListResponse.class)
                .block(); // 외부 연동 배치 작업이므로 간결하게 동기(block) 처리

        // 디버깅용 주석
        if (apiResponse == null || apiResponse.result() == null) {
            log.error("공공데이터 API 호출 실패 혹은 데이터 파싱 불가능 (apiResponse 또는 result 배열이 null입니다.)");
            return;
        }

        List<PublicJobListResponse.Item> apiItems = apiResponse.result();
        log.info("공공기관 채용 공고 데이터 {}건 수집 완료. 수집 데이터 저장 프로세스를 시작합니다.", apiItems.size());

        // 3. 수집된 데이터를 하나씩 순회하며 Upsert (있으면 업데이트, 없으면 신규 저장)
        for (PublicJobListResponse.Item briefItem : apiItems) {
            Long sn = briefItem.recrutPblntSn();

//            log.info("sn = {}", sn.toString()); // sn은 정상적으로 받아옴.
            // 핵심 비즈니스 로직 및 저장 처리는 분리된 서비스 클래스가 처리함
            jobDetailSyncService.fetchAndSaveJobDetail(webClient, sn, serviceKey);

            try {
                Thread.sleep(100); // 외부 API 가혹 부하 방지용 짧은 휴식
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("공공기관 채용 공고 DB 동기화가 성공적으로 완료되었습니다.");
    }
}