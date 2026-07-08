package com.jobai.backend.domain.member.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.global.llm.AnthropicClient;
import com.jobai.backend.global.util.PdfParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 이력서 PDF에서 텍스트를 추출하고 기술스택을 파싱하는 서비스.
 * 1차 키워드 기반 추출(섹션 탐지 + 기술명 사전 매칭) 후,
 * 부족하면 2차 LLM 폴백으로 Anthropic API를 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParsingService {

    private final PdfParserUtil pdfParserUtil;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    private static final int MIN_KEYWORD_MATCH_COUNT = 3;

    private static final List<String> SECTION_KEYWORDS = List.of(
            "skills", "skill", "tech stack", "기술 스택", "기술스택",
            "보유 기술", "사용 기술", "기술역량", "기술 역량",
            "technical skills", "technologies", "tools"
    );

    private static final Set<String> TECH_DICTIONARY = Set.of(
            // 언어
            "java", "python", "javascript", "typescript", "c++", "c#",
            "go", "golang", "rust", "kotlin", "swift", "scala", "ruby",
            "php", "dart", "perl", "lua", "groovy",
            // 프론트엔드
            "react", "vue", "vue.js", "angular", "svelte", "next.js", "nextjs",
            "nuxt", "nuxt.js", "html", "css", "sass", "scss", "tailwind",
            "tailwindcss", "bootstrap", "jquery", "webpack", "vite",
            // 백엔드
            "spring", "spring boot", "springboot", "django", "flask", "fastapi",
            "express", "express.js", "nestjs", "nest.js", "node.js", "nodejs",
            "rails", "ruby on rails", "asp.net", "gin", "fiber",
            // 데이터베이스
            "mysql", "postgresql", "postgres", "mariadb", "oracle", "mssql",
            "mongodb", "redis", "elasticsearch", "cassandra", "dynamodb",
            "sqlite", "neo4j", "influxdb",
            // 클라우드 / 인프라
            "aws", "gcp", "azure", "docker", "kubernetes", "k8s",
            "terraform", "ansible", "jenkins", "github actions", "gitlab ci",
            "circleci", "nginx", "apache", "linux",
            // 데이터 / ML
            "kafka", "rabbitmq", "spark", "hadoop", "airflow",
            "tensorflow", "pytorch", "pandas", "numpy", "scikit-learn",
            // 기타
            "git", "github", "gitlab", "bitbucket", "jira", "confluence",
            "figma", "graphql", "rest", "restful", "grpc", "swagger",
            "junit", "jest", "cypress", "selenium"
    );

    /**
     * PDF 바이트에서 텍스트를 추출하고 기술스택을 파싱하여 Resumes 엔티티에 저장한다.
     */
    @Transactional
    public void parseAndUpdate(Resumes resume, byte[] pdfBytes) {
        String extractedText = pdfParserUtil.extractText(pdfBytes);
        if (extractedText.isBlank()) {
            log.warn("이력서 PDF 텍스트 추출 실패: resumeId={}", resume.getId());
            return;
        }

        List<String> skills = extractSkillsByKeyword(extractedText);
        if (skills.size() < MIN_KEYWORD_MATCH_COUNT) {
            log.info("키워드 기반 추출 부족({}개), LLM 폴백 시도: resumeId={}", skills.size(), resume.getId());
            skills = extractSkillsByLlm(extractedText);
        }

        String skillsJson = toJson(skills);
        resume.updateParsedData(extractedText, skillsJson);
        log.info("이력서 파싱 완료: resumeId={}, skills={}", resume.getId(), skillsJson);
    }

    /**
     * 1차: 키워드 기반 기술스택 추출.
     * 섹션 키워드 주변 텍스트에서 기술명 사전 매칭을 시도하고,
     * 섹션을 찾지 못하면 전체 텍스트에서 매칭한다.
     */
    List<String> extractSkillsByKeyword(String text) {
        String lowerText = text.toLowerCase();

        String sectionText = extractSkillSection(lowerText);
        if (sectionText != null) {
            Set<String> matched = matchTechNames(sectionText);
            if (matched.size() >= MIN_KEYWORD_MATCH_COUNT) {
                return new ArrayList<>(matched);
            }
        }

        Set<String> matched = matchTechNames(lowerText);
        return new ArrayList<>(matched);
    }

    private String extractSkillSection(String lowerText) {
        int earliestIdx = -1;
        for (String keyword : SECTION_KEYWORDS) {
            int idx = lowerText.indexOf(keyword);
            if (idx >= 0 && (earliestIdx < 0 || idx < earliestIdx)) {
                earliestIdx = idx;
            }
        }
        if (earliestIdx < 0) {
            return null;
        }

        int end = Math.min(lowerText.length(), earliestIdx + 500);
        int doubleNewline = lowerText.indexOf("\n\n", earliestIdx + 1);
        if (doubleNewline > 0 && doubleNewline < end) {
            end = doubleNewline;
        }
        return lowerText.substring(earliestIdx, end);
    }

    private Set<String> matchTechNames(String lowerText) {
        Set<String> matched = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String tech : TECH_DICTIONARY) {
            String regex = "(?i)\\b" + Pattern.quote(tech) + "\\b";
            if (Pattern.compile(regex).matcher(lowerText).find()) {
                matched.add(canonicalize(tech));
            }
        }
        return matched;
    }

    private String canonicalize(String tech) {
        return switch (tech.toLowerCase()) {
            case "java" -> "Java";
            case "python" -> "Python";
            case "javascript" -> "JavaScript";
            case "typescript" -> "TypeScript";
            case "c++" -> "C++";
            case "c#" -> "C#";
            case "go", "golang" -> "Go";
            case "rust" -> "Rust";
            case "kotlin" -> "Kotlin";
            case "swift" -> "Swift";
            case "scala" -> "Scala";
            case "ruby" -> "Ruby";
            case "php" -> "PHP";
            case "dart" -> "Dart";
            case "react" -> "React";
            case "vue", "vue.js" -> "Vue.js";
            case "angular" -> "Angular";
            case "svelte" -> "Svelte";
            case "next.js", "nextjs" -> "Next.js";
            case "nuxt", "nuxt.js" -> "Nuxt.js";
            case "html" -> "HTML";
            case "css" -> "CSS";
            case "sass" -> "Sass";
            case "scss" -> "SCSS";
            case "tailwind", "tailwindcss" -> "TailwindCSS";
            case "bootstrap" -> "Bootstrap";
            case "jquery" -> "jQuery";
            case "webpack" -> "Webpack";
            case "vite" -> "Vite";
            case "spring" -> "Spring";
            case "spring boot", "springboot" -> "Spring Boot";
            case "django" -> "Django";
            case "flask" -> "Flask";
            case "fastapi" -> "FastAPI";
            case "express", "express.js" -> "Express";
            case "nestjs", "nest.js" -> "NestJS";
            case "node.js", "nodejs" -> "Node.js";
            case "rails", "ruby on rails" -> "Ruby on Rails";
            case "asp.net" -> "ASP.NET";
            case "gin" -> "Gin";
            case "fiber" -> "Fiber";
            case "mysql" -> "MySQL";
            case "postgresql", "postgres" -> "PostgreSQL";
            case "mariadb" -> "MariaDB";
            case "oracle" -> "Oracle";
            case "mssql" -> "MSSQL";
            case "mongodb" -> "MongoDB";
            case "redis" -> "Redis";
            case "elasticsearch" -> "Elasticsearch";
            case "cassandra" -> "Cassandra";
            case "dynamodb" -> "DynamoDB";
            case "sqlite" -> "SQLite";
            case "neo4j" -> "Neo4j";
            case "aws" -> "AWS";
            case "gcp" -> "GCP";
            case "azure" -> "Azure";
            case "docker" -> "Docker";
            case "kubernetes", "k8s" -> "Kubernetes";
            case "terraform" -> "Terraform";
            case "ansible" -> "Ansible";
            case "jenkins" -> "Jenkins";
            case "github actions" -> "GitHub Actions";
            case "gitlab ci" -> "GitLab CI";
            case "nginx" -> "Nginx";
            case "linux" -> "Linux";
            case "kafka" -> "Kafka";
            case "rabbitmq" -> "RabbitMQ";
            case "spark" -> "Spark";
            case "hadoop" -> "Hadoop";
            case "airflow" -> "Airflow";
            case "tensorflow" -> "TensorFlow";
            case "pytorch" -> "PyTorch";
            case "pandas" -> "Pandas";
            case "numpy" -> "NumPy";
            case "scikit-learn" -> "scikit-learn";
            case "git" -> "Git";
            case "github" -> "GitHub";
            case "gitlab" -> "GitLab";
            case "bitbucket" -> "Bitbucket";
            case "jira" -> "Jira";
            case "confluence" -> "Confluence";
            case "figma" -> "Figma";
            case "graphql" -> "GraphQL";
            case "rest", "restful" -> "REST";
            case "grpc" -> "gRPC";
            case "swagger" -> "Swagger";
            case "junit" -> "JUnit";
            case "jest" -> "Jest";
            case "cypress" -> "Cypress";
            case "selenium" -> "Selenium";
            default -> tech;
        };
    }

    /**
     * 2차: LLM 기반 기술스택 추출 (키워드 추출 실패 시 폴백).
     */
    List<String> extractSkillsByLlm(String resumeText) {
        String truncated = resumeText.length() > 4000
                ? resumeText.substring(0, 4000) : resumeText;

        String system = "당신은 이력서에서 기술스택을 추출하는 전문가입니다. "
                + "결과는 반드시 JSON 배열 형식으로만 출력하세요. 다른 텍스트는 포함하지 마세요.";
        String userText = "다음 이력서 텍스트에서 기술스택(프로그래밍 언어, 프레임워크, 라이브러리, 도구)을 "
                + "JSON 배열로 추출해주세요. 결과만 출력하세요.\n"
                + "예: [\"Python\", \"Spring\", \"Docker\"]\n\n"
                + "이력서:\n" + truncated;

        try {
            String response = anthropicClient.complete(system, userText, 500);
            return parseLlmResponse(response);
        } catch (Exception e) {
            log.error("LLM 기술스택 추출 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * LLM 응답 문자열에서 JSON 배열 부분을 추출하여 기술스택 리스트로 파싱한다.
     *
     * @param response LLM이 반환한 텍스트 (JSON 배열 포함)
     * @return 파싱된 기술스택 리스트, 실패 시 빈 리스트
     */
    List<String> parseLlmResponse(String response) {
        if (response == null || response.isBlank()) return List.of();

        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("LLM 응답에서 JSON 배열을 찾을 수 없음: {}", response);
            return List.of();
        }

        String jsonArray = response.substring(start, end + 1);
        try {
            return objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("LLM 응답 JSON 파싱 실패: {}", jsonArray);
            return List.of();
        }
    }

    private String toJson(List<String> skills) {
        try {
            return objectMapper.writeValueAsString(skills);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
