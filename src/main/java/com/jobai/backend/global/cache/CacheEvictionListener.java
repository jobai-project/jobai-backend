package com.jobai.backend.global.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * {@link PipelineCacheEvictionEvent} 수신 시 모든 캐시를 클리어한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!classify & !export & !collect")
public class CacheEvictionListener {

    private final CacheManager cacheManager;

    @EventListener
    public void onPipelineComplete(PipelineCacheEvictionEvent event) {
        log.info("[CacheEviction] 파이프라인 완료 — 전체 캐시 무효화 시작");
        for (String cacheName : cacheManager.getCacheNames()) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
        log.info("[CacheEviction] 전체 캐시 무효화 완료: {}", cacheManager.getCacheNames());
    }
}
