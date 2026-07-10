package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.crawler.classify.JobCategory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 검색 쿼리에서 카테고리, 지역, 경력 키워드를 추출하고
 * 매칭되지 않은 토큰을 분류하는 컴포넌트.
 * 한글 조사 제거, 불용어 필터링을 수행한다.
 */
@Component
public class KeywordMatcher {

    private final Map<String, JobCategory> categorySynonyms = new HashMap<>();
    private final Map<String, String> locationKeywords = new HashMap<>();
    private final Map<String, String> experienceKeywords = new HashMap<>();
    private final Map<String, String> employmentTypeKeywords = new HashMap<>();

    private static final Set<String> STOPWORDS = Set.of(
            "채용", "모집", "구인", "공고", "직무", "직종", "분야", "관련", "포지션",
            "하는", "있는", "없는", "좋은", "쉬운", "편한",
            "취업", "일자리", "구직", "찾는", "싶어", "싶은", "원해",
            "나", "난", "저", "제", "하고", "해서", "합니다", "해요"
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
    }

    /**
     * 쿼리에서 카테고리/지역/경력을 추출하고, 매칭되지 않은 토큰도 함께 반환한다.
     */
    public MatchResult extract(String query) {
        if (query == null || query.isBlank()) {
            return MatchResult.empty();
        }

        String normalized = query.toLowerCase().trim();
        String[] tokens = normalized.split("\\s+");

        Set<JobCategory> matchedCategories = new LinkedHashSet<>();
        String matchedLocation = null;
        String matchedExperience = null;
        String matchedEmploymentType = null;
        List<String> unmatchedTokens = new ArrayList<>();

        // 인접 토큰 결합(bigram) 매칭을 위해 소비 여부 추적
        boolean[] consumed = new boolean[tokens.length];

        // 1차: bigram 매칭 (인접 2개 토큰을 합쳐서 phrase 매칭)
        for (int i = 0; i < tokens.length - 1; i++) {
            String combined = stripParticles(tokens[i]) + stripParticles(tokens[i + 1]);
            JobCategory category = categorySynonyms.get(combined);
            if (category != null) {
                matchedCategories.add(category);
                consumed[i] = true;
                consumed[i + 1] = true;
            }
        }

        // 2차: 단일 토큰 매칭 (bigram에서 소비되지 않은 토큰만)
        for (int i = 0; i < tokens.length; i++) {
            if (consumed[i]) continue;

            String rawToken = tokens[i];
            String stripped = stripParticles(rawToken);

            JobCategory category = findCategory(stripped, rawToken);
            if (category != null) {
                matchedCategories.add(category);
                continue;
            }

            String location = findLocation(stripped, rawToken);
            if (location != null) {
                matchedLocation = location;
                continue;
            }

            String experience = findExperience(stripped, rawToken);
            if (experience != null) {
                matchedExperience = experience;
                continue;
            }

            String empType = findEmploymentType(stripped, rawToken);
            if (empType != null) {
                matchedEmploymentType = empType;
                continue;
            }

            // 불용어 제거
            if (!STOPWORDS.contains(stripped) && !STOPWORDS.contains(rawToken)
                    && !stripped.isBlank()) {
                unmatchedTokens.add(rawToken);
            }
        }

        List<String> categoryLabels = matchedCategories.stream()
                .map(JobCategory::getLabel)
                .toList();

        List<String> expLevels = deriveExperienceLevels(matchedExperience);
        List<String> empTypes = deriveEmploymentTypes(matchedEmploymentType);

        return new MatchResult(categoryLabels, matchedLocation, matchedExperience,
                expLevels, empTypes, unmatchedTokens);
    }

    /**
     * 기존 호환용: 카테고리/지역/경력 중 하나라도 매칭되면 SearchCondition 반환.
     */
    public Optional<SearchCondition> match(String query) {
        MatchResult result = extract(query);

        if (result.categories().isEmpty() && result.location() == null
                && result.experience() == null && result.employmentTypes().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new SearchCondition(
                result.categories(),
                List.of(),
                List.of(),
                result.location(),
                result.experience(),
                result.experienceLevels(),
                result.employmentTypes(),
                SearchCondition.METHOD_KEYWORD
        ));
    }

    private JobCategory findCategory(String stripped, String raw) {
        JobCategory cat = categorySynonyms.get(stripped);
        if (cat != null) return cat;
        return categorySynonyms.get(raw);
    }

    private String findLocation(String stripped, String raw) {
        String loc = locationKeywords.get(stripped);
        if (loc != null) return loc;
        return locationKeywords.get(raw);
    }

    private String findExperience(String stripped, String raw) {
        String exp = experienceKeywords.get(stripped);
        if (exp != null) return exp;
        return experienceKeywords.get(raw);
    }

    private String findEmploymentType(String stripped, String raw) {
        String emp = employmentTypeKeywords.get(stripped);
        if (emp != null) return emp;
        return employmentTypeKeywords.get(raw);
    }

    private List<String> deriveExperienceLevels(String experience) {
        if (experience == null) return List.of();
        return switch (experience) {
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
                "개발", "developer", "엔지니어", "engineer", "프로그래머");
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

    /**
     * KeywordMatcher의 추출 결과.
     * unmatchedTokens가 비어있으면 구조화 검색만으로 충분, 있으면 벡터 검색 필요.
     */
    public record MatchResult(
            List<String> categories,
            String location,
            String experience,
            List<String> experienceLevels,
            List<String> employmentTypes,
            List<String> unmatchedTokens
    ) {
        public boolean hasUnmatchedTokens() {
            return unmatchedTokens != null && !unmatchedTokens.isEmpty();
        }

        public static MatchResult empty() {
            return new MatchResult(List.of(), null, null, List.of(), List.of(), List.of());
        }
    }
}
