package com.jobai.backend.domain.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.service.JobRecord;
import com.jobai.backend.domain.crawler.service.DeclarativeCrawler;
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
        // 오프셋 페이지네이션 동작 확인: 네이버 공고가 1페이지(기본 배치) 이상
        assertTrue(records.size() > 1, "페이지네이션으로 2건 이상 수집되어야 합니다");

        JobRecord first = records.get(0);
        System.out.println("=== 네이버 ===");
        System.out.println("수집 건수: " + records.size());
        System.out.println("job_id: " + first.getJobId());
        System.out.println("title: " + first.getTitle());
        System.out.println("apply_url: " + first.getApplyUrl());
        System.out.println("description 있나: " + (first.getDescription() != null));

        assertNotNull(first.getJobId());
        // 상세 보강(detail html) 확인: description이 채워져야 함
        assertNotNull(first.getDescription(), "상세 보강으로 description이 채워져야 합니다");
        assertFalse(first.getDescription().toString().isBlank(), "description이 비어 있으면 안 됩니다");
        // detail_error가 없어야 정상
        assertNull(first.get("detail_error"), "상세 호출에 에러가 없어야 합니다");
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
        // metadata_extraction 검증: metadata에서만 오는 필드가 채워져야 함
        assertNotNull(first.getDescription(), "metadata_extraction으로 description이 채워져야 합니다");
        assertNotNull(first.get("job_category"), "metadata_extraction으로 job_category가 채워져야 합니다");
        assertNotNull(first.get("subsidiary"), "metadata_extraction으로 subsidiary가 채워져야 합니다");
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
