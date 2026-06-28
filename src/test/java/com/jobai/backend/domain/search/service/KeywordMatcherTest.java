package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.search.service.KeywordMatcher.MatchResult;
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

    // --- 기존 match() 테스트 (하위 호환) ---

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

    @Test
    @DisplayName("애매한 자연어 입력은 match()에서 빈 결과")
    void matchNaturalLanguageReturnsEmpty() {
        assertThat(matcher.match("혼자 일하기 편한 직무")).isEmpty();
    }

    // --- 조사 제거 ---

    @Test
    @DisplayName("조사 제거: '서울에서' → '서울'")
    void stripParticles_서울에서() {
        assertThat(KeywordMatcher.stripParticles("서울에서")).isEqualTo("서울");
    }

    @Test
    @DisplayName("조사 제거: '백엔드를' → '백엔드'")
    void stripParticles_백엔드를() {
        assertThat(KeywordMatcher.stripParticles("백엔드를")).isEqualTo("백엔드");
    }

    @Test
    @DisplayName("조사 제거: '경력직으로' → '경력직'")
    void stripParticles_경력직으로() {
        assertThat(KeywordMatcher.stripParticles("경력직으로")).isEqualTo("경력직");
    }

    @Test
    @DisplayName("조사 제거: 조사가 없으면 그대로 반환")
    void stripParticles_조사없음() {
        assertThat(KeywordMatcher.stripParticles("백엔드")).isEqualTo("백엔드");
    }

    @Test
    @DisplayName("복합 조사 제거: '서울에서만' → '서울'")
    void stripParticles_서울에서만() {
        assertThat(KeywordMatcher.stripParticles("서울에서만")).isEqualTo("서울");
    }

    @Test
    @DisplayName("복합 조사 제거: '경력직으로만' → '경력직'")
    void stripParticles_경력직으로만() {
        assertThat(KeywordMatcher.stripParticles("경력직으로만")).isEqualTo("경력직");
    }

    // --- extract: 토큰 분류 ---

    @Test
    @DisplayName("단순 키워드만: '서울 백엔드' → 미매칭 토큰 없음")
    void extract_단순키워드() {
        MatchResult result = matcher.extract("서울 백엔드");

        assertThat(result.categories()).contains("백엔드");
        assertThat(result.location()).isEqualTo("서울");
        assertThat(result.hasUnmatchedTokens()).isFalse();
    }

    @Test
    @DisplayName("조사 포함: '서울에서 백엔드' → 조사 제거 후 매칭, 미매칭 없음")
    void extract_조사포함() {
        MatchResult result = matcher.extract("서울에서 백엔드");

        assertThat(result.categories()).contains("백엔드");
        assertThat(result.location()).isEqualTo("서울");
        assertThat(result.hasUnmatchedTokens()).isFalse();
    }

    @Test
    @DisplayName("자연어 쿼리: '서울에서 혼자 일하기 쉬운 백엔드' → 미매칭 토큰 있음")
    void extract_자연어() {
        MatchResult result = matcher.extract("서울에서 혼자 일하기 쉬운 백엔드");

        assertThat(result.categories()).contains("백엔드");
        assertThat(result.location()).isEqualTo("서울");
        assertThat(result.hasUnmatchedTokens()).isTrue();
        assertThat(result.unmatchedTokens()).contains("혼자", "일하기");
        // "쉬운"은 불용어로 필터링
        assertThat(result.unmatchedTokens()).doesNotContain("쉬운");
    }

    @Test
    @DisplayName("불용어 필터링: '백엔드 채용 모집' → 미매칭 없음")
    void extract_불용어() {
        MatchResult result = matcher.extract("백엔드 채용 모집");

        assertThat(result.categories()).contains("백엔드");
        assertThat(result.hasUnmatchedTokens()).isFalse();
    }

    @Test
    @DisplayName("순수 자연어: '혼자 일하기 편한 직무' → 카테고리 없음, 미매칭만")
    void extract_순수자연어() {
        MatchResult result = matcher.extract("혼자 일하기 편한 직무");

        assertThat(result.categories()).isEmpty();
        assertThat(result.location()).isNull();
        assertThat(result.experience()).isNull();
        assertThat(result.hasUnmatchedTokens()).isTrue();
        assertThat(result.unmatchedTokens()).contains("혼자", "일하기");
    }

    @Test
    @DisplayName("경력 조사 포함: '서울에서 경력직으로' → 지역+경력 추출, 미매칭 없음")
    void extract_경력조사() {
        MatchResult result = matcher.extract("서울에서 경력직으로");

        assertThat(result.location()).isEqualTo("서울");
        assertThat(result.experience()).isEqualTo("경력");
        assertThat(result.hasUnmatchedTokens()).isFalse();
    }

    @Test
    @DisplayName("빈 쿼리: null이나 공백은 빈 결과 반환")
    void extract_빈쿼리() {
        assertThat(matcher.extract(null).hasUnmatchedTokens()).isFalse();
        assertThat(matcher.extract("").hasUnmatchedTokens()).isFalse();
        assertThat(matcher.extract("   ").hasUnmatchedTokens()).isFalse();
    }
}
