package com.jobai.backend.domain.crawler.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import com.jobai.backend.domain.crawler.spec.SpecLoader;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeclarativeCrawlerIntegrationTest {

    @Test
    void 쿠팡_목록_수집() throws Exception {
        DeclarativeCrawler crawler =
                new DeclarativeCrawler(RestClient.builder(), new ObjectMapper());

        CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/coupang.yaml");
        List<JobRecord> records = crawler.collect(spec);

        assertFalse(records.isEmpty());
        JobRecord first = records.get(0);
        System.out.println("수집 건수: " + records.size());
        System.out.println("job_id: " + first.getJobId());
        System.out.println("title: " + first.getTitle());
        System.out.println("apply_url: " + first.getApplyUrl());
        System.out.println("description 있나: " + (first.getDescription() != null));

        assertNotNull(first.getJobId());
    }

    @Test
    void 당근_목록_수집() throws Exception {
        DeclarativeCrawler crawler =
                new DeclarativeCrawler(RestClient.builder(), new ObjectMapper());

        CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/daangn.yaml");  // 파일명 맞게
        List<JobRecord> records = crawler.collect(spec);

        assertFalse(records.isEmpty());
        JobRecord first = records.get(0);
        System.out.println("수집 건수: " + records.size());
        System.out.println("job_id: " + first.getJobId());
        System.out.println("title: " + first.getTitle());
        System.out.println("apply_url: " + first.getApplyUrl());
        System.out.println("description 있나: " + (first.getDescription() != null));

        assertNotNull(first.getJobId());
    }
}
