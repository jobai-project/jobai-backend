package com.jobai.backend.domain.home.service;

import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * TODO: AI 담당자가 실제 매칭점수 계산 로직으로 교체 예정.
 *
 * <p>현재는 공고 source+id 기반 결정론적 해시로 60~99 사이 점수를 생성한다.
 * "더 불러오기" 페이지네이션 중 같은 공고가 매 요청마다 다른 점수로 계산되면
 * 정렬 순서가 흔들려 중복/누락이 생기므로 반드시 결정론적이어야 한다.
 */
@Component
public class JobMatchScorer {

    private static final int MIN_SCORE = 60;
    private static final int SCORE_RANGE = 40;

    public int mockScore(String source, Long id) {
        return Math.floorMod(Objects.hash(source, id), SCORE_RANGE) + MIN_SCORE;
    }
}
