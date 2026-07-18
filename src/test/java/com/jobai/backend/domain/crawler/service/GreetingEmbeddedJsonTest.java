package com.jobai.backend.domain.crawler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.service.JobRecord;
import com.jobai.backend.domain.crawler.service.DeclarativeCrawler;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import com.jobai.backend.domain.crawler.spec.ListSpec;
import com.jobai.backend.domain.crawler.spec.SelectSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * embedded_json (그리팅/카카오페이) 수집 검증.
 *
 * <p>Python collect_embedded_json 의 Java 포팅(collectEmbeddedJson)을 검증한다.
 * 네트워크를 타지 않도록 MockRestServiceServer 로 __NEXT_DATA__ 픽스처 HTML 을 응답시킨다.
 * 픽스처 구조는 Python ground-truth(실제 kakaopay 응답)와 동일:
 *   props.pageProps.dehydratedState.queries = [4개]  중
 *   queryKey == ["openings"] 인 항목의 state.data 가 공고 배열.
 *
 * <p>@SpringBootTest 를 쓰지 않고 new DeclarativeCrawler(...) 로 직접 생성한다(기존 패턴).
 */
class GreetingEmbeddedJsonTest {

    private static final String SUBDOMAIN_URL =
            "https://kakaopay.career.greetinghr.com/ko/main";

