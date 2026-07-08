package com.jobai.backend.domain.publicInstitution.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobDataSyncServiceTest {

    private static final String SERVICE_KEY = "test-service-key";

    private MockWebServer server;
    private JobDetailSyncService jobDetailSyncService;
    private JobDataSyncService syncService;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        jobDetailSyncService = Mockito.mock(JobDetailSyncService.class);
        WebClient webClient = WebClient.builder().build();
        syncService = new JobDataSyncService(jobDetailSyncService, webClient);

        setField(syncService, "serviceKey", SERVICE_KEY);
        setField(syncService, "baseUrl", server.url("/").toString().replaceAll("/$", ""));
        setField(syncService, "syncEnabled", true);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void setField(Object target, String name, Object value) throws Exception {
        var field = JobDataSyncService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String listJson(long... sns) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < sns.length; i++) {
            if (i > 0) items.append(",");
            items.append("{\"recrutPblntSn\":").append(sns[i])
                    .append(",\"recrutPbancTtl\":\"채용공고 ").append(sns[i]).append("\"")
                    .append(",\"instNm\":\"기관\",\"recrutSeNm\":\"신입\",\"workRgnNmLst\":\"서울\"")
                    .append(",\"pbancBgngYmd\":\"20260101\",\"pbancEndYmd\":\"20260131\"}");
        }
        return "{\"resultCode\":0,\"resultMsg\":\"OK\",\"totalCount\":" + sns.length + ",\"result\":[" + items + "]}";
    }

    @Test
    @DisplayName("정상 응답: 각 공고의 상세 조회/저장을 호출한다")
    void syncPublicJobOpenings_정상처리() {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody(listJson(224316L, 300000L)));
        when(jobDetailSyncService.fetchAndSaveJobDetailAsync(any(), any(), eq(SERVICE_KEY)))
                .thenReturn(Mono.empty());

        syncService.syncPublicJobOpenings();

        verify(jobDetailSyncService).fetchAndSaveJobDetailAsync(any(), eq(224316L), eq(SERVICE_KEY));
        verify(jobDetailSyncService).fetchAndSaveJobDetailAsync(any(), eq(300000L), eq(SERVICE_KEY));
    }

    @Test
    @DisplayName("result가 없으면 상세 조회 없이 조기 종료한다")
    void syncPublicJobOpenings_결과없음_조기종료() {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody("{\"resultCode\":1,\"resultMsg\":\"NO_DATA\",\"totalCount\":0}"));

        syncService.syncPublicJobOpenings();

        verifyNoInteractions(jobDetailSyncService);
    }

    @Test
    @DisplayName("개별 공고 처리 실패는 전체 동기화를 막지 않는다")
    void syncPublicJobOpenings_개별실패_계속진행() {
        server.enqueue(new MockResponse()
                .setHeader("content-type", "application/json")
                .setBody(listJson(1L, 2L)));
        when(jobDetailSyncService.fetchAndSaveJobDetailAsync(any(), eq(1L), eq(SERVICE_KEY)))
                .thenReturn(Mono.error(new RuntimeException("상세 조회 실패")));
        when(jobDetailSyncService.fetchAndSaveJobDetailAsync(any(), eq(2L), eq(SERVICE_KEY)))
                .thenReturn(Mono.empty());

        assertThatCode(() -> syncService.syncPublicJobOpenings()).doesNotThrowAnyException();

        verify(jobDetailSyncService).fetchAndSaveJobDetailAsync(any(), eq(1L), eq(SERVICE_KEY));
        verify(jobDetailSyncService).fetchAndSaveJobDetailAsync(any(), eq(2L), eq(SERVICE_KEY));
    }

    @Test
    @DisplayName("목록 API가 실패(잘못된 서비스키 등)하면 재시도 후 예외를 전파한다")
    @Timeout(30)
    void syncPublicJobOpenings_목록API실패_예외전파() {
        // 최초 1회 + 3회 재시도 = 총 4번의 실패 응답 필요
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setResponseCode(401).setBody("SERVICE_KEY_IS_NOT_REGISTERED_ERROR"));
        }

        assertThatThrownBy(() -> syncService.syncPublicJobOpenings())
                .isInstanceOf(Exception.class);

        verifyNoInteractions(jobDetailSyncService);
    }

    @Test
    @DisplayName("scheduledSync: public-job.sync.enabled=false면 실행되지 않는다")
    void scheduledSync_비활성화() throws Exception {
        setField(syncService, "syncEnabled", false);

        syncService.scheduledSync();

        verifyNoInteractions(jobDetailSyncService);
    }

    @Test
    @DisplayName("scheduledSync: 내부에서 예외가 발생해도 밖으로 전파되지 않는다")
    void scheduledSync_예외를_삼킨다() throws Exception {
        JobDataSyncService spyService = Mockito.spy(syncService);
        setField(spyService, "syncEnabled", true);
        doThrow(new RuntimeException("동기화 실패")).when(spyService).syncPublicJobOpenings();

        assertThatCode(spyService::scheduledSync).doesNotThrowAnyException();
    }
}
