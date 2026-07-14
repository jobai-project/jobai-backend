package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;

import java.util.List;

/**
 * 외부 IT 뉴스 소스에서 기사를 수집하는 인터페이스.
 * <p>구현체는 소스별로 하나씩 존재하며, 수집 실패 시 빈 리스트를 반환해야 한다.</p>
 */
public interface ArticleCollector {

    /** 이 수집기의 콘텐츠 소스 식별자. */
    ContentSource source();

    /**
     * 외부 소스에서 최신 기사를 수집한다.
     *
     * @return 수집된 기사 목록 (실패 시 빈 리스트)
     */
    List<RawArticle> collect();
}
