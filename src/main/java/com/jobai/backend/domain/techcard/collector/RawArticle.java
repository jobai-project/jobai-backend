package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;

import java.time.LocalDateTime;

/**
 * 외부 소스에서 수집한 원시 기사 데이터.
 * <p>LLM 요약 전 단계의 중간 데이터로, Bloom Filter 중복 검사 및 요약 입력에 사용된다.</p>
 *
 * @param source      콘텐츠 소스 (HACKERNEWS, GEEKNEWS)
 * @param externalId  소스별 고유 식별자 (예: {@code hn:12345}, {@code gn:a1b2c3...})
 * @param title       원문 기사 제목
 * @param url         원문 URL
 * @param snippet     기사 요약 (없으면 빈 문자열)
 * @param publishedAt 원문 발행 시각
 */
public record RawArticle(
        ContentSource source,
        String externalId,
        String title,
        String url,
        String snippet,
        LocalDateTime publishedAt
) {}
