package com.jobai.backend.domain.search.service;

import com.jobai.backend.global.enums.JobCategory;
import com.jobai.backend.domain.search.dto.SearchCondition;
import kr.co.shineware.nlp.komoran.constant.DEFAULT_MODEL;
import kr.co.shineware.nlp.komoran.core.Komoran;
import kr.co.shineware.nlp.komoran.model.KomoranResult;
import kr.co.shineware.util.common.model.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 검색 쿼리에서 카테고리, 지역, 경력 키워드를 추출하고
 * 매칭되지 않은 토큰을 분류하는 컴포넌트.
 * 한글 조사 제거, 불용어 필터링을 수행한다.
 */
@Slf4j
@Component
public class KeywordMatcher {

    private final Komoran komoran;
    private final Map<String, JobCategory> categorySynonyms = new HashMap<>();
    private final Map<String, String> locationKeywords = new HashMap<>();
    private final Map<String, String> experienceKeywords = new HashMap<>();
    private final Map<String, String> employmentTypeKeywords = new HashMap<>();
    private final Map<String, String> companyKeywords = new HashMap<>();
    private final Map<String, String> companyDisplayNames = new HashMap<>();
    private final Map<String, String> sourceTypeKeywords = new HashMap<>();

    /** Komoran POS 태그 중 키워드 매칭 대상이 되는 콘텐츠 형태소 */
    private static final Set<String> CONTENT_POS_TAGS = Set.of(
            "NNG",  // 일반명사: 백엔드, 서울, 신입
            "NNP",  // 고유명사: 판교
            "SL",   // 외국어: react, java, spring
            "SH",   // 한자어
            "SN"    // 숫자
    );

    /** 명사이지만 검색 키워드로 의미 없는 불용어 */
    private static final Set<String> NOUN_STOPWORDS = Set.of(
            "채용", "모집", "구인", "공고", "직무", "직종", "분야", "관련", "포지션",
            "취업", "일자리", "구직"
    );

    // 구두점 제거 패턴: 토큰 양끝의 쉼표, 마침표 등 제거
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile(
            "^[,\\.!?~;:]+|[,\\.!?~;:]+$"
    );

    // 한글 조사/접미사 패턴: 토큰 끝에 붙는 조사를 제거
    private static final Pattern PARTICLE_PATTERN = Pattern.compile(
            "(에서의|에서도|으로서|쪽으로|이지만|이라서|이라고|이었던|이라면|에서|으로|이나|에게|한테|까지|부터|마다|처럼|만큼|같은|이고|이랑|이면|이든|이야|이요|에|을|를|이|가|은|는|의|로|과|와|도|만|쪽)$"
    );

    public KeywordMatcher() {
        initCategorySynonyms();
        initLocationKeywords();
        initExperienceKeywords();
        initEmploymentTypeKeywords();
        initCompanyKeywords();
        initSourceTypeKeywords();
        this.komoran = initKomoran();
    }

