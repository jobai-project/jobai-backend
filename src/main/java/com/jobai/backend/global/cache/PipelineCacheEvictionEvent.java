package com.jobai.backend.global.cache;

import org.springframework.context.ApplicationEvent;

/**
 * 일배치 파이프라인 완료 시 발행되는 캐시 무효화 이벤트.
 * <p>모든 캐시(L1 + L2)를 클리어하여 새로 수집된 공고 데이터를 즉시 반영한다.</p>
 */
public class PipelineCacheEvictionEvent extends ApplicationEvent {

    public PipelineCacheEvictionEvent(Object source) {
        super(source);
    }
}
