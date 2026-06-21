package com.jobai.backend.domain.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.jobai.backend.domain.crawler.engine.DeclarativeCrawler;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 사기업 공고 수집 오케스트레이터. 부품들을 하나의 흐름으로 잇는다.
 *
 * <pre>
 *   회사명 → 스펙 로드(resources/specs/{company}.yaml)
 *         → DeclarativeCrawler.collect (목록 + 상세)
 *         → saveAll (DB upsert, raw 그대로)
 *         → SaveResult (신규/변경/마감)
 * </pre>
 *
 * <p>정제(HTML 제거)·IT 필터는 여기서 하지 않는다. 원본 보존을 위해 DB 엔 수집한 raw 를 그대로 넣고,
 * 가공은 소비 시점(export)에 한다. 그래서 이 서비스는 "수집해서 저장"만 담당한다.
 *
 * <p>스펙은 클래스패스(resources/specs/)에서 읽는다. ObjectMapper(YAMLFactory)로 직접 파싱한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateJobCollectService {

    private final DeclarativeCrawler crawler;
    private final PrivateJobPostingService savingService;

    // YAML 파서는 상태 없는 도구라 주입받지 않고 내부에서 직접 생성한다.
    // (기본 JSON ObjectMapper 와 빈이 충돌하는 문제를 피함)
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private static final String SPEC_PATH_FORMAT = "specs/%s.yaml";

    /**
     * 한 회사를 수집해 DB 에 저장하고 결과를 돌려준다.
     *
     * @param company 회사 식별자(스펙 파일명과 일치, 예: doodlin → specs/doodlin.yaml)
     * @return 저장 결과(신규/변경/마감). 수집 0건이면 빈 결과.
     */
    public SaveResult collectAndSave(String company) {
        CrawlSpec spec = loadSpec(company);

        // 스펙의 company 와 인자가 다르면 스펙 쪽을 신뢰하되 경고(파일명-내용 불일치 조기 발견)
        String specCompany = spec.getCompany();
        if (specCompany == null || specCompany.isBlank()) {
            throw new IllegalStateException(company + " 스펙에 company 가 비어있습니다");
        }
        if (!specCompany.equals(company)) {
            throw new IllegalStateException(
                    "요청 회사(" + company + ")와 스펙 company(" + specCompany + ")가 일치하지 않습니다");
        }

        List<JobRecord> records = crawler.collect(spec);
        log.info("[{}] 수집 {}건", company, records.size());   // specCompany → company

        SaveResult result = savingService.saveAll(company, records);  // specCompany → company
        log.info("[{}] 저장 결과 {}", company, result);
        return result;
    }

    /** resources/specs/{company}.yaml 을 CrawlSpec 으로 로드. */
    private CrawlSpec loadSpec(String company) {
        String resource = String.format(SPEC_PATH_FORMAT, company);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("스펙 파일을 찾을 수 없습니다: " + resource);
            }
            return yamlMapper.readValue(in, CrawlSpec.class);
        } catch (IOException e) {
            throw new IllegalStateException(company + " 스펙 로드 실패: " + e.getMessage(), e);
        }
    }
}