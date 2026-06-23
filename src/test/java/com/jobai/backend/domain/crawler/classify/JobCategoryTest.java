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
    @DisplayName("labelsForPrompt: 전체 라벨이 정의 순서대로 정확히 이어진다")
    void labelsForPromptExact() {
        String expected = "백엔드, 프론트엔드, 풀스택, 모바일, AI/ML, 데이터엔지니어링, "
                + "DevOps/인프라, 보안, QA/테스트, 임베디드, 기타개발, "
                + "디자이너, PM/기획, 비대상, 미분류";

        assertThat(JobCategory.labelsForPrompt()).isEqualTo(expected);
    }

    @Test
    @DisplayName("택소노미는 정확히 15개 라벨로 고정된다")
    void taxonomyIsFixed() {
        assertThat(JobCategory.values()).hasSize(15);
    }

    @Test
    @DisplayName("매칭 대상은 정확히 13개(개발 11 + 디자이너 + PM)")
    void matchTargetsAreFixed() {
        long matchTargets = java.util.Arrays.stream(JobCategory.values())
                .filter(JobCategory::isMatchTarget)
                .count();
        assertThat(matchTargets).isEqualTo(13);
    }
}