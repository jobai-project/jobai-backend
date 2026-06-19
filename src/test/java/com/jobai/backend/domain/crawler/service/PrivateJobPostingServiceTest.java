package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.engine.DeclarativeCrawler;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import com.jobai.backend.domain.crawler.spec.SpecLoader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PrivateJobPostingServiceTest {

    @Autowired DeclarativeCrawler crawler;
    @Autowired PrivateJobPostingService service;
    @Autowired PrivateJobPostingRepository repository;

    @Test
    void 수집_저장_재수집() throws Exception {
        CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/kakao.yaml");

        // 1차 수집 → 저장
        List<JobRecord> records = crawler.collect(spec);
        System.out.println("=== 1차 수집: " + records.size() + "건 ===");
        service.saveAll("kakao", records);

        long count1 = repository.count();
        System.out.println("=== DB 저장 후: " + count1 + "건 ===");

        // DB 내용 확인
        for (PrivateJobPosting p : repository.findAllByCompany("kakao")) {
            System.out.printf("  %s | %s | closed=%s | apply=%s%n",
                    p.getSourceJobId(), p.getTitle(), p.isClosed(), p.getApplyUrl());
        }

        // 2차 수집 → 저장 (같은 데이터 다시)
        List<JobRecord> records2 = crawler.collect(spec);
        service.saveAll("kakao", records2);

        long count2 = repository.count();
        System.out.println("=== 재수집 후: " + count2 + "건 (1차와 같아야 = 중복 안 됨) ===");
        // 수집_저장_재수집
        assertEquals(count1, count2, "재수집 시 중복 없이 동일 건수여야 함");
    }

    @Test
    void 마감_처리() throws Exception {
        CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/kakao.yaml");
        List<JobRecord> records = crawler.collect(spec);
        // 마감_처리
        assertTrue(records.size() > 1, "마감 테스트엔 2건 이상 필요");

        // 1차: 전체 저장 (7건)
        service.saveAll("kakao", records);

        // 2차: 일부러 마지막 1건을 뺀 목록으로 저장 (그 공고가 "사라진" 상황 흉내)
        List<JobRecord> fewer = records.subList(0, records.size() - 1);  // 6건만
        service.saveAll("kakao", fewer);

        // 빠진 그 공고가 is_closed=true 됐나 확인
        String droppedId = String.valueOf(records.get(records.size() - 1).getJobId());
        PrivateJobPosting dropped =
                repository.findByCompanyAndSourceJobId("kakao", droppedId).orElseThrow();
        System.out.println("빠진 공고 " + droppedId + " closed=" + dropped.isClosed());
        // → closed=true 면 마감 처리 작동!
        assertTrue(dropped.isClosed(), "누락 공고는 마감 처리되어야 함");
    }

    @Test
    void 사기업_3곳_저장() throws Exception {
        String[] companies = {"coupang", "daangn", "kakao"};

        for (String company : companies) {
            CrawlSpec spec = new SpecLoader().loadFromClasspath("specs/" + company + ".yaml");
            List<JobRecord> records = crawler.collect(spec);
            service.saveAll(company, records);
            System.out.println(company + ": " + records.size() + "건 저장");
        }

        System.out.println("=== 전체 DB: " + repository.count() + "건 ===");
    }
}
