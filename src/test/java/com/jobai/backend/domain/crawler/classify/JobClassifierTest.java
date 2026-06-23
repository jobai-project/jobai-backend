package com.jobai.backend.domain.crawler.classify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.llm.AnthropicClient;
import com.jobai.backend.global.llm.LlmException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class JobClassifierTest {

    private AnthropicClient anthropicClient;
    private JobClassifier classifier;

    @BeforeEach
    void setUp() {
        anthropicClient = Mockito.mock(AnthropicClient.class);
        classifier = new JobClassifier(anthropicClient, new ObjectMapper());
    }

    @Test
    @DisplayName("정상 응답: 제목 순서대로 분류된다")
    void classifiesInOrder() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[\"백엔드\", \"비대상\", \"디자이너\"]");

        List<JobCategory> result = classifier.classify(
                List.of("백엔드 개발자", "영업 매니저", "프로덕트 디자이너"));

        assertThat(result).containsExactly(
                JobCategory.BACKEND, JobCategory.NON_TARGET, JobCategory.DESIGNER);
    }

    @Test
    @DisplayName("개수 불일치: 그 배치는 전부 미분류로 처리된다")
    void countMismatchFallsBackToUnclassified() {
        // 제목 3개인데 응답은 2개 → 전부 미분류
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[\"백엔드\", \"비대상\"]");

        List<JobCategory> result = classifier.classify(
                List.of("제목1", "제목2", "제목3"));

        assertThat(result).containsExactly(null, null, null);
    }

    @Test
    @DisplayName("목록 밖 라벨: 해당 항목만 미분류, 나머지는 정상")
    void unknownLabelBecomesUnclassified() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[\"백엔드\", \"서버개발\", \"디자이너\"]");

        List<JobCategory> result = classifier.classify(
                List.of("제목1", "제목2", "제목3"));

        assertThat(result).containsExactly(
                JobCategory.BACKEND, JobCategory.UNCLASSIFIED, JobCategory.DESIGNER);
    }

    @Test
    @DisplayName("응답에 설명이 섞여도 JSON 배열만 추출해 파싱한다")
    void extractsJsonFromNoisyResponse() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("분류 결과: [\"백엔드\", \"AI/ML\"] 이상입니다");

        List<JobCategory> result = classifier.classify(List.of("제목1", "제목2"));

        assertThat(result).containsExactly(JobCategory.BACKEND, JobCategory.AI_ML);
    }

    @Test
    @DisplayName("깨진 JSON: 그 배치는 전부 미분류")
    void brokenJsonFallsBack() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[\"백엔드\", 비대상");

        List<JobCategory> result = classifier.classify(List.of("제목1", "제목2"));

        assertThat(result).containsExactly(null, null);
    }

    @Test
    @DisplayName("LLM 호출 실패(401 등): 그 배치는 전부 미분류, 전체는 살아남는다")
    void llmFailureFallsBack() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("Anthropic 호출 실패: 401"));

        List<JobCategory> result = classifier.classify(List.of("제목1", "제목2"));

        assertThat(result).containsExactly(null, null);
    }

    @Test
    @DisplayName("빈 입력: 빈 결과를 주고 LLM 을 호출하지 않는다")
    void emptyInput() {
        List<JobCategory> result = classifier.classify(List.of());

        assertThat(result).isEmpty();
        Mockito.verifyNoInteractions(anthropicClient);
    }

    @Test
    @DisplayName("여러 배치 중 한 배치만 실패하면 그 배치만 null, 나머지는 정상 분류된다")
    void partialBatchFailureIsolated() {
        // BATCH_SIZE=10 기준: 11건 입력 → 배치1(10건) + 배치2(1건)
        // 첫 호출(배치1)은 실패, 둘째 호출(배치2)은 정상
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenThrow(new LlmException("Anthropic 호출 실패: 401"))   // 배치1 실패
                .thenReturn("[\"백엔드\"]");                                // 배치2 정상

        List<String> titles = new ArrayList<>();
        for (int i = 1; i <= 10; i++) titles.add("제목" + i);   // 배치1: 10건
        titles.add("백엔드 개발자");                              // 배치2: 1건

        List<JobCategory> result = classifier.classify(titles);

        // 전체 11건 결과
        assertThat(result).hasSize(11);
        // 배치1(0~9): 실패 → 전부 null (재시도 대상)
        assertThat(result.subList(0, 10)).containsOnlyNulls();
        // 배치2(10): 정상 → 백엔드
        assertThat(result.get(10)).isEqualTo(JobCategory.BACKEND);
    }

    @Test
    @DisplayName("배치 경계를 넘어도 입력 순서대로 1:1 분류된다")
    void preservesOrderAcrossBatches() {
        when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                .thenReturn("[\"백엔드\",\"프론트엔드\",\"풀스택\",\"모바일\",\"AI/ML\","
                        + "\"보안\",\"QA/테스트\",\"임베디드\",\"디자이너\",\"PM/기획\"]")  // 배치1: 10개
                .thenReturn("[\"비대상\"]");                                                  // 배치2: 1개

        List<String> titles = new ArrayList<>();
        for (int i = 1; i <= 11; i++) titles.add("제목" + i);

        List<JobCategory> result = classifier.classify(titles);

        assertThat(result).hasSize(11);
        assertThat(result.get(0)).isEqualTo(JobCategory.BACKEND);    // 배치1 첫
        assertThat(result.get(9)).isEqualTo(JobCategory.PM);         // 배치1 끝
        assertThat(result.get(10)).isEqualTo(JobCategory.NON_TARGET); // 배치2
    }
}