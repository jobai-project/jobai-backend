package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;

import java.util.List;

public interface ArticleCollector {

    ContentSource source();

    List<RawArticle> collect();
}