    /**
     * 쿼리에서 카테고리/지역/경력을 추출하고, 매칭되지 않은 토큰도 함께 반환한다.
     * Komoran 형태소 분석기를 사용하여 조사/어미를 자동 분리한다.
     */
    public MatchResult extract(String query) {
        if (query == null || query.isBlank()) {
            return MatchResult.empty();
        }

        List<String> tokens = extractMorphemes(query);

        Set<JobCategory> matchedCategories = new LinkedHashSet<>();
        String matchedCompany = null;
        String matchedLocation = null;
        String matchedExperience = null;
        String matchedEmploymentType = null;
        String matchedSourceType = null;
        List<String> unmatchedTokens = new ArrayList<>();

        // 인접 토큰 결합(bigram) 매칭을 위해 소비 여부 추적
        boolean[] consumed = new boolean[tokens.size()];

        // 1차: bigram 매칭 (인접 2개 형태소를 합쳐서 phrase 매칭)
        for (int i = 0; i < tokens.size() - 1; i++) {
            String combined = tokens.get(i) + tokens.get(i + 1);
            JobCategory category = categorySynonyms.get(combined);
            if (category != null) {
                matchedCategories.add(category);
                consumed[i] = true;
                consumed[i + 1] = true;
                continue;
            }
            String sourceType = sourceTypeKeywords.get(combined);
            if (sourceType != null) {
                matchedSourceType = sourceType;
                consumed[i] = true;
                consumed[i + 1] = true;
            }
        }

        // 2차: 단일 토큰 매칭 (bigram에서 소비되지 않은 토큰만)
        for (int i = 0; i < tokens.size(); i++) {
            if (consumed[i]) continue;

            String token = tokens.get(i);

            JobCategory category = categorySynonyms.get(token);
            if (category != null) {
                matchedCategories.add(category);
                continue;
            }

            String company = companyKeywords.get(token);
            if (company != null) {
                matchedCompany = company;
                continue;
            }

            String location = locationKeywords.get(token);
            if (location != null) {
                matchedLocation = location;
                continue;
            }

            String experience = experienceKeywords.get(token);
            if (experience != null) {
                matchedExperience = experience;
                continue;
            }

            String empType = employmentTypeKeywords.get(token);
            if (empType != null) {
                matchedEmploymentType = empType;
                continue;
            }

            String sourceType = sourceTypeKeywords.get(token);
            if (sourceType != null) {
                matchedSourceType = sourceType;
                continue;
            }

            unmatchedTokens.add(token);
        }

        List<String> categoryLabels = matchedCategories.stream()
                .map(JobCategory::getLabel)
                .toList();

        List<String> expLevels = deriveExperienceLevels(matchedExperience);
        List<String> empTypes = deriveEmploymentTypes(matchedEmploymentType);

        return new MatchResult(categoryLabels, matchedCompany, matchedLocation,
                matchedExperience, expLevels, empTypes, matchedSourceType, unmatchedTokens);
    }

    /** Komoran으로 형태소 분석 후 콘텐츠 형태소(명사/외국어)만 추출한다. */
    private List<String> extractMorphemes(String text) {
        KomoranResult result = komoran.analyze(text.trim());
        List<String> tokens = new ArrayList<>();
        for (Pair<String, String> pair : result.getList()) {
            if (!CONTENT_POS_TAGS.contains(pair.getSecond())) continue;
            String morpheme = pair.getFirst().toLowerCase();
            if (morpheme.isBlank() || NOUN_STOPWORDS.contains(morpheme)) continue;

            // Komoran이 공백 포함 복합어를 하나의 토큰으로 반환하는 경우 분리
            if (morpheme.contains(" ")) {
                for (String sub : morpheme.split("\\s+")) {
                    if (!sub.isBlank() && !NOUN_STOPWORDS.contains(sub)) {
                        tokens.add(sub);
                    }
                }
            } else {
                tokens.add(morpheme);
            }
        }
        return tokens;
    }

    /**
     * 기존 호환용: 카테고리/지역/경력 중 하나라도 매칭되면 SearchCondition 반환.
     */
    public Optional<SearchCondition> match(String query) {
        MatchResult result = extract(query);

        if (result.categories().isEmpty() && result.company() == null
                && result.location() == null
                && result.experience() == null && result.employmentTypes().isEmpty()
                && result.sourceType() == null) {
            return Optional.empty();
        }

        return Optional.of(new SearchCondition(
                result.categories(),
                result.company(),
                result.location(),
                result.experience(),
                result.experienceLevels(),
                result.employmentTypes(),
                SearchCondition.METHOD_KEYWORD,
                result.sourceType(),
                List.of(), List.of()
        ));
    }

    private List<String> deriveExperienceLevels(String experience) {
        if (experience == null) return List.of();
        return switch (experience) {
            // '미확인' = 크롤링 시 경력 구분을 파악하지 못한 공고.
            // 신입 검색 결과에 포함하되 determineMatchType에서 SIMILAR 배지를 부여한다.
            case "신입" -> List.of("신입", "무관", "미확인");
            case "경력" -> List.of("경력", "무관", "미확인");
            default -> List.of();
        };
    }

    private List<String> deriveEmploymentTypes(String employmentType) {
        if (employmentType == null) return List.of();
        return List.of(employmentType);
    }

