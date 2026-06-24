package com.jobai.backend.domain.search.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordMatcherTest {

    private KeywordMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new KeywordMatcher();
    }

    @Test
    @DisplayName("단일 카테고리 키워드 매칭 - '백엔드' → 백엔드")
    void matchSingleCategory() {
        Optional<SearchCondition> result = matcher.match("백엔드");

        assertThat(result).isPresent();
        assertThat(result.get().categories()).containsExactly("백엔드");
        assertThat(result.get().method()).isEqualTo(SearchCondition.METHOD_KEYWORD);
    }

    @Test
    @DisplayName("영문 키워드 매칭 - 'react' → 프론트엔드")
    void matchEnglishKeyword() {
        Optional<SearchCondition> result = matcher.match("react");

        assertThat(result).isPresent();
        assertThat(result.get().categories()).containsExactly("프론트엔드");
    }

    @Test
    @DisplayName("복합 입력 - '서울 신입 백엔드' → 카테고리 + 지역 + 경력")
    void matchComposite() {
        Optional<SearchCondition> result = matcher.match("서울 신입 백엔드");

        assertThat(result).isPresent();
        SearchCondition condition = result.get();
        assertThat(condition.categories()).containsExactly("백엔드");
        assertThat(condition.location()).isEqualTo("서울");
        assertThat(condition.experience()).isEqualTo("신입");
    }

    @Test
    @DisplayName("대소문자 무시 - 'JAVA Spring' → 백엔드")
    void matchCaseInsensitive() {
        Optional<SearchCondition> result = matcher.match("JAVA Spring");

        assertThat(result).isPresent();
        assertThat(result.get().categories()).containsExactly("백엔드");
    }

    @Test
    @DisplayName("여러 카테고리 매칭 - 'react node' → 프론트엔드 + 백엔드")
    void matchMultipleCategories() {
        Optional<SearchCondition> result = matcher.match("react node");

        assertThat(result).isPresent();
        assertThat(result.get().categories()).containsExactlyInAnyOrder("프론트엔드", "백엔드");
    }

    @Test
    @DisplayName("매칭 실패 - 애매한 자연어 입력은 빈 결과")
    void noMatchForAmbiguousInput() {
        Optional<SearchCondition> result = matcher.match("혼자 집중해서 개발하는 일");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("빈 입력 → 빈 결과")
    void emptyInput() {
        assertThat(matcher.match("")).isEmpty();
        assertThat(matcher.match(null)).isEmpty();
        assertThat(matcher.match("   ")).isEmpty();
    }

    @Test
    @DisplayName("지역만 입력 - '판교' → 지역 필터만")
    void matchLocationOnly() {
        Optional<SearchCondition> result = matcher.match("판교");

        assertThat(result).isPresent();
        assertThat(result.get().categories()).isEmpty();
        assertThat(result.get().location()).isEqualTo("판교");
    }

    @Test
    @DisplayName("경력만 입력 - '시니어' → 경력 필터만")
    void matchExperienceOnly() {
        Optional<SearchCondition> result = matcher.match("시니어");

        assertThat(result).isPresent();
        assertThat(result.get().experience()).isEqualTo("경력");
    }
}
