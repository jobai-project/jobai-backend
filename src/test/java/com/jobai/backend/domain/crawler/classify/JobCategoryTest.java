package com.jobai.backend.domain.crawler.classify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobCategoryTest {

    @Test
    @DisplayName("fromLabel: 정상 라벨은 해당 enum 으로 변환된다")
    void fromLabelNormal() {
        assertThat(JobCategory.fromLabel("백엔드")).isEqualTo(JobCategory.BACKEND);
        assertThat(JobCategory.fromLabel("AI/ML")).isEqualTo(JobCategory.AI_ML);
        assertThat(JobCategory.fromLabel("비대상")).isEqualTo(JobCategory.NON_TARGET);
    }

    @Test
    @DisplayName("fromLabel: 앞뒤 공백은 무시하고 변환한다")
    void fromLabelTrims() {
        assertThat(JobCategory.fromLabel("  백엔드  ")).isEqualTo(JobCategory.BACKEND);
    }

    @Test
    @DisplayName("fromLabel: 목록 밖 라벨/null/빈값은 미분류로 떨어진다")
    void fromLabelFallback() {
        assertThat(JobCategory.fromLabel("서버개발")).isEqualTo(JobCategory.UNCLASSIFIED);
        assertThat(JobCategory.fromLabel(null)).isEqualTo(JobCategory.UNCLASSIFIED);
        assertThat(JobCategory.fromLabel("")).isEqualTo(JobCategory.UNCLASSIFIED);
    }

    @Test
    @DisplayName("isMatchTarget: 개발/디자이너/PM 은 true, 비대상/미분류는 false")
    void isMatchTarget() {
        assertThat(JobCategory.BACKEND.isMatchTarget()).isTrue();
        assertThat(JobCategory.AI_ML.isMatchTarget()).isTrue();
        assertThat(JobCategory.DESIGNER.isMatchTarget()).isTrue();
        assertThat(JobCategory.PM.isMatchTarget()).isTrue();
        assertThat(JobCategory.NON_TARGET.isMatchTarget()).isFalse();
        assertThat(JobCategory.UNCLASSIFIED.isMatchTarget()).isFalse();
    }

    @Test
    @DisplayName("labelsForPrompt: 모든 라벨이 콤마로 이어진다")
    void labelsForPrompt() {
        String labels = JobCategory.labelsForPrompt();
        assertThat(labels).contains("백엔드", "AI/ML", "비대상", "미분류");
        assertThat(labels).contains(", ");
    }
}