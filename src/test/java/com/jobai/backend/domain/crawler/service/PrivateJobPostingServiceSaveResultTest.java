package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PrivateJobPostingService#saveAll} 개선분 검증.
 *
 * <p>실제 DB 대신 repository 를 모킹한다. 검증 포인트:
 * <ul>
 *   <li>신규/변경/무변경/마감/마감부활이 SaveResult 에 정확히 분류되는가</li>
 *   <li>내용이 안 바뀐 공고는 INSERT(save) 되지 않는가</li>
 *   <li>회사 전체 조회(findAllByCompany)가 1회만 일어나는가(N+1 제거)</li>
 *   <li>0건 입력 시 빈 결과 + 기존 공고 보존(대량 오마감 방지)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PrivateJobPostingServiceSaveResultTest {

    @Mock
    private PrivateJobPostingRepository repository;

    @InjectMocks
    private PrivateJobPostingService service;

    private static final String COMPANY = "kakao";

    @BeforeEach
    void setUp() {
        // save 는 받은 엔티티를 그대로 돌려주도록(테스트 단순화)
        // lenient 가 아니면 일부 테스트에서 unnecessary stubbing 경고가 날 수 있어 필요한 테스트에서만 스텁
    }

    /** JobRecord 한 건 만들기 (Map 기반). */
    private JobRecord record(String jobId, String title, String description) {
        JobRecord r = new JobRecord();
        r.put("job_id", jobId);
        r.put("title", title);
        r.put("description", description);
        return r;
    }

    /** 기존 공고 엔티티 한 건 만들기. */
    private PrivateJobPosting existing(String sourceJobId, String title, String description,
                                       boolean closed) {
        LocalDateTime t1 = LocalDateTime.now().minusDays(1);
        return PrivateJobPosting.builder()
                .company(COMPANY)
                .sourceJobId(sourceJobId)
                .title(title)
                .description(description)
                .isClosed(closed)
                .lastSeenAt(t1)
                .createdAt(t1)
                .updatedAt(t1)
                .build();
    }

    @Test
    @DisplayName("신규 공고는 insert 되고 SaveResult.inserted 에 담긴다")
    void insertsNewPosting() {
        when(repository.findAllByCompany(COMPANY)).thenReturn(new ArrayList<>());

        List<JobRecord> records = List.of(record("E", "신입 개발자", "본문E"));
        SaveResult result = service.saveAll(COMPANY, records);

        assertThat(result.getInsertedCount()).isEqualTo(1);
        assertThat(result.getInserted().get(0).getSourceJobId()).isEqualTo("E");
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getClosedCount()).isZero();
        verify(repository, times(1)).save(any(PrivateJobPosting.class));
    }

    @Test
    @DisplayName("내용이 바뀐 기존 공고는 update 로 분류되고 save(insert) 는 일어나지 않는다")
    void updatesChangedPosting() {
        PrivateJobPosting b = existing("B", "기획자", "본문B-old", false);
        when(repository.findAllByCompany(COMPANY)).thenReturn(new ArrayList<>(List.of(b)));

        List<JobRecord> records = List.of(record("B", "기획자", "본문B-NEW"));  // description 변경
        SaveResult result = service.saveAll(COMPANY, records);

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(result.getInsertedCount()).isZero();
        // 기존 엔티티가 실제로 갱신됐는지
        assertThat(b.getDescription()).isEqualTo("본문B-NEW");
        // 신규가 아니므로 save 호출 안 됨
        verify(repository, never()).save(any(PrivateJobPosting.class));
    }

    @Test
    @DisplayName("내용이 같은 기존 공고는 update 되지 않는다(lastSeenAt 만 갱신)")
    void skipsUnchangedPosting() {
        PrivateJobPosting a = existing("A", "개발자", "본문A", false);
        LocalDateTime before = a.getUpdatedAt();
        when(repository.findAllByCompany(COMPANY)).thenReturn(new ArrayList<>(List.of(a)));

        List<JobRecord> records = List.of(record("A", "개발자", "본문A"));  // 동일
        SaveResult result = service.saveAll(COMPANY, records);

        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getInsertedCount()).isZero();
        assertThat(result.getClosedCount()).isZero();
        // updatedAt 은 그대로(실제 변경 아님), lastSeenAt 은 갱신
        assertThat(a.getUpdatedAt()).isEqualTo(before);
        assertThat(a.getLastSeenAt()).isAfter(before);
        verify(repository, never()).save(any(PrivateJobPosting.class));
    }

    @Test
    @DisplayName("마감됐던 공고가 다시 보이면 내용이 같아도 되살린다(update)")
    void revivesClosedPosting() {
        PrivateJobPosting d = existing("D", "마케터", "본문D", true);  // 마감 상태
        when(repository.findAllByCompany(COMPANY)).thenReturn(new ArrayList<>(List.of(d)));

        List<JobRecord> records = List.of(record("D", "마케터", "본문D"));  // 내용 동일
        SaveResult result = service.saveAll(COMPANY, records);

        // 내용은 같아도 마감 부활이라 update 로 친다
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(d.isClosed()).isFalse();   // 되살아남
    }

    @Test
    @DisplayName("이번 수집에 없는 기존 공고는 마감 처리된다")
    void closesMissingPosting() {
        PrivateJobPosting c = existing("C", "디자이너", "본문C", false);
        when(repository.findAllByCompany(COMPANY)).thenReturn(new ArrayList<>(List.of(c)));

        // C 는 수집 결과에 없음, 다른 공고 E 만 들어옴
        List<JobRecord> records = List.of(record("E", "신입", "본문E"));
        SaveResult result = service.saveAll(COMPANY, records);

        assertThat(result.getClosedCount()).isEqualTo(1);
        assertThat(c.isClosed()).isTrue();
        assertThat(result.getInsertedCount()).isEqualTo(1);  // E 는 신규
    }

    @Test
    @DisplayName("회사 전체 조회는 한 번만 일어난다(N+1 제거)")
    void queriesCompanyOnce() {
        PrivateJobPosting a = existing("A", "개발자", "본문A", false);
        PrivateJobPosting b = existing("B", "기획자", "본문B", false);
        when(repository.findAllByCompany(COMPANY))
                .thenReturn(new ArrayList<>(List.of(a, b)));

        // 공고 여러 건이어도 findAllByCompany 는 1회, findByCompanyAndSourceJobId 는 0회여야
        List<JobRecord> records = List.of(
                record("A", "개발자", "본문A"),
                record("B", "기획자", "본문B"),
                record("E", "신입", "본문E"));
        service.saveAll(COMPANY, records);

        verify(repository, times(1)).findAllByCompany(COMPANY);
        verify(repository, never()).findByCompanyAndSourceJobId(any(), any());
    }

    @Test
    @DisplayName("수집 0건이면 빈 결과를 주고 기존 공고를 건드리지 않는다(대량 오마감 방지)")
    void emptyRecordsProtects() {
        SaveResult result = service.saveAll(COMPANY, List.of());

        assertThat(result.getInsertedCount()).isZero();
        assertThat(result.getUpdatedCount()).isZero();
        assertThat(result.getClosedCount()).isZero();
        // 조회조차 하지 않아야(0건 빠른 반환)
        verify(repository, never()).findAllByCompany(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("job_id 가 없으면 예외를 던진다")
    void throwsWhenNoJobId() {
        when(repository.findAllByCompany(COMPANY)).thenReturn(new ArrayList<>());

        JobRecord bad = new JobRecord();
        bad.put("title", "제목만 있음");   // job_id 없음

        try {
            service.saveAll(COMPANY, List.of(bad));
            assertThat(false).as("예외가 났어야 함").isTrue();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("job_id");
        }
    }
}
