package com.jobai.backend.domain.crawler.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.spec.CrawlSpec;
import com.jobai.backend.domain.crawler.spec.DetailSpec;
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
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 상세(detail) 단계 수집 검증.
 *
 * <p>Python {@code collector/engine.py} 의 fetch_detail 을 Java 로 옮긴 것(DeclarativeCrawler.fetchDetail)을
 * 검증한다. 두 분기를 다룬다:
 * <ul>
 *   <li>JSON 상세 — 우아한형제들: 목록(data.list) + recruit_number 로 상세 URL 조립 → data.recruitContents</li>
 *   <li>embedded_json 상세 — 그리팅: 상세 HTML 의 __NEXT_DATA__ → queryKey 접두 일치(match_prefix) → openingsInfo.detail</li>
 * </ul>
 *
 * <p>네트워크를 타지 않도록 MockRestServiceServer 로 목록·상세 응답을 순서대로 돌려준다.
 * 픽스처 구조·기대값은 트랜스크립트의 Python ground-truth(woowahan n=2, greeting 상세)와 일치한다.
 *
 * <p>@SpringBootTest 를 쓰지 않고 new DeclarativeCrawler(...) 로 직접 생성한다(기존 패턴).
 */
class DetailStepTest {

    private DeclarativeCrawler crawler;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        crawler = new DeclarativeCrawler(builder, new ObjectMapper());
    }

    // ============================================================
    // 1) JSON 상세 — 우아한형제들
    // ============================================================

    private static final String WOOWA_LIST_URL = "https://career.woowahan.com/w1/recruits";

    /** 목록(JSON) + recruit_number 로 상세 URL 조립 + JSON 상세에서 본문 추출. */
    private CrawlSpec woowahanSpec() {
        CrawlSpec spec = new CrawlSpec();
        spec.setCompany("woowahan");
        spec.setSourceType("json");

        ListSpec ls = new ListSpec();
        ls.setUrl(WOOWA_LIST_URL);
        ls.setResponsePath("data.list");
        spec.setList(ls);

        // recruit_number 를 상세 URL 조립용으로 목록 fields 에 매핑
        spec.setFields(new java.util.LinkedHashMap<>(Map.of(
                "job_id", "recruitSeq",
                "title", "recruitName",
                "recruit_number", "recruitNumber"
        )));
        spec.setRequired(List.of("job_id", "title"));

        // 상세: url_template 으로 recruit_number 치환 → data.recruitContents 를 description 으로
        DetailSpec detail = new DetailSpec();
        detail.setEnabled(true);
        detail.setSourceType("json");
        detail.setUrlTemplate("https://career.woowahan.com/w1/recruits/{recruit_number}");
        detail.setFields(new java.util.LinkedHashMap<>(Map.of(
                "description", "data.recruitContents"
        )));
        spec.setDetail(detail);
        return spec;
    }

    @Test
    @DisplayName("우아한형제들: 목록 후 recruit_number 로 JSON 상세를 호출해 본문을 채운다 (n=2)")
    void woowahanJsonDetail() {
        // 1) 목록 응답
        server.expect(requestTo(WOOWA_LIST_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(woowahanListJson(), MediaType.APPLICATION_JSON));
        // 2) 상세 응답 2건 (recruit_number 순서대로)
        server.expect(requestTo("https://career.woowahan.com/w1/recruits/R2605045"))
                .andExpect(method(GET))
                .andRespond(withSuccess(woowahanDetailJson("<p>구분: 신입/경력</p><p>로봇 관제 업무</p>"),
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://career.woowahan.com/w1/recruits/R2605011"))
                .andExpect(method(GET))
                .andRespond(withSuccess(woowahanDetailJson("<p>마케팅 전략 수립</p>"),
                        MediaType.APPLICATION_JSON));

        List<JobRecord> records = crawler.collect(woowahanSpec());

        server.verify();
        assertThat(records).hasSize(2);

        JobRecord first = records.get(0);
        assertThat(first.get("recruit_number")).isEqualTo("R2605045");
        // JSON 상세에서 본문이 채워져야 한다.
        assertThat(first.getDescription()).isEqualTo("<p>구분: 신입/경력</p><p>로봇 관제 업무</p>");
        assertThat(records.get(1).getDescription()).isEqualTo("<p>마케팅 전략 수립</p>");
        // 상세 에러가 없어야 한다.
        assertThat(first.get("detail_error")).isNull();
    }

    private String woowahanListJson() {
        return """
            {"code":"2000","data":{"list":[
              {"recruitSeq":25030,"recruitNumber":"R2605045","recruitName":"자율주행 로봇 관제"},
              {"recruitSeq":25028,"recruitNumber":"R2605011","recruitName":"마케팅전략"}
            ]}}
            """;
    }

    private String woowahanDetailJson(String contents) {
        return """
            {"data":{"recruitContents":"%s"}}
            """.formatted(contents);
    }

    @Test
    @DisplayName("상세 호출이 실패하면 해당 공고에 detail_error 를 남기고 목록은 보존한다")
    void detailErrorIsolation() {
        // 목록은 정상
        server.expect(requestTo(WOOWA_LIST_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(woowahanListJson(), MediaType.APPLICATION_JSON));
        // 첫 공고 상세: 서버 500 → 예외 → detail_error 기록
        server.expect(requestTo("https://career.woowahan.com/w1/recruits/R2605045"))
                .andExpect(method(GET))
                .andRespond(withServerError());
        // 둘째 공고 상세: 깨진 JSON → 파싱 실패 → detail_error 기록
        server.expect(requestTo("https://career.woowahan.com/w1/recruits/R2605011"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{not valid json", MediaType.APPLICATION_JSON));

        List<JobRecord> records = crawler.collect(woowahanSpec());

        server.verify();
        // 상세가 둘 다 실패해도 목록 2건은 그대로 보존된다.
        assertThat(records).hasSize(2);

        JobRecord first = records.get(0);
        assertThat(first.get("detail_error")).isNotNull();   // 500 → 에러 기록
        assertThat(first.getDescription()).isNull();         // 본문은 못 채움
        // 목록 단계에서 채워진 필드는 살아있다.
        assertThat(first.getJobId()).isEqualTo(25030);
        assertThat(first.getTitle()).isEqualTo("자율주행 로봇 관제");

        JobRecord second = records.get(1);
        assertThat(second.get("detail_error")).isNotNull();  // 파싱 실패 → 에러 기록
        assertThat(second.getDescription()).isNull();
    }

    // ============================================================
    // 2) embedded_json 상세 — 그리팅 (match_prefix)
    // ============================================================

    private static final String GREETING_LIST_URL = "https://doodlin.career.greetinghr.com/ko/main";

    /** 목록(embedded_json) + 상세도 embedded_json(__NEXT_DATA__) + match_prefix 로 상세 항목 선택. */
    private CrawlSpec greetingSpec() {
        CrawlSpec spec = new CrawlSpec();
        spec.setCompany("doodlin");
        spec.setSourceType("embedded_json");

        ListSpec ls = new ListSpec();
        ls.setUrl(GREETING_LIST_URL);
        ls.setScriptId("__NEXT_DATA__");
        // 목록: queryKey == ["openings"] 정확 일치(match_value)
        SelectSpec listSel = new SelectSpec();
        listSel.setArray("props.pageProps.dehydratedState.queries");
        listSel.setMatchField("queryKey");
        listSel.setMatchValue(List.of("openings"));
        listSel.setTake("state.data");
        ls.setSelect(listSel);
        spec.setList(ls);

        spec.setFields(new java.util.LinkedHashMap<>(Map.of(
                "job_id", "openingId",
                "title", "title"
        )));
        spec.setRequired(List.of("job_id", "title"));
        spec.setApplyUrl(Map.of(
                "template", "https://doodlin.career.greetinghr.com/ko/o/{job_id}"
        ));

        // 상세: url_template + embedded_json + match_prefix
        DetailSpec detail = new DetailSpec();
        detail.setEnabled(true);
        detail.setSourceType("embedded_json");
        detail.setUrlTemplate("https://doodlin.career.greetinghr.com/ko/o/{job_id}");
        detail.setScriptId("__NEXT_DATA__");
        // 상세: queryKey 가 ["career","getOpeningById", ...] 로 시작(match_prefix)
        SelectSpec detailSel = new SelectSpec();
        detailSel.setArray("props.pageProps.dehydratedState.queries");
        detailSel.setMatchField("queryKey");
        detailSel.setMatchPrefix(List.of("career", "getOpeningById"));
        detailSel.setTake("state.data.data");
        detail.setSelect(detailSel);
        detail.setFields(new java.util.LinkedHashMap<>(Map.of(
                "description", "openingsInfo.detail"
        )));
        spec.setDetail(detail);
        return spec;
    }

    @Test
    @DisplayName("그리팅: 목록 후 상세 __NEXT_DATA__ 에서 match_prefix 로 본문을 채운다")
    void greetingEmbeddedJsonDetail() {
        // 1) 목록 응답: openings 2건
        server.expect(requestTo(GREETING_LIST_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(greetingListHtml(),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));
        // 2) 상세 응답 2건 (job_id 순서대로)
        server.expect(requestTo("https://doodlin.career.greetinghr.com/ko/o/1001"))
                .andExpect(method(GET))
                .andRespond(withSuccess(greetingDetailHtml(1001, "<p>백엔드 엔지니어 상세 본문</p>"),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));
        server.expect(requestTo("https://doodlin.career.greetinghr.com/ko/o/1002"))
                .andExpect(method(GET))
                .andRespond(withSuccess(greetingDetailHtml(1002, "<p>프론트엔드 엔지니어 상세 본문</p>"),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));

        List<JobRecord> records = crawler.collect(greetingSpec());

        server.verify();
        assertThat(records).hasSize(2);

        JobRecord first = records.get(0);
        assertThat(first.getJobId()).isEqualTo(1001);
        assertThat(first.getTitle()).isEqualTo("백엔드 엔지니어");
        // 상세 __NEXT_DATA__ 의 career/getOpeningById 항목에서 본문이 채워져야 한다.
        assertThat(first.getDescription()).isEqualTo("<p>백엔드 엔지니어 상세 본문</p>");
        assertThat(records.get(1).getDescription()).isEqualTo("<p>프론트엔드 엔지니어 상세 본문</p>");
        assertThat(first.get("detail_error")).isNull();
    }

    @Test
    @DisplayName("그리팅 상세: career/getOpeningById 프리픽스가 없으면 본문을 안 채운다(잘못 안 고름)")
    void greetingDetailNoPrefixMatch() {
        server.expect(requestTo(GREETING_LIST_URL))
                .andExpect(method(GET))
                .andRespond(withSuccess(greetingListHtml(),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));
        // 상세 응답에 career/getOpeningById 쿼리가 없음 → 본문 못 채움(에러 아님, description null)
        server.expect(requestTo("https://doodlin.career.greetinghr.com/ko/o/1001"))
                .andExpect(method(GET))
                .andRespond(withSuccess(greetingDetailHtmlNoMatch(),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));
        server.expect(requestTo("https://doodlin.career.greetinghr.com/ko/o/1002"))
                .andExpect(method(GET))
                .andRespond(withSuccess(greetingDetailHtmlNoMatch(),
                        new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8)));

        List<JobRecord> records = crawler.collect(greetingSpec());

        server.verify();
        // 목록은 보존되고, 본문만 안 채워진다.
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getDescription()).isNull();
        assertThat(records.get(0).get("detail_error")).isNull();
    }

    /** 목록용 __NEXT_DATA__: queries 중 ["openings"] 에 공고 2건. */
    private String greetingListHtml() {
        String json = """
            {"props":{"pageProps":{"dehydratedState":{"queries":[
              {"queryKey":["publicCareer","getCareerBootInfo"],"state":{"data":{"x":1}}},
              {"queryKey":["openings"],"state":{"data":[
                {"openingId":1001,"title":"백엔드 엔지니어"},
                {"openingId":1002,"title":"프론트엔드 엔지니어"}
              ]}}
            ]}}}}
            """;
        return wrapNextData(json);
    }

    /** 상세용 __NEXT_DATA__: queryKey ["career","getOpeningById", id] 항목의 state.data.data 에 본문. */
    private String greetingDetailHtml(int openingId, String detailBody) {
        String json = """
            {"props":{"pageProps":{"dehydratedState":{"queries":[
              {"queryKey":["publicCareer","getCareerBootInfo"],"state":{"data":{"x":1}}},
              {"queryKey":["career","getOpeningById",%d],"state":{"data":{"data":{
                "openingsInfo":{"detail":"%s"}
              }}}}
            ]}}}}
            """.formatted(openingId, detailBody);
        return wrapNextData(json);
    }

    /** 상세용 __NEXT_DATA__ 인데 career/getOpeningById 쿼리가 없는 경우. */
    private String greetingDetailHtmlNoMatch() {
        String json = """
            {"props":{"pageProps":{"dehydratedState":{"queries":[
              {"queryKey":["publicCareer","getCareerBootInfo"],"state":{"data":{"x":1}}}
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
}