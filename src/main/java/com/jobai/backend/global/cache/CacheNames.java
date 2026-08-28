package com.jobai.backend.global.cache;

/** {@code @Cacheable}에서 사용하는 캐시 이름 상수. */
public final class CacheNames {

    private CacheNames() {
    }

    public static final String LATEST_JOBS = "latestJobs";
    public static final String RECOMMENDED_JOBS = "recommendedJobs";
    public static final String JOB_SEARCH = "jobSearch";
    public static final String NEW_JOBS_CARD = "newJobsCard";
    public static final String QUERY_EXPANSION = "queryExpansion";
    public static final String QUERY_EMBEDDING = "queryEmbedding";
}
