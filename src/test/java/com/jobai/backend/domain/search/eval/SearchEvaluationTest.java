package com.jobai.backend.domain.search.eval;

import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.domain.search.service.JobSearchService;
import com.jobai.backend.domain.search.service.SearchReranker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.jobai.backend.domain.bloom.service.JobBloomFilterService;
import com.jobai.backend.domain.bloom.service.TechCardBloomFilterService;
import com.jobai.backend.global.storage.FileStorageService;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.function.Supplier;

import static com.jobai.backend.domain.search.eval.SearchMetrics.*;

/**
 * 검색 파이프라인 성능 비교 평가 테스트
 *
 * <p>KEYWORD / HYBRID / HYBRID+RERANK 3가지 모드의 검색 품질을 실제 DB 데이터 기반으로 수치화한다.
 *
 * <h3>실행 조건</h3>
 * <ul>
 *   <li>DB에 실제 공고 데이터가 존재해야 함 (test 프로필 DB 또는 profile 변경)</li>
 *   <li>HYBRID 모드: 임베딩 서버 실행 필요</li>
 *   <li>HYBRID+RERANK 모드: 임베딩 서버 + Cross-Encoder 서버 실행 필요</li>
 * </ul>
 *
 * <h3>실행 방법</h3>
 * <pre>
 * 1. 아래 @Disabled 제거
 * 2. 필요 시 @ActiveProfiles 값을 실제 데이터가 있는 프로필로 변경
 * 3. ./gradlew test --tests "com.jobai.backend.domain.search.eval.SearchEvaluationTest"
 * </pre>
 *
 * <h3>출력 예시</h3>
 * <pre>
 * 쿼리                           │ GT │ KEYWORD                  │ HYBRID                   │ HYBRID+RERANK
 * 서울 백엔드 신입                │ 12 │ 1.00 / 0.42 / 0.58       │ 1.00 / 0.50 / 0.67       │ 1.00 / 0.58 / 0.75
 * 재택근무 가능한 백엔드          │  5 │ 0.33 / 0.20 / 0.40       │ 0.50 / 0.40 / 0.60       │ 1.00 / 0.60 / 0.80
 * 평균 (10개 케이스)             │    │ 0.52 / 0.31 / 0.47       │ 0.63 / 0.42 / 0.59       │ 0.71 / 0.50 / 0.67
 * </pre>
 *
 * <h3>지표 해석</h3>
 * <ul>
 *   <li>MRR@10   : 상위 10위 내 첫 정답의 역수 순위. 1.0에 가까울수록 좋음</li>
 *   <li>Recall@5 : 상위 5위 내 정답 포함 비율</li>
 *   <li>Recall@10: 상위 10위 내 정답 포함 비율</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("evaluation")
// @Disabled("수동 실행 전용 — @Disabled 제거 후 실제 DB/AI 서비스 환경에서 실행")
@TestPropertySource(properties = {
        // test resources/application.yaml 의 jobai_test DB 설정을 실 데이터 DB로 덮어쓴다
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jobai",
        "spring.datasource.username=postgres",
        "spring.datasource.password=1234",
        // create-drop 으로 실 데이터가 삭제되지 않도록 DDL 비활성화
        "spring.jpa.hibernate.ddl-auto=none",
        // SSM 포트 포워딩: localPortNumber=18001
        "ai-server.base-url=http://localhost:18001",
        // LLM 쿼리 확장 활성화 (ANTHROPIC_API_KEY는 .env / 환경변수에서 로드)
        "search.query-expand.enabled=true",
        // Anthropic API 직접 호출 — base-url 명시, api-key 환경변수에서 주입
        "spring.ai.anthropic.base-url=https://api.anthropic.com",
        "spring.ai.anthropic.model=claude-haiku-4-5-20251001",
        "spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:dummy-key}"
})
class SearchEvaluationTest {

    // RedissonConfig 가 test 프로필에서 활성화되지만 Redis가 없으므로 mock으로 대체
    @MockitoBean
    private RedissonClient redissonClient;

    // Bloom filter 서비스들이 @PostConstruct 에서 redissonClient 를 사용하므로 함께 mock
    @MockitoBean
    private JobBloomFilterService jobBloomFilterService;

    @MockitoBean
    private TechCardBloomFilterService techCardBloomFilterService;

    // S3FileStorageService 가 app.s3.bucket 없이 생성되지 않으므로 mock으로 대체
    @MockitoBean
    private FileStorageService fileStorageService;

    @Autowired
    private JobSearchService jobSearchService;

    @Autowired
    private SearchReranker searchReranker;

    @PersistenceContext
    private EntityManager em;

    /** 평가 대상 상위 K개 */
    private static final int K = 10;

    /** search() 호출 시 요청할 결과 수 */
    private static final int FETCH_SIZE = K;

    /**
     * Ground truth 상한.
     * DB에 수천 건이 매칭돼도 Recall이 의미 있는 범위로 유지되도록 제한한다.
     */
    private static final int GT_CAP = 200;

    // -------------------------------------------------------------------------
    // 자료형
    // -------------------------------------------------------------------------

    record EvalEntry(String name, String query, Supplier<Set<String>> groundTruth) {}

    record ModeResult(double mrr, double recall5, double recall10) {
        static ModeResult zero() {
            return new ModeResult(0, 0, 0);
        }

        ModeResult plus(ModeResult o) {
            return new ModeResult(mrr + o.mrr, recall5 + o.recall5, recall10 + o.recall10);
        }

        ModeResult div(int n) {
            return new ModeResult(mrr / n, recall5 / n, recall10 / n);
        }
    }

    // -------------------------------------------------------------------------
    // 평가 케이스 정의
    // -------------------------------------------------------------------------

    /**
     * 10개 평가 케이스를 정의한다.
     *
     * <p>Ground truth는 DB JPQL로 자동 생성한다. 특정 조건을 만족하는 공고 ID를 정답 셋으로 삼아,
     * 각 모드에서 해당 공고가 상위에 나타나는지를 측정한다.
     *
     * <ul>
     *   <li>구조화 쿼리 5개 — 키워드 검색이 충분히 동작하는 기준점</li>
     *   <li>자연어 쿼리 2개 — 벡터/하이브리드 검색이 우위를 가져야 하는 케이스</li>
     *   <li>회사 특정 쿼리 2개 — 회사명 매칭 정확도</li>
     *   <li>공기업 쿼리 1개 — PublicJobPosting 대상</li>
     * </ul>
     */
    private List<EvalEntry> buildEvalCases() {
        return List.of(

            // ── 구조화 쿼리 ─────────────────────────────────────────────────────────────
            entry("백엔드 신입",
                  "백엔드 신입",
                  privateWhere("p.jobCategory = '백엔드' " +
                               // deriveExperienceLevels("신입") = ["신입", "무관", "미확인"]
                               "AND p.experienceLevel IN ('신입', '무관', '미확인') " +
                               "AND p.isClosed = false")),

            entry("프론트엔드 경력",
                  "프론트엔드 경력",
                  privateWhere("p.jobCategory = '프론트엔드' " +
                               "AND p.experienceLevel IN ('경력', '무관') " +
                               "AND p.isClosed = false")),

            entry("데이터 엔지니어",
                  "데이터 엔지니어",
                  privateWhere("p.jobCategory = '데이터엔지니어링' " +
                               "AND p.isClosed = false")),

            entry("모바일 개발자",
                  "모바일 앱 개발자",
                  privateWhere("p.jobCategory = '모바일' " +
                               "AND p.isClosed = false")),

            entry("DevOps 경력",
                  "DevOps 경력 채용",
                  privateWhere("p.jobCategory = 'DevOps/인프라' " +
                               "AND p.experienceLevel IN ('경력', '무관') " +
                               "AND p.isClosed = false")),

            // ── 자연어 쿼리 ─────────────────────────────────────────────────────────────
            // 키워드 검색은 "백엔드"만 인식하고 "재택근무"는 unmatchedToken으로 처리.
            // 벡터/하이브리드 검색이 description의 의미를 포착해 상위에 노출해야 한다.
            entry("재택근무 가능한 백엔드",
                  "재택근무 가능한 백엔드",
                  privateWhere("p.jobCategory LIKE '%백엔드%' " +
                               "AND (p.description LIKE '%재택%' " +
                               "  OR p.description LIKE '%원격%' " +
                               "  OR p.description LIKE '%리모트%') " +
                               "AND p.isClosed = false")),

            entry("신입 환영 스타트업",
                  "신입 환영하는 스타트업 개발자",
                  // "스타트업"이 unmatched → HYBRID Path B (experience=신입 구조 필터 있음)
                  // "개발자" 제거로 categories가 비어 모든 카테고리 신입 공고가 후보
                  privateWhere("p.experienceLevel IN ('신입', '무관', '미확인') " +
                               "AND p.isClosed = false")),

            // ── 회사 특정 쿼리 ──────────────────────────────────────────────────────────
            entry("카카오 백엔드",
                  "카카오 백엔드",
                  // JobSearchRepository: p.company = :company (정확 일치), company = "kakao"
                  privateWhere("p.company = 'kakao' " +
                               "AND p.jobCategory = '백엔드' " +
                               "AND p.isClosed = false")),

            entry("네이버 신입",
                  "네이버 신입 개발자",
                  // JobSearchRepository: p.company = :company (정확 일치), company = "naver"
                  privateWhere("p.company = 'naver' " +
                               "AND p.experienceLevel IN ('신입', '무관', '미확인') " +
                               "AND p.isClosed = false")),

            // ── 공기업 쿼리 ─────────────────────────────────────────────────────────────
            entry("공기업 IT 채용",
                  "공기업 IT 개발자 채용",
                  publicWhere("(p.isClosed IS NULL OR p.isClosed = false)")),

            // ── HYBRID Path B: exactRequired (기술 스택) ────────────────────────────
            // "kafka"는 KeywordMatcher에서 매칭 안 됨 → unmatched → QueryExpander가
            // EXACT_REQUIRED[KAFKA]로 분류 → Path B 또는 D 진입
            // 정답: description/title에 kafka 또는 apache kafka가 포함된 백엔드 공고
            entry("Kafka 백엔드",
                  "kafka 쓰는 백엔드",
                  privateWhere("p.jobCategory = '백엔드' " +
                               "AND (LOWER(p.description) LIKE '%kafka%' " +
                               "  OR LOWER(p.title) LIKE '%kafka%') " +
                               "AND p.isClosed = false")),

            // ── HYBRID Path C: 순수 자연어 (structural anchor 없음) ────────────────
            // "수평적", "자율"은 모두 unmatched → QueryExpander가 SEMANTIC_PREFERRED로 분류
            // Path C (순수 벡터) 진입
            // 정답: description에 수평/자율/flat 등 문화 키워드 포함 공고
            entry("수평적 자율 문화",
                  "수평적이고 자율적인 조직문화",
                  privateWhere("(LOWER(p.description) LIKE '%수평%' " +
                               "  OR LOWER(p.description) LIKE '%자율%' " +
                               "  OR LOWER(p.description) LIKE '%flat%') " +
                               "AND p.isClosed = false")),

            // ── HYBRID Path B: semantic + structural 조합 ────────────────────────
            // "python"은 EXACT_REQUIRED, "재택"은 SEMANTIC_REQUIRED → Path B
            // 정답: title/description에 python AND (재택 OR 원격) 포함 공고
            entry("파이썬 재택근무",
                  "파이썬 재택근무 백엔드",
                  privateWhere("p.jobCategory = '백엔드' " +
                               "AND (LOWER(p.description) LIKE '%python%' " +
                               "  OR LOWER(p.title) LIKE '%python%') " +
                               "AND (LOWER(p.description) LIKE '%재택%' " +
                               "  OR LOWER(p.description) LIKE '%원격%') " +
                               "AND p.isClosed = false"))
        );
    }

    // -------------------------------------------------------------------------
    // 메인 평가 실행
    // -------------------------------------------------------------------------

    @Test
    void compareSearchPipelines() {
        List<EvalEntry> cases = buildEvalCases();

        ModeResult sumKeyword = ModeResult.zero();
        ModeResult sumHybrid  = ModeResult.zero();
        ModeResult sumFull    = ModeResult.zero();
        int valid = 0;

        printHeader();

        for (EvalEntry e : cases) {
            Set<String> gt;
            try {
                gt = e.groundTruth().get();
            } catch (Exception ex) {
                System.out.printf("%-32s │ (ground truth 오류: %s)%n", e.name(), ex.getMessage());
                continue;
            }

            if (gt.isEmpty()) {
                System.out.printf("%-32s │  0 │ (DB에 해당 공고 없음 — 건너뜀)%n", e.name());
                continue;
            }

            valid++;

            ModeResult keyword = runMode(e.query(), gt, false, false, false);
            ModeResult hybrid  = runMode(e.query(), gt, true,  true,  false);
            ModeResult full    = runMode(e.query(), gt, true,  true,  true);

            sumKeyword = sumKeyword.plus(keyword);
            sumHybrid  = sumHybrid.plus(hybrid);
            sumFull    = sumFull.plus(full);

            printRow(e.name(), gt.size(), keyword, hybrid, full);
        }

        if (valid > 0) {
            printSummary(valid,
                    sumKeyword.div(valid),
                    sumHybrid.div(valid),
                    sumFull.div(valid));
        } else {
            System.out.println("평가 가능한 케이스가 없습니다. DB 데이터와 프로필 설정을 확인하세요.");
        }
    }

    // -------------------------------------------------------------------------
    // 모드 전환 및 검색 실행
    // -------------------------------------------------------------------------

    /**
     * 피처 플래그를 설정하고 검색을 수행한 뒤 지표를 계산한다.
     *
     * @param embedding true = 벡터 임베딩 검색 활성화
     * @param hybrid    true = RRF 하이브리드 검색 활성화 (embedding도 true여야 동작)
     * @param rerank    true = Cross-Encoder 리랭킹 활성화
     */
    private ModeResult runMode(String query, Set<String> gt,
                               boolean embedding, boolean hybrid, boolean rerank) {
        ReflectionTestUtils.setField(jobSearchService, "embeddingEnabled", embedding);
        ReflectionTestUtils.setField(jobSearchService, "hybridEnabled",    hybrid);
        ReflectionTestUtils.setField(searchReranker,  "enabled",           rerank);

        try {
            JobSearchResponse resp = jobSearchService.search(query, 0, FETCH_SIZE, null);
            List<String> ranked = resp.jobs().stream()
                    .map(j -> j.source() + ":" + j.id())
                    .toList();

            return new ModeResult(
                    mrr(ranked, gt, K),
                    recallAtK(ranked, gt, 5),
                    recallAtK(ranked, gt, K)
            );
        } catch (Exception e) {
            System.out.printf("  ⚠ 검색 실패 (embedding=%b, hybrid=%b, rerank=%b): %s%n",
                    embedding, hybrid, rerank, e.getMessage());
            return ModeResult.zero();
        }
    }

    // -------------------------------------------------------------------------
    // Ground truth 빌더
    // -------------------------------------------------------------------------

    /**
     * PrivateJobPosting에서 조건에 맞는 공고 ID를 "PRIVATE:{id}" 형식으로 수집한다.
     * GT_CAP 개를 초과하면 잘라낸다.
     */
    private Supplier<Set<String>> privateWhere(String whereClause) {
        return () -> {
            List<Long> ids = em.createQuery(
                            "SELECT p.id FROM PrivateJobPosting p WHERE " + whereClause
                            + " ORDER BY p.createdAt DESC", Long.class)
                    .setMaxResults(GT_CAP)
                    .getResultList();
            Set<String> keys = new LinkedHashSet<>();
            ids.forEach(id -> keys.add("PRIVATE:" + id));
            return keys;
        };
    }

    /**
     * PublicJobPosting에서 조건에 맞는 공고 ID를 "PUBLIC:{id}" 형식으로 수집한다.
     * GT_CAP 개를 초과하면 잘라낸다.
     */
    private Supplier<Set<String>> publicWhere(String whereClause) {
        return () -> {
            List<Long> ids = em.createQuery(
                            "SELECT p.id FROM PublicJobPosting p WHERE " + whereClause
                            + " ORDER BY p.createdAt DESC", Long.class)
                    .setMaxResults(GT_CAP)
                    .getResultList();
            Set<String> keys = new LinkedHashSet<>();
            ids.forEach(id -> keys.add("PUBLIC:" + id));
            return keys;
        };
    }

    private EvalEntry entry(String name, String query, Supplier<Set<String>> gt) {
        return new EvalEntry(name, query, gt);
    }

    // -------------------------------------------------------------------------
    // 출력 포맷
    // -------------------------------------------------------------------------

    private static final String H_LINE = "═".repeat(118);
    private static final String D_LINE = "─".repeat(118);

    private void printHeader() {
        System.out.println("\n" + H_LINE);
        System.out.println("  검색 파이프라인 성능 비교  │  지표: MRR@10 / Recall@5 / Recall@10  │  GT 상한: " + GT_CAP + "건");
        System.out.println(H_LINE);
        System.out.printf("%-32s │ GT │ %-26s │ %-26s │ %-26s%n",
                "쿼리", "KEYWORD", "HYBRID", "HYBRID+RERANK");
        System.out.println(D_LINE);
    }

    private void printRow(String name, int gtSize,
                          ModeResult keyword, ModeResult hybrid, ModeResult full) {
        System.out.printf("%-32s │%3d │ %s │ %s │ %s%n",
                name, gtSize, fmt(keyword), fmt(hybrid), fmt(full));
    }

    private void printSummary(int n,
                              ModeResult avgKeyword,
                              ModeResult avgHybrid,
                              ModeResult avgFull) {
        System.out.println(D_LINE);
        System.out.printf("%-32s │    │ %s │ %s │ %s%n",
                "평균 (" + n + "개 케이스)",
                fmt(avgKeyword), fmt(avgHybrid), fmt(avgFull));
        System.out.println(H_LINE + "\n");
    }

    /** "MRR / R@5 / R@10" 형식 (26자 고정폭) */
    private String fmt(ModeResult r) {
        return String.format("%.2f / %.2f / %.2f       ", r.mrr(), r.recall5(), r.recall10());
    }
}