    static String stripParticles(String token) {
        // 1단계: 구두점 제거
        String current = PUNCTUATION_PATTERN.matcher(token).replaceAll("");
        if (current.isBlank()) return current;

        // 2단계: 조사/접미사 반복 제거
        while (!current.isBlank()) {
            String stripped = PARTICLE_PATTERN.matcher(current).replaceAll("");
            if (stripped.equals(current)) {
                return current;
            }
            current = stripped;
        }
        return current;
    }

    private void initCategorySynonyms() {
        addSynonyms(JobCategory.BACKEND,
                "백엔드", "backend", "서버", "server", "자바", "java", "스프링", "spring", "노드", "node");
        addSynonyms(JobCategory.FRONTEND,
                "프론트엔드", "frontend", "프론트", "react", "리액트", "vue", "뷰", "웹개발");
        addSynonyms(JobCategory.FULLSTACK,
                "풀스택", "fullstack", "full-stack");
        addSynonyms(JobCategory.MOBILE,
                "모바일", "mobile", "안드로이드", "android", "ios", "아이폰", "앱개발", "flutter", "플러터");
        addSynonyms(JobCategory.AI_ML,
                "ai", "ml", "머신러닝", "딥러닝", "인공지능");
        addSynonyms(JobCategory.DATA_ENGINEERING,
                "데이터", "data", "데이터엔지니어", "빅데이터");
        addSynonyms(JobCategory.DEVOPS,
                "devops", "데브옵스", "인프라", "infra", "sre", "클라우드", "cloud", "aws", "kubernetes");
        addSynonyms(JobCategory.SECURITY,
                "보안", "security", "정보보안");
        addSynonyms(JobCategory.QA,
                "qa", "테스트", "test", "품질");
        addSynonyms(JobCategory.EMBEDDED,
                "임베디드", "embedded", "펌웨어", "firmware", "iot");
        addSynonyms(JobCategory.ETC_DEV,
                "developer", "프로그래머");
        // "개발", "엔지니어", "engineer" 제거: 너무 generic하여 카테고리 고정보다
        // unmatched → 벡터 검색으로 처리하는 것이 적합
        addSynonyms(JobCategory.UX_RESEARCHER,
                "ux리서처", "ux리서치", "사용자리서치", "유저리서처", "유저리서치");
        addSynonyms(JobCategory.UXUI_DESIGNER,
                "ux", "ui", "ux/ui", "uxui", "ux디자이너", "ui디자이너");
        addSynonyms(JobCategory.PRODUCT_DESIGNER,
                "프로덕트디자이너", "제품디자이너", "productdesigner");
        addSynonyms(JobCategory.WEB_DESIGNER,
                "웹디자이너", "디자이너", "designer", "디자인", "그래픽디자이너");
        addSynonyms(JobCategory.PM_PO,
                "pm", "po", "프로덕트매니저", "프로덕트오너", "product", "productmanager");
        addSynonyms(JobCategory.SERVICE_PLANNER,
                "기획", "서비스기획", "planner", "기획자");
    }

    private void addSynonyms(JobCategory category, String... synonyms) {
        for (String synonym : synonyms) {
            categorySynonyms.put(synonym.toLowerCase(), category);
        }
    }

    /** 키워드 맵의 한글 단어를 사용자 사전에 등록하여 Komoran이 정확히 인식하도록 한다. */
    private Komoran initKomoran() {
        Komoran k = new Komoran(DEFAULT_MODEL.FULL);

        Set<String> dictWords = new LinkedHashSet<>();
        categorySynonyms.keySet().stream()
                .filter(w -> w.matches(".*[가-힣].*"))
                .forEach(dictWords::add);
        dictWords.addAll(locationKeywords.keySet());
        experienceKeywords.keySet().stream()
                .filter(w -> w.matches(".*[가-힣].*"))
                .forEach(dictWords::add);
        employmentTypeKeywords.keySet().stream()
                .filter(w -> w.matches(".*[가-힣].*"))
                .forEach(dictWords::add);
        companyKeywords.keySet().stream()
                .filter(w -> w.matches(".*[가-힣].*"))
                .forEach(dictWords::add);
        sourceTypeKeywords.keySet().stream()
                .filter(w -> w.matches(".*[가-힣].*"))
                .forEach(dictWords::add);

        // bigram 구성 단어 중 Komoran이 분리할 수 있는 단어 추가
        dictWords.addAll(List.of("리서처", "리서치", "매니저", "오너"));

        try {
            Path dictFile = Files.createTempFile("komoran-user-dict", ".tsv");
            List<String> lines = dictWords.stream()
                    .map(word -> word + "\tNNG")
                    .toList();
            Files.write(dictFile, lines);
            k.setUserDic(dictFile.toString());
            dictFile.toFile().deleteOnExit();
            log.info("Komoran 사용자 사전 등록: {}개 단어", dictWords.size());
        } catch (IOException e) {
            log.warn("Komoran 사용자 사전 생성 실패, 기본 사전으로 동작", e);
        }

        return k;
    }

