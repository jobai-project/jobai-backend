package com.jobai.backend.domain.privatejobposting.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 공고가 IT 직군인지 3단계 가중치로 판별한다.
 *
 * <p>회사마다 직무 표현이 제각각이고, 본 서비스는 개발뿐 아니라 데이터/AI·PM·디자인 등
 * IT 연관 직군까지 포함하므로 단순 키워드 매칭으로는 과탐/누락이 잦다. 그래서:
 * <ul>
 *   <li><b>STRONG</b> — 하나만 있어도 IT (예: 'Backend Engineer'). exclude 보다 우선.</li>
 *   <li><b>EXCLUDE</b> — strong 없이 이게 있으면 비IT (예: '재무', '부동산 개발').</li>
 *   <li><b>WEAK</b> — 단독이면 애매한 키워드. 2개 이상일 때만 IT.</li>
 * </ul>
 *
 * <p>"개발"은 IT 밖에서도 흔히 쓰여(부동산 개발·사업 개발·상권 개발) STRONG 에서 제외하고,
 * "개발자"·"백엔드개발" 같은 IT 맥락 복합어만 STRONG 에 둔다. 비IT '개발' 패턴은 EXCLUDE 로
 * 명시한다. 단 STRONG 이 우선이므로 'Backend Engineer - 부동산'(부동산팀 개발자)은 통과한다.
 *
 * <p>짧은 영어 약어(it·ai·ml·pm 등)는 단어경계로만 매칭해 'editor' 속 'it' 같은
 * 부분매칭 오탐을 막는다.
 */
public class ItJobFilter {

    // 하나만 있어도 IT 직군으로 볼 가능성이 높은 키워드
    private static final List<String> STRONG_KEYWORDS = List.of(
            // 개발 (단독 "개발"은 제외 — IT 맥락 복합어만)
            "개발자", "엔지니어",
            "backend", "frontend", "fullstack",
            "백엔드", "프론트", "풀스택",
            "server", "서버", "클라이언트",
            "software", "developer", "programmer", "프로그래머",
            "웹개발", "앱개발", "서버개발", "프론트엔드개발", "백엔드개발",
            "소프트웨어개발", "시스템개발",
            // 모바일
            "android", "ios", "모바일", "mobile",
            // 인프라
            "devops", "데브옵스", "sre",
            "인프라", "infra",
            "cloud", "클라우드",
            // 보안
            "security", "보안",
            // QA
            "qa", "테스터", "test engineer",
            // 데이터/AI
            "data engineer", "데이터 엔지니어",
            "machine learning", "머신러닝",
            "deep learning", "딥러닝",
            "ml engineer", "ai engineer",
            // 플랫폼
            "platform engineer", "플랫폼 엔지니어"
    );

    // 단독으로 있으면 오탐 가능성이 큰 키워드
    private static final List<String> WEAK_KEYWORDS = List.of(
            "it",
            "ai",
            "ml",
            "data", "데이터",
            "분석", "analytics",
            "pm", "po",
            "product manager", "product owner",
            "프로덕트", "서비스기획", "기획",
            "ui", "ux",
            "design", "designer", "디자인", "디자이너",
            "platform", "플랫폼",
            "service", "서비스"
    );

    // 비IT 직무로 볼 가능성이 높은 키워드
    private static final List<String> EXCLUDE_KEYWORDS = List.of(
            // 비IT '개발'/사업 맥락
            "부동산", "상권", "임대차",
            "사업개발", "사업 개발", "비즈니스 개발", "business develop",
            "파트너십", "partnership",
            // 경영/기획
            "경영", "전략", "사업기획",
            // 재무
            "재무", "회계", "세무", "자금",
            // 인사
            "인사", "hr", "채용", "리크루터", "recruiter", "recruitment",
            // 마케팅/브랜드
            "마케팅", "marketing", "브랜드", "brand", "브랜딩",
            // 콘텐츠
            "콘텐츠", "content", "editor", "에디터",
            // 영업/고객
            "영업", "sales", "세일즈", "고객", "cs", "customer", "광고주",
            // 운영/기타
            "운영", "operation", "operations",
            "법무", "총무", "구매", "물류", "md"
    );

    public static boolean isItJob(String title, String jobCategory) {
        String text = normalize(title) + " " + normalize(jobCategory);

        boolean hasStrong = STRONG_KEYWORDS.stream()
                .anyMatch(keyword -> matchKeyword(text, keyword));

        long weakCount = WEAK_KEYWORDS.stream()
                .filter(keyword -> matchKeyword(text, keyword))
                .count();

        boolean hasExclude = EXCLUDE_KEYWORDS.stream()
                .anyMatch(keyword -> matchKeyword(text, keyword));

        // 강한 IT 키워드가 있으면 우선 IT로 판단
        // 예: "Backend Engineer - 부동산"은 부동산이 있어도 개발 직무
        if (hasStrong) {
            return true;
        }

        // 강한 키워드 없이 제외 키워드가 있으면 비IT로 판단
        // 예: "부동산 개발 담당자"는 strong 없고 부동산(exclude) → 비IT
        if (hasExclude) {
            return false;
        }

        // 약한 키워드는 2개 이상 있을 때만 IT로 판단
        return weakCount >= 2;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase()
                .replace("-", "")                       // 하이픈 제거(붙임): back-end → backend
                .replaceAll("[\\[\\]()/_,·|]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean matchKeyword(String text, String keyword) {
        String normalizedKeyword = normalize(keyword);

        if (normalizedKeyword.isBlank()) {
            return false;
        }

        // 영어 키워드는 길이 무관하게 단어 단위로만 매칭한다.
        // (it/ai 같은 짧은 약어의 부분매칭 방지 + design/designer 처럼
        //  한 키워드가 다른 키워드의 부분문자열일 때 WEAK 중복 계산 방지)
        if (isEnglishWordKeyword(normalizedKeyword)) {
            return containsWord(text, normalizedKeyword);
        }

        return text.contains(normalizedKeyword);
    }

    private static boolean isEnglishWordKeyword(String keyword) {
        // 영문/숫자/공백으로만 이루어진 키워드 (단일어 'design' 및 복합어 'product manager')
        return keyword.matches("[a-z0-9 ]+");
    }

    private static boolean containsWord(String text, String word) {
        Pattern pattern = Pattern.compile(
                "(^|[^a-zA-Z0-9])" + Pattern.quote(word) + "([^a-zA-Z0-9]|$)",
                Pattern.CASE_INSENSITIVE
        );

        return pattern.matcher(text).find();
    }
}