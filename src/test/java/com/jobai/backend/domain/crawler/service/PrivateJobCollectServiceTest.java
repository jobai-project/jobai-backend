package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.classify.JobClassifier;
import com.jobai.backend.domain.crawler.engine.DeclarativeCrawler;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.jobai.backend.domain.crawler.classify.JobCategory;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import java.time.LocalDateTime;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 수집 오케스트레이터 {@link PrivateJobCollectService} 검증.
 *
 * <p>crawler·savingService 를 모킹해 "스펙 로드 → collect → saveAll" 연결만 검증한다.
 * 실제 스펙 로드는 resources(classpath)에서 일어나므로, 테스트는 클래스패스에 둔
 * 테스트용 스펙(specs/testco.yaml)으로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class PrivateJobCollectServiceTest {

    @Mock
    private DeclarativeCrawler crawler;

    @Mock
    private PrivateJobPostingService savingService;

    @Mock
    private JobClassifier jobClassifier;

    private PrivateJobCollectService service() {
        return new PrivateJobCollectService(crawler, savingService, jobClassifier);
    }

    private JobRecord record(String jobId, String title) {
        JobRecord r = new JobRecord();
        r.put("job_id", jobId);
        r.put("title", title);
        return r;
    }

    @Test
    @DisplayName("스펙을 로드해 수집한 결과를 그대로 saveAll 에 넘긴다")
    void loadsCollectsAndSaves() {
        // testco 스펙으로 수집되면 2건 반환하도록
        List<JobRecord> collected = List.of(record("1", "백엔드"), record("2", "프론트"));
        when(crawler.collect(any(CrawlSpec.class))).thenReturn(collected);
        when(savingService.saveAll(eq("testco"), any()))
                .thenReturn(new SaveResult(List.of(), 0, 0));

        service().collectAndSave("testco");

        // 1) 로드된 스펙이 crawler 로 들어갔는지 + company 가 testco 인지
        ArgumentCaptor<CrawlSpec> specCaptor = ArgumentCaptor.forClass(CrawlSpec.class);
        verify(crawler).collect(specCaptor.capture());
        assertThat(specCaptor.getValue().getCompany()).isEqualTo("testco");

        // 2) 수집 결과가 그대로 saveAll 로 전달됐는지 (정제·필터 없이 raw)
        ArgumentCaptor<List<JobRecord>> recCaptor = ArgumentCaptor.forClass(List.class);
        verify(savingService).saveAll(eq("testco"), recCaptor.capture());
        assertThat(recCaptor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("saveAll 의 결과(SaveResult)를 그대로 반환한다")
    void returnsResultFromSaving() {
        when(crawler.collect(any(CrawlSpec.class))).thenReturn(List.of(record("1", "x")));
        SaveResult expected = new SaveResult(List.of(), 3, 1);
        when(savingService.saveAll(eq("testco"), any())).thenReturn(expected);

        SaveResult result = service().collectAndSave("testco");

        assertThat(result.getUpdatedCount()).isEqualTo(3);
        assertThat(result.getClosedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("스펙 파일이 없으면 명확한 예외를 던진다")
    void throwsWhenSpecMissing() {
        assertThatThrownBy(() -> service().collectAndSave("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("스펙 파일을 찾을 수 없습니다");

        // 스펙 로드 실패 시 수집·저장은 일어나지 않아야
        verify(crawler, never()).collect(any());
        verify(savingService, never()).saveAll(any(), any());
    }

    @Test
    @DisplayName("스펙 company 와 요청 회사가 다르면 예외를 던진다")
    void throwsWhenCompanyMismatch() {
        // mismatch.yaml 은 파일명은 mismatch 지만 안의 company 는 anothercompany
        // → 파일은 찾지만 company 불일치로 예외가 나야 한다
        assertThatThrownBy(() -> service().collectAndSave("mismatch"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("일치하지 않습니다");

        // 불일치면 수집·저장이 일어나면 안 됨
        verify(crawler, never()).collect(any());
        verify(savingService, never()).saveAll(any(), any());
    }

    @Test
    @DisplayName("신규 공고가 있으면 분류기를 호출하고 분류 결과를 저장한다")
    void classifiesNewPostings() {
        when(crawler.collect(any(CrawlSpec.class)))
                .thenReturn(List.of(record("1", "백엔드 개발자")));

        PrivateJobPosting newPosting = posting("백엔드 개발자");
        when(savingService.saveAll(eq("testco"), any()))
                .thenReturn(new SaveResult(List.of(newPosting), 0, 0));
        when(jobClassifier.classify(any()))
                .thenReturn(List.of(JobCategory.BACKEND));

        service().collectAndSave("testco");

        // 1) 분류기가 신규 제목으로 호출됐는지
        ArgumentCaptor<List<String>> titlesCaptor = ArgumentCaptor.forClass(List.class);
        verify(jobClassifier).classify(titlesCaptor.capture());
        assertThat(titlesCaptor.getValue()).containsExactly("백엔드 개발자");

        // 2) 분류 결과가 엔티티에 반영됐는지
        assertThat(newPosting.getJobCategory()).isEqualTo("백엔드");

        // 3) 분류 결과 저장 루틴이 이어졌는지
        verify(savingService).saveClassified(any());
    }

    @Test
    @DisplayName("신규 공고가 없으면 분류기를 호출하지 않는다")
    void skipsClassificationWhenNoInserted() {
        when(crawler.collect(any(CrawlSpec.class)))
                .thenReturn(List.of(record("1", "x")));
        // inserted 가 비어있음 (전부 기존 공고였던 경우)
        when(savingService.saveAll(eq("testco"), any()))
                .thenReturn(new SaveResult(List.of(), 2, 0));

        service().collectAndSave("testco");

        // 신규 없음 → 분류기·분류저장 모두 호출 안 됨 (LLM 비용 절약)
        verify(jobClassifier, never()).classify(any());
        verify(savingService, never()).saveClassified(any());
    }

    @Test
    @DisplayName("분류가 실패해도 saveAll 결과는 정상 반환된다(예외 격리)")
    void classificationFailureDoesNotBreakSave() {
        when(crawler.collect(any(CrawlSpec.class)))
                .thenReturn(List.of(record("1", "백엔드 개발자")));
        PrivateJobPosting newPosting = posting("백엔드 개발자");
        SaveResult saveResult = new SaveResult(List.of(newPosting), 0, 0);
        when(savingService.saveAll(eq("testco"), any())).thenReturn(saveResult);
        when(jobClassifier.classify(any()))
                .thenThrow(new RuntimeException("LLM 장애"));

        // collectAndSave 는 예외 없이 SaveResult 를 반환해야 함
        SaveResult result = service().collectAndSave("testco");

        assertThat(result).isSameAs(saveResult);
    }

    private PrivateJobPosting posting(String title) {
        LocalDateTime now = LocalDateTime.now();
        return PrivateJobPosting.builder()
                .company("testco")
                .sourceJobId(title)
                .title(title)
                .isClosed(false)
                .lastSeenAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}