    private void initLocationKeywords() {
        String[] locations = {
                "서울", "경기", "인천", "부산", "대구", "광주", "대전", "울산", "세종",
                "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주",
                "판교", "성남", "강남"
        };
        for (String loc : locations) {
            locationKeywords.put(loc, loc);
        }
    }

    private void initExperienceKeywords() {
        for (String keyword : List.of("신입", "주니어", "junior")) {
            experienceKeywords.put(keyword.toLowerCase(), "신입");
        }
        for (String keyword : List.of("경력", "시니어", "senior", "미드", "mid", "경력직")) {
            experienceKeywords.put(keyword.toLowerCase(), "경력");
        }
    }

    private void initEmploymentTypeKeywords() {
        for (String keyword : List.of("정규직")) {
            employmentTypeKeywords.put(keyword.toLowerCase(), "정규직");
        }
        for (String keyword : List.of("인턴", "인턴십")) {
            employmentTypeKeywords.put(keyword.toLowerCase(), "인턴");
        }
        for (String keyword : List.of("계약직", "기간제")) {
            employmentTypeKeywords.put(keyword.toLowerCase(), "계약직");
        }
    }

    private void initSourceTypeKeywords() {
        for (String keyword : List.of("공공기관", "공기업", "공공")) {
            sourceTypeKeywords.put(keyword, "PUBLIC");
        }
        for (String keyword : List.of("사기업", "민간")) {
            sourceTypeKeywords.put(keyword, "PRIVATE");
        }
    }

    private void initCompanyKeywords() {
        addCompany("kakao", "카카오");
        addCompany("kakaopay", "카카오페이");
        addCompany("naver", "네이버");
        addCompany("coupang", "쿠팡");
        addCompany("woowahan", "우아한형제들");
        addCompany("toss", "토스");
        addCompany("daangn", "당근마켓");
        addCompany("socar", "쏘카");
        addCompany("kurly", "컬리");
        addCompany("zigbang", "직방");
        addCompany("wrtn", "뤼튼");
        addCompany("nhn", "NHN");
        addCompany("upstage", "업스테이지");
        addCompany("doodlin", "두들린");
        addCompany("gccompany", "여기어때");
    }

    private void addCompany(String companyId, String displayName) {
        companyKeywords.put(companyId.toLowerCase(), companyId);
        companyKeywords.put(displayName.toLowerCase(), companyId);
        companyDisplayNames.put(companyId, displayName);
    }

    /** 영문 company ID를 한글 표시명으로 변환한다. 매핑이 없으면 ID를 그대로 반환한다. */
    public String getDisplayName(String companyId) {
        if (companyId == null) return null;
        return companyDisplayNames.getOrDefault(companyId, companyId);
    }

    /**
     * KeywordMatcher의 추출 결과.
     * unmatchedTokens가 비어있으면 구조화 검색만으로 충분, 있으면 벡터 검색 필요.
     */
    public record MatchResult(
            List<String> categories,
            String company,
            String location,
            String experience,
            List<String> experienceLevels,
            List<String> employmentTypes,
            String sourceType,
            List<String> unmatchedTokens
    ) {
        public boolean hasUnmatchedTokens() {
            return unmatchedTokens != null && !unmatchedTokens.isEmpty();
        }

        public static MatchResult empty() {
            return new MatchResult(List.of(), null, null, null, List.of(), List.of(), null, List.of());
        }
    }
}
