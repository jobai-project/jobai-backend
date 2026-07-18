package com.jobai.backend.domain.crawler.export;

import com.jobai.backend.domain.privatejobposting.service.ItJobFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT 직군 필터 단위 테스트.
 *
 * <p>3단계 가중치 로직을 검증한다:
 * <ul>
 *   <li>STRONG: 하나만 있어도 IT (exclude 보다 우선)</li>
 *   <li>EXCLUDE: strong 없이 exclude 있으면 비IT</li>
 *   <li>WEAK: 2개 이상일 때만 IT</li>
 * </ul>
 * isItJob 은 static 이라 인스턴스 없이 호출한다.
 */
class ItJobFilterTest {

    @Nested
    @DisplayName("STRONG 키워드 — 하나만 있어도 IT")
    class StrongKeywords {
        @Test
        void backendEngineer() {
            assertThat(ItJobFilter.isItJob("백엔드 엔지니어", "프로덕트")).isTrue();
        }

        @Test
        void frontendDeveloper() {
            assertThat(ItJobFilter.isItJob("프론트엔드 개발자", "Engineering")).isTrue();
        }

        @Test
        void mlEngineer() {
            assertThat(ItJobFilter.isItJob("머신러닝 엔지니어", "AI/ML")).isTrue();
        }

        @Test
        void iosDeveloper() {
            assertThat(ItJobFilter.isItJob("iOS 개발자", "Mobile")).isTrue();
        }

        @Test
        @DisplayName("STRONG 은 EXCLUDE 보다 우선: '마케팅 데이터 엔지니어'도 IT")
        void strongBeatsExclude() {
            assertThat(ItJobFilter.isItJob("마케팅 데이터 엔지니어", "Marketing")).isTrue();
        }
    }

    @Nested
    @DisplayName("EXCLUDE 키워드 — strong 없으면 비IT")
    class ExcludeKeywords {
        @Test
        void finance() {
            assertThat(ItJobFilter.isItJob("재무 회계 담당자", "Finance")).isFalse();
        }

        @Test
        void marketing() {
            assertThat(ItJobFilter.isItJob("브랜드 마케터", "Marketing")).isFalse();
        }

        @Test
        void hr() {
            assertThat(ItJobFilter.isItJob("인사 담당자", "People")).isFalse();
        }

        @Test
        void sales() {
            assertThat(ItJobFilter.isItJob("영업 매니저", "Sales")).isFalse();
        }

        @Test
        @DisplayName("EXCLUDE 는 WEAK 보다 우선: '경영 기획'은 기획(weak) 있어도 비IT")
        void excludeBeatsWeak() {
            assertThat(ItJobFilter.isItJob("경영 기획 담당자", "Strategy")).isFalse();
            assertThat(ItJobFilter.isItJob("재무 분석 담당자", "Finance")).isFalse();
        }

        @Test
        @DisplayName("비IT '개발' 맥락은 제외: 부동산/사업 개발 등 (실제 데이터 케이스)")
        void excludesNonItDevelopment() {
            // "개발" 단독은 STRONG 이 아니므로, 부동산/사업개발 등은 exclude 로 걸러진다
            assertThat(ItJobFilter.isItJob("부동산 개발 및 임대차 계약 담당자", null)).isFalse();
            assertThat(ItJobFilter.isItJob("쿠팡이츠 전략 파트너십 사업개발 전문가", null)).isFalse();
            assertThat(ItJobFilter.isItJob("물류 사업장 물류 장비 구매 및 개발 담당자", null)).isFalse();
            assertThat(ItJobFilter.isItJob("AD Sales Manager - 광고 (세일즈, Client)", null)).isFalse();
        }

        @Test
        @DisplayName("STRONG 은 EXCLUDE 보다 우선: 'Backend - 부동산'은 개발자라 통과")
        void strongBeatsExcludeForDevInNonItTeam() {
            // 부동산팀 소속 백엔드 개발자 → backend(strong) 우선 → IT
            assertThat(ItJobFilter.isItJob("Software Engineer, Backend - 부동산", null)).isTrue();
            assertThat(ItJobFilter.isItJob("Software Engineer, Backend - 광고", null)).isTrue();
        }
    }

    @Nested
    @DisplayName("WEAK 키워드 — 2개 이상이면 IT, 1개면 비IT")
    class WeakKeywords {
        @Test
        @DisplayName("weak 2개 이상 → IT")
        void twoOrMoreWeak() {
            assertThat(ItJobFilter.isItJob("프로덕트 매니저", "PM")).isTrue();          // 프로덕트 + pm
            assertThat(ItJobFilter.isItJob("데이터 분석가", "Data Analytics")).isTrue(); // 데이터+분석+data+analytics
            assertThat(ItJobFilter.isItJob("UX 디자이너", "Design")).isTrue();          // ux+디자이너+design
        }

        @Test
        @DisplayName("weak 1개 → 비IT (정보 부족, 애매)")
        void singleWeakIsNotEnough() {
            assertThat(ItJobFilter.isItJob("기획 담당자", "Planning")).isFalse();
        }

        @Test
        @DisplayName("부분문자열 중복 매칭 방지: 'Designer' 단독은 design+designer 로 이중계산되지 않아 비IT")
        void noSubstringDoubleCount() {
            // design 이 designer 의 부분문자열이지만, 단어경계 매칭이라 designer 1개만 카운트.
            // weak 1개 → 비IT (이중계산되면 2개로 잘못 IT 판정됨 → 회귀 방지)
            assertThat(ItJobFilter.isItJob("Designer", "")).isFalse();
            assertThat(ItJobFilter.isItJob("Product Designer", "")).isFalse();
            // 반면 ux/ui 가 붙으면 weak 2개 → IT (정상)
            assertThat(ItJobFilter.isItJob("UX Designer", "")).isTrue();
        }

        @Test
        @DisplayName("하이픈 표기 정규화: back-end·full-stack 도 STRONG 매칭")
        void normalizesHyphen() {
            assertThat(ItJobFilter.isItJob("back-end engineer", null)).isTrue();
            assertThat(ItJobFilter.isItJob("full-stack developer", null)).isTrue();
        }
    }

    @Nested
    @DisplayName("짧은 영어 약어 — 단어경계로만 매칭 (부분매칭 오탐 방지)")
    class ShortEnglishWordBoundary {
        @Test
        @DisplayName("'it' 은 단독 단어일 때만: editor·recruitment 안 잡힘")
        void itDoesNotMatchSubstring() {
            // editor, recruitment 에 'it' 이 들어있지만 부분매칭 안 됨 + 둘 다 exclude
            assertThat(ItJobFilter.isItJob("editor 콘텐츠", "Content")).isFalse();
            assertThat(ItJobFilter.isItJob("recruitment 리크루터", "HR")).isFalse();
        }

        @Test
        @DisplayName("'IT 개발팀' 처럼 단독 'it' 은 잡되, 그 자체론 weak 1개라 strong 동반 필요")
        void itAsStandaloneWord() {
            // "IT" 단독은 weak 1개 → 비IT. 하지만 "IT 개발자"는 개발(strong) → IT
            assertThat(ItJobFilter.isItJob("IT 개발자", null)).isTrue();   // strong:개발
        }
    }

    @Nested
    @DisplayName("null·빈 입력 안전 처리")
    class NullSafety {
        @Test
        void bothNull() {
            assertThat(ItJobFilter.isItJob(null, null)).isFalse();
        }

        @Test
        void emptyStrings() {
            assertThat(ItJobFilter.isItJob("", "")).isFalse();
        }
    }
}