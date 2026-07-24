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

import java.util.*;
import java.util.regex.Matcher;
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
            "redux", "zustand", "recoil", "mobx", "storybook", "electron",
            // 모바일
            "flutter", "react native", "swiftui",
            // 백엔드
            "spring", "spring boot", "springboot", "django", "flask", "fastapi",
            "express", "express.js", "nestjs", "nest.js", "node.js", "nodejs",
            "rails", "ruby on rails", "asp.net", "gin", "fiber",
            "spring security", "spring mvc", "spring batch", "spring cloud",
            // ORM / 빌드
            "jpa", "spring data jpa", "hibernate", "mybatis", "querydsl",
            "lombok", "gradle", "maven",
            // 데이터베이스
            "mysql", "postgresql", "postgres", "mariadb", "oracle", "mssql",
            "mongodb", "redis", "elasticsearch", "cassandra", "dynamodb",
            "sqlite", "neo4j", "influxdb", "tibero", "altibase",
            "snowflake", "bigquery",
            // 클라우드 / 인프라
            "aws", "gcp", "azure", "docker", "kubernetes", "k8s",
            "ec2", "eks", "cloudformation",
            "terraform", "ansible", "jenkins", "github actions", "gitlab ci",
            "circleci", "nginx", "apache", "linux",
            "openstack", "ceph", "glusterfs", "minio", "vmware", "kvm",
            "helm", "argocd", "podman",
            // CI/CD / 모니터링
            "sonarqube", "gitlab runner",
            "prometheus", "grafana", "pinpoint", "datadog",
            "kibana", "logstash", "elk", "opentelemetry", "jaeger",
            // 데이터 / ML
            "kafka", "rabbitmq", "spark", "hadoop", "airflow", "flink",
            "dbt", "trino", "databricks",
            "tensorflow", "pytorch", "keras", "pandas", "numpy", "scikit-learn",
            "xgboost", "lightgbm", "mlflow", "hugging face", "langchain",
            // 테스트 / 협업
            "git", "github", "gitlab", "bitbucket", "jira", "confluence",
            "figma", "graphql", "rest", "restful", "grpc", "swagger",
            "junit", "jest", "cypress", "selenium",
            "playwright", "pytest", "mockito", "testng", "vitest", "k6"
    );

    /**
     * PDF 바이트에서 텍스트를 추출하고 기술스택을 파싱하여 Resumes 엔티티에 저장한다.
     */
    public void parseAndUpdate(Resumes resume, byte[] pdfBytes) {
        String extractedText = pdfParserUtil.extractText(pdfBytes);
        if (extractedText.isBlank()) {
            log.warn("이력서 PDF 텍스트 추출 실패: resumeId={}", resume.getId());
            return;
        }

        List<String> keywordSkills = extractSkillsByKeyword(extractedText);
        List<String> skills = new ArrayList<>(keywordSkills);
        if (keywordSkills.size() < MIN_KEYWORD_MATCH_COUNT) {
            log.info("키워드 기반 추출 부족({}개), LLM 폴백 시도: resumeId={}", keywordSkills.size(), resume.getId());
            List<String> llmSkills = extractSkillsByLlm(extractedText);
            if (!llmSkills.isEmpty()) {
                Set<String> merged = new LinkedHashSet<>(keywordSkills);
                merged.addAll(llmSkills);
                skills = new ArrayList<>(merged);
            }
        }

        String skillsJson = toJson(skills);
        Integer experienceYears = extractExperienceYears(extractedText);
        resume.updateParsedData(extractedText, skillsJson, experienceYears);
        log.info("이력서 파싱 완료: resumeId={}, skills={}, experienceYears={}", resume.getId(), skillsJson, experienceYears);
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
            // 언어
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
            case "perl" -> "Perl";
            case "lua" -> "Lua";
            case "groovy" -> "Groovy";
            // 프론트엔드
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
            case "redux" -> "Redux";
            case "zustand" -> "Zustand";
            case "recoil" -> "Recoil";
            case "mobx" -> "MobX";
            case "storybook" -> "Storybook";
            case "electron" -> "Electron";
            // 모바일
            case "flutter" -> "Flutter";
            case "react native" -> "React Native";
            case "swiftui" -> "SwiftUI";
            // 백엔드
            case "spring" -> "Spring";
            case "spring boot", "springboot" -> "Spring Boot";
            case "spring security" -> "Spring Security";
            case "spring mvc" -> "Spring MVC";
            case "spring batch" -> "Spring Batch";
            case "spring cloud" -> "Spring Cloud";
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
            // ORM / 빌드
            case "jpa", "spring data jpa" -> "JPA";
            case "hibernate" -> "Hibernate";
            case "mybatis" -> "MyBatis";
            case "querydsl" -> "QueryDSL";
            case "lombok" -> "Lombok";
            case "gradle" -> "Gradle";
            case "maven" -> "Maven";
            // 데이터베이스
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
            case "influxdb" -> "InfluxDB";
            case "tibero" -> "Tibero";
            case "altibase" -> "Altibase";
            case "snowflake" -> "Snowflake";
            case "bigquery" -> "BigQuery";
            // 클라우드 / 인프라
            case "aws" -> "AWS";
            case "gcp" -> "GCP";
            case "azure" -> "Azure";
            case "docker" -> "Docker";
            case "kubernetes", "k8s" -> "Kubernetes";
            case "ec2" -> "EC2";
            case "eks" -> "EKS";
            case "cloudformation" -> "CloudFormation";
            case "terraform" -> "Terraform";
            case "ansible" -> "Ansible";
            case "jenkins" -> "Jenkins";
            case "github actions" -> "GitHub Actions";
            case "gitlab ci" -> "GitLab CI";
            case "circleci" -> "CircleCI";
            case "nginx" -> "Nginx";
            case "apache" -> "Apache";
            case "linux" -> "Linux";
            case "openstack" -> "OpenStack";
            case "ceph" -> "Ceph";
            case "glusterfs" -> "GlusterFS";
            case "minio" -> "MinIO";
            case "vmware" -> "VMware";
            case "kvm" -> "KVM";
            case "helm" -> "Helm";
            case "argocd" -> "ArgoCD";
            case "podman" -> "Podman";
            // CI/CD / 모니터링
            case "sonarqube" -> "SonarQube";
            case "gitlab runner" -> "GitLab Runner";
            case "prometheus" -> "Prometheus";
            case "grafana" -> "Grafana";
            case "pinpoint" -> "Pinpoint";
            case "datadog" -> "Datadog";
            case "kibana" -> "Kibana";
            case "logstash" -> "Logstash";
            case "elk" -> "ELK";
            case "opentelemetry" -> "OpenTelemetry";
            case "jaeger" -> "Jaeger";
            // 데이터 / ML
            case "kafka" -> "Kafka";
            case "rabbitmq" -> "RabbitMQ";
            case "spark" -> "Spark";
            case "hadoop" -> "Hadoop";
            case "airflow" -> "Airflow";
            case "flink" -> "Flink";
            case "dbt" -> "dbt";
            case "trino" -> "Trino";
            case "databricks" -> "Databricks";
            case "tensorflow" -> "TensorFlow";
            case "pytorch" -> "PyTorch";
            case "keras" -> "Keras";
            case "pandas" -> "Pandas";
            case "numpy" -> "NumPy";
            case "scikit-learn" -> "scikit-learn";
            case "xgboost" -> "XGBoost";
            case "lightgbm" -> "LightGBM";
            case "mlflow" -> "MLflow";
            case "hugging face" -> "Hugging Face";
            case "langchain" -> "LangChain";
            // 협업 / API
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
            // 테스트
            case "junit" -> "JUnit";
            case "jest" -> "Jest";
            case "cypress" -> "Cypress";
            case "selenium" -> "Selenium";
            case "playwright" -> "Playwright";
            case "pytest" -> "Pytest";
            case "mockito" -> "Mockito";
            case "testng" -> "TestNG";
            case "vitest" -> "Vitest";
            case "k6" -> "k6";
            default -> tech;
        };
    }

    /**
     * 이력서 텍스트에서 "경력 N년" 패턴으로 경력 연수를 추출한다.
     * 여러 패턴이 있을 경우 가장 큰 값을 사용한다.
     */
    Integer extractExperienceYears(String text) {
        // "경력 3년", "경력3년", "경력: 3년", "3년 경력" 등 커버
        Pattern pattern = Pattern.compile("경력\\s*:?\\s*(\\d+)\\s*년|(?<![\\d])(\\d+)\\s*년\\s*경력");
        Matcher matcher = pattern.matcher(text);
        int max = -1;
        while (matcher.find()) {
            String numStr = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int years = Integer.parseInt(numStr);
            if (years > max) {
                max = years;
            }
        }
        return max >= 0 ? max : null;
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
            log.warn("LLM 응답에서 JSON 배열을 찾을 수 없음: responseLength={}", response.length());
            return List.of();
        }

        String jsonArray = response.substring(start, end + 1);
        try {
            return objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("LLM 응답 JSON 파싱 실패: jsonLength={}", jsonArray.length());
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
