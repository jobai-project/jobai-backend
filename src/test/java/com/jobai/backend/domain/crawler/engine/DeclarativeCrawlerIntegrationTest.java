package com.jobai.backend.domain.crawler.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import com.jobai.backend.domain.crawler.spec.SpecLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("external")
class
DeclarativeCrawlerIntegrationTest {

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

    @Test
    void 네이버_목록_수집() throws Exception {
        DeclarativeCrawler crawler =
                new DeclarativeCrawler(RestClient.builder(), new ObjectMapper());

        CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/naver.yaml");
        List<JobRecord> records = crawler.collect(spec);

        assertFalse(records.isEmpty());
        JobRecord first = records.get(0);
        System.out.println("=== 네이버 ===");
        System.out.println("수집 건수: " + records.size());
        System.out.println("job_id: " + first.getJobId());
        System.out.println("title: " + first.getTitle());
        System.out.println("apply_url: " + first.getApplyUrl());
        System.out.println("description 있나: " + (first.getDescription() != null));

        assertNotNull(first.getJobId());
    }

    @Test
    void 토스_목록_수집() throws Exception {
        DeclarativeCrawler crawler =
                new DeclarativeCrawler(RestClient.builder(), new ObjectMapper());

        CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/toss.yaml");
        List<JobRecord> records = crawler.collect(spec);

        assertFalse(records.isEmpty());
        JobRecord first = records.get(0);
        System.out.println("=== 토스 ===");
        System.out.println("수집 건수: " + records.size());
        System.out.println("job_id: " + first.getJobId());
        System.out.println("title: " + first.getTitle());
        System.out.println("apply_url: " + first.getApplyUrl());
        System.out.println("description 있나: " + (first.getDescription() != null));
        System.out.println("extra: " + first.get("extra"));

        assertNotNull(first.getJobId());
    }

    @Test
    void 우아한형제들_목록_수집() throws Exception {
        DeclarativeCrawler crawler =
                new DeclarativeCrawler(RestClient.builder(), new ObjectMapper());

        CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/woowahan.yaml");
        List<JobRecord> records = crawler.collect(spec);

        assertFalse(records.isEmpty());
        JobRecord first = records.get(0);
        System.out.println("=== 우아한형제들 ===");
        System.out.println("수집 건수: " + records.size());
        System.out.println("job_id: " + first.getJobId());
        System.out.println("title: " + first.getTitle());
        System.out.println("apply_url: " + first.getApplyUrl());
        System.out.println("description 있나: " + (first.getDescription() != null));

        assertNotNull(first.getJobId());
    }
}
