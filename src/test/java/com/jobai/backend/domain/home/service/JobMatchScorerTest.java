package com.jobai.backend.domain.home.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobMatchScorerTest {

    private final JobMatchScorer scorer = new JobMatchScorer();

    @Test
    @DisplayName("같은 source+id는 항상 같은 점수를 반환한다")
    void 동일공고는_결정론적점수() {
        int first = scorer.mockScore("PUBLIC", 42L);
        int second = scorer.mockScore("PUBLIC", 42L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("점수는 항상 60~99 범위 안에 있다")
    void 점수범위는_60에서_99() {
        for (long id = 0; id < 500; id++) {
            int publicScore = scorer.mockScore("PUBLIC", id);
            int privateScore = scorer.mockScore("PRIVATE", id);

            assertThat(publicScore).isBetween(60, 99);
            assertThat(privateScore).isBetween(60, 99);
        }
    }

    @Test
    @DisplayName("같은 id라도 source가 다르면 점수가 달라질 수 있다")
    void source가_다르면_점수구분() {
        boolean anyDifferent = false;
        for (long id = 0; id < 200; id++) {
            if (scorer.mockScore("PUBLIC", id) != scorer.mockScore("PRIVATE", id)) {
                anyDifferent = true;
                break;
            }
        }

        assertThat(anyDifferent).isTrue();
    }
}
