package com.jobai.backend.domain.search.service;

import com.jobai.backend.domain.crawler.classify.JobCategory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class KeywordMatcher {

    private final Map<String, JobCategory> categorySynonyms = new HashMap<>();
    private final Map<String, String> locationKeywords = new HashMap<>();
    private final Map<String, String> experienceKeywords = new HashMap<>();

    public KeywordMatcher() {
        initCategorySynonyms();
        initLocationKeywords();
        initExperienceKeywords();
    }

    public Optional<SearchCondition> match(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        String normalized = query.toLowerCase().trim();
        String[] tokens = normalized.split("\\s+");

        Set<JobCategory> matchedCategories = new LinkedHashSet<>();
        String matchedLocation = null;
        String matchedExperience = null;
        List<String> titleKeywords = new ArrayList<>();

        for (String token : tokens) {
            JobCategory category = categorySynonyms.get(token);
            if (category != null) {
                matchedCategories.add(category);
                titleKeywords.add(token);
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
            }
        }

        if (matchedCategories.isEmpty() && matchedLocation == null && matchedExperience == null) {
            return Optional.empty();
        }

        List<String> categoryLabels = matchedCategories.stream()
                .map(JobCategory::getLabel)
                .toList();

        return Optional.of(new SearchCondition(
                categoryLabels,
                List.of(),
                titleKeywords,
                matchedLocation,
                matchedExperience,
                SearchCondition.METHOD_KEYWORD
        ));
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
        addSynonyms(JobCategory.DESIGNER,
                "디자이너", "designer", "ux", "ui", "디자인");
        addSynonyms(JobCategory.PM,
                "pm", "po", "기획", "planner", "프로덕트", "product", "서비스기획");
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
        for (String keyword : List.of("신입", "주니어", "junior", "인턴")) {
            experienceKeywords.put(keyword.toLowerCase(), "신입");
        }
        for (String keyword : List.of("경력", "시니어", "senior", "미드", "mid")) {
            experienceKeywords.put(keyword.toLowerCase(), "경력");
        }
    }
}
