package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.classify.EmploymentType;
import com.jobai.backend.domain.crawler.classify.ExperienceLevel;
import com.jobai.backend.domain.crawler.classify.JobCategory;
import com.jobai.backend.domain.crawler.classify.JobClassifier;
import com.jobai.backend.domain.crawler.classify.JobClassifier.ClassificationResult;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateJobClassifyServiceTest {

    @Mock
    private PrivateJobPostingRepository repository;

    @Mock
    private JobClassifier jobClassifier;

    @InjectMocks
    private PrivateJobPostingService service;

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

    @Test
    @DisplayName("미분류 공고를 분류해 jobCategory 를 채운다")
    void classifiesUnclassified() {
        PrivateJobPosting p1 = posting("백엔드 개발자");
        PrivateJobPosting p2 = posting("영업 매니저");

        // 첫 조회는 2건, 둘째 조회는 0건(루프 종료)
        when(repository.findNeedsClassification(anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p1, p2)))
                .thenReturn(new PageImpl<>(List.of()));
        when(jobClassifier.classify(anyList()))
                .thenReturn(List.of(
                        new ClassificationResult(JobCategory.BACKEND, EmploymentType.FULL_TIME, ExperienceLevel.UNKNOWN),
                        new ClassificationResult(JobCategory.NON_TARGET, EmploymentType.UNKNOWN, ExperienceLevel.UNKNOWN)));

        int total = service.classifyUnclassified(100);

        assertThat(total).isEqualTo(2);
        assertThat(p1.getJobCategory()).isEqualTo("백엔드");
        assertThat(p2.getJobCategory()).isEqualTo("비대상");
    }

    @Test
    @DisplayName("분류할 공고가 없으면 0건, 분류기를 호출하지 않는다")
    void noUnclassified() {
        when(repository.findNeedsClassification(anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        int total = service.classifyUnclassified(100);

        assertThat(total).isZero();
        verifyNoInteractions(jobClassifier);
    }
}
