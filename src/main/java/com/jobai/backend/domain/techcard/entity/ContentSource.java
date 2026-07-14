package com.jobai.backend.domain.techcard.entity;

/** 테크 카드 콘텐츠의 출처를 나타내는 열거형. */
public enum ContentSource {
    /** HackerNews (news.ycombinator.com) */
    HACKERNEWS,
    /** GeekNews (news.hada.io) */
    GEEKNEWS,
    /** 내부 공고 데이터 기반 카드 (DB 미저장, 실시간 생성) */
    INTERNAL
}
