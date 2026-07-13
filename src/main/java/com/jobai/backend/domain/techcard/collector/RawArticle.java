package com.jobai.backend.domain.techcard.collector;

import com.jobai.backend.domain.techcard.entity.ContentSource;

import java.time.LocalDateTime;

public record RawArticle(
        ContentSource source,
        String externalId,
        String title,
        String url,
        String snippet,
        LocalDateTime publishedAt
) {}
