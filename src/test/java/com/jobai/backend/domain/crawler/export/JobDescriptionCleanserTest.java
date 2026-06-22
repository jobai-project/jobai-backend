package com.jobai.backend.domain.crawler.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HTML 본문 정제 단위 테스트. Spring 컨텍스트 불필요(순수 로직). */
class JobDescriptionCleanserTest {

    private final JobDescriptionCleanser cleanser = new JobDescriptionCleanser();

    @Test
    @DisplayName("평범한 HTML 태그를 제거하고 문단 줄바꿈은 보존한다")
    void cleansPlainHtml() {
        // 가장 흔한 형태: description 이 평범한 HTML(<p>...)
        String raw = "<p><strong>회사 소개</strong><br>"
                + "주식회사 쿠팡은 한국 이커머스를 선도합니다.</p>"
                + "<p><strong>주요 업무</strong></p>"
                + "<ul><li>대규모 트래픽 처리</li><li>MSA 백엔드 개발</li></ul>";

        String cleaned = cleanser.clean(raw);

        assertThat(cleaned).doesNotContain("<", ">", "<p>", "<br>");
        assertThat(cleaned).contains("회사 소개");
        assertThat(cleaned).contains("주식회사 쿠팡은 한국 이커머스를 선도합니다.");
        assertThat(cleaned).contains("MSA 백엔드 개발");
        assertThat(cleaned).contains("회사 소개\n");                 // 문단 줄바꿈 보존
        assertThat(cleaned.split("\n").length).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("엔티티 인코딩된 HTML(&lt;p&gt;)도 디코드해 태그를 제거한다")
    void cleansEntityEncodedHtml() {
        // 이중 인코딩된 형태: 수집 중 이스케이프돼 &lt;p&gt; 로 저장된 경우
        String raw = "&lt;p&gt;&lt;strong&gt;회사 소개&lt;/strong&gt;&lt;br&gt;"
                + "주식회사 쿠팡은 한국 이커머스를 선도합니다.&lt;/p&gt;"
                + "&lt;p&gt;&lt;strong&gt;주요 업무&lt;/strong&gt;&lt;/p&gt;"
                + "&lt;ul&gt;&lt;li&gt;대규모 트래픽 처리&lt;/li&gt;"
                + "&lt;li&gt;MSA 백엔드 개발&lt;/li&gt;&lt;/ul&gt;";

        String cleaned = cleanser.clean(raw);

        // 디코드 후 태그까지 제거돼야 한다(엔티티도, 복원된 태그도 남으면 안 됨)
        assertThat(cleaned).doesNotContain("<", ">", "&lt;", "&gt;", "<p>", "<br>");
        assertThat(cleaned).contains("회사 소개");
        assertThat(cleaned).contains("MSA 백엔드 개발");
        assertThat(cleaned).contains("회사 소개\n");
    }

    @Test
    @DisplayName("평문(태그 없음)은 그대로 보존한다")
    void keepsPlainText() {
        String raw = "백엔드 엔지니어를 찾습니다. 카카오페이의 결제 시스템을 함께 만들어요.";
        assertThat(cleanser.clean(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("null·빈값·공백은 빈 문자열로 처리한다")
    void handlesEmpty() {
        assertThat(cleanser.clean(null)).isEmpty();
        assertThat(cleanser.clean("")).isEmpty();
        assertThat(cleanser.clean("   ")).isEmpty();
    }

    @Test
    @DisplayName("연속된 빈 줄은 최대 2줄로 축소한다")
    void collapsesExcessiveBlankLines() {
        String raw = "<p>첫 문단</p><br><br><br><br><p>둘째 문단</p>";
        String cleaned = cleanser.clean(raw);
        assertThat(cleaned).doesNotContain("\n\n\n");
    }
}