    private DeclarativeCrawler crawler;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        // MockRestServiceServer 가 이 builder 가 만드는 RestClient 의 요청을 가로챈다.
        server = MockRestServiceServer.bindTo(builder).build();
        crawler = new DeclarativeCrawler(builder, new ObjectMapper());
    }

    /** Python 과 동일한 spec(greeting_template 에서 __SUBDOMAIN__→kakaopay 치환한 형태). */
    private CrawlSpec kakaopaySpec() {
        CrawlSpec spec = new CrawlSpec();
        spec.setCompany("kakaopay");
        spec.setSourceType("embedded_json");

        ListSpec ls = new ListSpec();
        ls.setUrl(SUBDOMAIN_URL);
        ls.setScriptId("__NEXT_DATA__");

        // select: queries 배열에서 queryKey==["openings"] 인 항목의 state.data 를 꺼낸다.
        SelectSpec sel = new SelectSpec();
        sel.setArray("props.pageProps.dehydratedState.queries");
        sel.setMatchField("queryKey");
        sel.setMatchValue(List.of("openings"));   // Jackson 이 JSON 배열을 List 로 파싱 → List.equals 비교
        sel.setTake("state.data");
        ls.setSelect(sel);
        spec.setList(ls);

        // fields: openingId→job_id, title→title (검증 핵심만)
        spec.setFields(new java.util.LinkedHashMap<>(Map.of(
                "job_id", "openingId",
                "title", "title",
                "posted_at", "openDate",
                "deadline", "dueDate",
                "job_category",
                "openingJobPosition.openingJobPositions[].workspaceOccupation.occupation"
        )));

        // apply_url 템플릿
        spec.setApplyUrl(Map.of(
                "template", "https://kakaopay.career.greetinghr.com/ko/o/{job_id}"
        ));
        spec.setRequired(List.of("job_id", "title"));
        return spec;
    }

    @Test
    @DisplayName("그리팅 __NEXT_DATA__ 에서 openings 22건을 select 로 골라 매핑한다")
    void collectsEmbeddedOpenings() {
        server.expect(requestTo(SUBDOMAIN_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(nextDataHtml(22),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));
        List<JobRecord> records = crawler.collect(kakaopaySpec());

        server.verify();
        // Python n=22 와 일치해야 한다.
        assertThat(records).hasSize(22);

        JobRecord first = records.get(0);
        assertThat(first.getJobId()).isEqualTo(1001);        // openingId
        assertThat(first.getTitle()).isEqualTo("백엔드 엔지니어 1");
        // 중첩 배열 경로(openingJobPositions[].workspaceOccupation.occupation)가 풀려야 한다.
        // resolve 의 [] 는 리스트를 주므로 job_category 는 ["프로덕트"] 형태.
        assertThat(first.get("job_category")).isEqualTo(List.of("프로덕트"));
        // apply_url 템플릿 치환
        assertThat(first.getApplyUrl())
                .isEqualTo("https://kakaopay.career.greetinghr.com/ko/o/1001");
    }

    @Test
    @DisplayName("openings 쿼리가 없으면 빈 결과(다른 query 를 잘못 고르지 않는다)")
    void emptyWhenNoOpeningsQuery() {
        // openings 쿼리를 뺀 픽스처 → select 가 매칭 실패 → 빈 리스트
        server.expect(requestTo(SUBDOMAIN_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(nextDataHtmlNoOpenings(),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));

        List<JobRecord> records = crawler.collect(kakaopaySpec());

        server.verify();
        assertThat(records).isEmpty();
    }

    // ---------- 픽스처 ----------

    /** queries 4개(앞 3개는 openings 아님 + 마지막이 openings list n건) 인 __NEXT_DATA__ HTML. */
    private String nextDataHtml(int openingCount) {
        StringBuilder openings = new StringBuilder();
        for (int i = 0; i < openingCount; i++) {
            int id = 1001 + i;
            if (i > 0) openings.append(",");
            openings.append("""
                {"openingId":%d,"title":"백엔드 엔지니어 %d","openDate":"2026-05-01","dueDate":"2026-06-01","deadlineDDay":12,
                 "openingJobPosition":{"openingJobPositions":[
                    {"workspaceOccupation":{"occupation":"프로덕트"},
                     "workspacePlace":{"location":"카카오페이"},
                     "jobPositionCareer":{"careerType":"EXPERIENCED"},
                     "jobPositionEmployment":{"employmentType":"FULL_TIME_WORKER"}}]}}
                """.formatted(id, i + 1));
        }
        // queries: openings 아닌 3개 + openings 1개 (Python 실데이터와 동일 순서/형태)
        String json = """
            {"props":{"pageProps":{"dehydratedState":{"queries":[
              {"queryKey":["publicCareer","getCareerBootInfo"],"state":{"data":{"brandColor":"x"}}},
              {"queryKey":["publicCareer","getCareerPageDetail"],"state":{"data":{"version":1}}},
              {"queryKey":["employeeReferral","getOpeningFilters"],"state":{"data":{"success":true}}},
              {"queryKey":["openings"],"state":{"data":[%s]}}
            ]}}}}
            """.formatted(openings.toString());
        return wrapNextData(json);
    }

    /** openings 쿼리가 없는 픽스처. */
    private String nextDataHtmlNoOpenings() {
        String json = """
            {"props":{"pageProps":{"dehydratedState":{"queries":[
              {"queryKey":["publicCareer","getCareerBootInfo"],"state":{"data":{"brandColor":"x"}}},
              {"queryKey":["employeeReferral","getOpeningFilters"],"state":{"data":{"success":true}}}
            ]}}}}
            """;
        return wrapNextData(json);
    }

    private String wrapNextData(String json) {
        return "<!DOCTYPE html><html><body>"
                + "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                + json
                + "</script></body></html>";
    }

    @Test
    @DisplayName("select 없이 response_path 로 배열을 직접 꺼내 매핑한다")
    void collectsViaResponsePath() {
        // select 가 없는 spec → response_path 분기를 탄다
        CrawlSpec spec = new CrawlSpec();
        spec.setCompany("generic");
        spec.setSourceType("embedded_json");

        ListSpec ls = new ListSpec();
        ls.setUrl(SUBDOMAIN_URL);
        ls.setScriptId("__NEXT_DATA__");
        ls.setResponsePath("props.pageProps.jobs");   // 배열이 바로 이 경로에 있음
        // ls.setSelect(...) 안 함 → select == null → response_path 분기
        spec.setList(ls);

        spec.setFields(new java.util.LinkedHashMap<>(Map.of(
                "job_id", "id",
                "title", "title"
        )));
        spec.setRequired(List.of("job_id", "title"));

        server.expect(requestTo(SUBDOMAIN_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(responsePathHtml(),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));

        List<JobRecord> records = crawler.collect(spec);

        server.verify();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getJobId()).isEqualTo(7001);
        assertThat(records.get(0).getTitle()).isEqualTo("데이터 엔지니어");
    }

    /** select 없이 response_path 로 바로 닿는 위치에 공고 배열이 있는 __NEXT_DATA__. */
    private String responsePathHtml() {
        String json = """
        {"props":{"pageProps":{"jobs":[
          {"id":7001,"title":"데이터 엔지니어"},
          {"id":7002,"title":"플랫폼 엔지니어"}
        ]}}}
        """;
        return wrapNextData(json);
    }
}