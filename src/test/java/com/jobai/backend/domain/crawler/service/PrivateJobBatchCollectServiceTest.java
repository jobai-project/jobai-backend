package com.jobai.backend.domain.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PrivateJobBatchCollectServiceTest {

    private PrivateJobCollectService collectService;
    private PrivateJobBatchCollectService batchService;

    @BeforeEach
    void setUp() throws Exception {
        collectService = Mockito.mock(PrivateJobCollectService.class);
        batchService = new PrivateJobBatchCollectService(collectService);

        // @Value 필드를 리플렉션으로 설정
        var field = PrivateJobBatchCollectService.class.getDeclaredField("excludeCompanies");
        field.setAccessible(true);
        field.set(batchService, Set.of("testco"));
    }

    @Test
    @DisplayName("모든 회사에 대해 collectAndSave가 호출되고 testco는 제외된다")
    void collectAll_모든회사_testco제외() {
        when(collectService.collectAndSave(anyString()))
                .thenReturn(new SaveResult(List.of(), 0, 0));

        batchService.collectAll();

        // testco 는 호출되지 않아야 한다
        verify(collectService, never()).collectAndSave("testco");

        // specs 디렉토리의 나머지 회사들은 모두 호출되어야 한다
        verify(collectService).collectAndSave("kakao");
        verify(collectService).collectAndSave("coupang");
        verify(collectService).collectAndSave("naver");
        verify(collectService).collectAndSave("toss");
        verify(collectService).collectAndSave("woowahan");
        verify(collectService).collectAndSave("daangn");
    }

    @Test
    @DisplayName("한 회사가 실패해도 나머지 회사는 계속 수집된다")
    void collectAll_부분실패_계속진행() {
        // 첫 번째 호출만 실패, 나머지는 성공
        when(collectService.collectAndSave(anyString()))
                .thenThrow(new RuntimeException("크롤링 실패"))
                .thenReturn(new SaveResult(List.of(), 0, 0));

        batchService.collectAll();

        // 실패한 회사 이후에도 나머지 회사에 대해 호출이 계속 되어야 한다
        verify(collectService, atLeast(2)).collectAndSave(anyString());
    }

    @Test
    @DisplayName("수집 결과 건수가 정확히 합산된다")
    void collectAll_결과합산() {
        when(collectService.collectAndSave(anyString()))
                .thenReturn(new SaveResult(List.of(), 1, 0));

        batchService.collectAll();

        // 예외 없이 모든 회사가 호출되었는지 확인
        verify(collectService, never()).collectAndSave("testco");
        verify(collectService, atLeast(5)).collectAndSave(anyString());
    }
}
