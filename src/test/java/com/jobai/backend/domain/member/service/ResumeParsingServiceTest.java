package com.jobai.backend.domain.member.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.domain.member.entity.Resumes;
import com.jobai.backend.global.llm.AnthropicClient;
import com.jobai.backend.global.util.PdfParserUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResumeParsingServiceTest {

    private PdfParserUtil pdfParserUtil;
    private AnthropicClient anthropicClient;
    private ResumeParsingService service;

    @BeforeEach
    void setUp() {
        pdfParserUtil = Mockito.mock(PdfParserUtil.class);
        anthropicClient = Mockito.mock(AnthropicClient.class);
        service = new ResumeParsingService(pdfParserUtil, anthropicClient, new ObjectMapper());
    }

    // --- 키워드 기반 추출 ---

    @Nested
    @DisplayName("extractSkillsByKeyword")
    class ExtractSkillsByKeywordTest {

        @Test
        @DisplayName("기술스택 섹션이 있으면 해당 영역에서 추출한다")
        void 섹션_키워드_매칭() {
            String text = "학력\n서울대학교 컴퓨터공학과\n\n"
                    + "기술 스택\nJava, Spring Boot, Docker, AWS, MySQL\n\n"
                    + "경력\n네이버 백엔드 개발자";

            List<String> skills = service.extractSkillsByKeyword(text);

            assertThat(skills).contains("Java", "Spring Boot", "Docker", "AWS", "MySQL");
        }

        @Test
        @DisplayName("Skills 영문 섹션도 인식한다")
        void 영문_섹션_키워드() {
            String text = "Education\nSNU CS\n\nSkills\nPython, React, PostgreSQL, Git\n\nExperience\nSWE";

            List<String> skills = service.extractSkillsByKeyword(text);

            assertThat(skills).contains("Python", "React", "PostgreSQL", "Git");
        }

        @Test
        @DisplayName("섹션이 없으면 전체 텍스트에서 추출한다")
        void 섹션_없음_전체_추출() {
            String text = "저는 Java와 Spring Boot를 활용한 백엔드 개발 경험이 있으며, "
                    + "Docker와 AWS를 이용한 배포 경험도 보유하고 있습니다. "
                    + "MySQL과 Redis를 사용한 데이터 관리 경험이 있습니다.";

            List<String> skills = service.extractSkillsByKeyword(text);

            assertThat(skills).contains("Java", "Spring Boot", "Docker", "AWS", "MySQL", "Redis");
        }

        @Test
        @DisplayName("기술명이 3개 미만이면 부족한 결과를 반환한다")
        void 매칭_부족() {
            String text = "저는 성실한 인재입니다. Java를 조금 다룰 수 있습니다.";

            List<String> skills = service.extractSkillsByKeyword(text);

            assertThat(skills).hasSizeLessThan(3);
        }

        @Test
        @DisplayName("대소문자를 무시하고 매칭한다")
        void 대소문자_무시() {
            String text = "기술스택: JAVA, PYTHON, DOCKER, kubernetes, React";

            List<String> skills = service.extractSkillsByKeyword(text);

            assertThat(skills).contains("Java", "Python", "Docker", "Kubernetes", "React");
        }

        @Test
        @DisplayName("동의어는 표준 이름으로 변환된다")
        void 동의어_정규화() {
            String text = "기술스택: golang, k8s, nextjs, nodejs, springboot";

            List<String> skills = service.extractSkillsByKeyword(text);

            assertThat(skills).contains("Go", "Kubernetes", "Next.js", "Node.js", "Spring Boot");
        }

        @Test
        @DisplayName("빈 텍스트는 빈 리스트를 반환한다")
        void 빈_텍스트() {
            List<String> skills = service.extractSkillsByKeyword("");

            assertThat(skills).isEmpty();
        }
    }

    // --- LLM 응답 파싱 ---

    @Nested
    @DisplayName("parseLlmResponse")
    class ParseLlmResponseTest {

        @Test
        @DisplayName("정상 JSON 배열을 파싱한다")
        void 정상_파싱() {
            String response = "[\"Java\", \"Spring\", \"Docker\"]";

            List<String> result = service.parseLlmResponse(response);

            assertThat(result).containsExactly("Java", "Spring", "Docker");
        }

        @Test
        @DisplayName("JSON 배열 앞뒤에 텍스트가 있어도 파싱한다")
        void 앞뒤_텍스트_포함() {
            String response = "다음은 추출된 기술스택입니다:\n[\"Python\", \"React\", \"AWS\"]\n이상입니다.";

            List<String> result = service.parseLlmResponse(response);

            assertThat(result).containsExactly("Python", "React", "AWS");
        }

        @Test
        @DisplayName("null 응답은 빈 리스트를 반환한다")
        void null_응답() {
            assertThat(service.parseLlmResponse(null)).isEmpty();
        }

        @Test
        @DisplayName("빈 응답은 빈 리스트를 반환한다")
        void 빈_응답() {
            assertThat(service.parseLlmResponse("")).isEmpty();
        }

        @Test
        @DisplayName("대괄호가 없는 응답은 빈 리스트를 반환한다")
        void 대괄호_없음() {
            assertThat(service.parseLlmResponse("Java, Spring, Docker")).isEmpty();
        }

        @Test
        @DisplayName("잘못된 JSON은 빈 리스트를 반환한다")
        void 잘못된_JSON() {
            assertThat(service.parseLlmResponse("[Java, Spring]")).isEmpty();
        }
    }

    // --- parseAndUpdate 통합 ---

    @Nested
    @DisplayName("parseAndUpdate")
    class ParseAndUpdateTest {

        @Test
        @DisplayName("키워드 추출이 3개 이상이면 LLM을 호출하지 않는다")
        void 키워드_충분하면_LLM_미호출() {
            String text = "기술스택: Java, Spring Boot, Docker, AWS, MySQL";
            when(pdfParserUtil.extractText(any())).thenReturn(text);

            Resumes resume = Resumes.builder().id(1L).build();
            service.parseAndUpdate(resume, new byte[]{1});

            verify(anthropicClient, never()).complete(anyString(), anyString(), anyInt());
            assertThat(resume.getExtractedText()).isEqualTo(text);
            assertThat(resume.getResumeSkills()).contains("Java");
        }

        @Test
        @DisplayName("키워드 추출이 3개 미만이면 LLM 폴백을 호출한다")
        void 키워드_부족하면_LLM_호출() {
            String text = "저는 성실한 인재입니다.";
            when(pdfParserUtil.extractText(any())).thenReturn(text);
            when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                    .thenReturn("[\"Python\", \"Django\", \"PostgreSQL\"]");

            Resumes resume = Resumes.builder().id(1L).build();
            service.parseAndUpdate(resume, new byte[]{1});

            verify(anthropicClient).complete(anyString(), anyString(), anyInt());
            assertThat(resume.getResumeSkills()).contains("Python", "Django", "PostgreSQL");
        }

        @Test
        @DisplayName("PDF 텍스트 추출 실패 시 파싱을 중단한다")
        void PDF_추출_실패() {
            when(pdfParserUtil.extractText(any())).thenReturn("");

            Resumes resume = Resumes.builder().id(1L).build();
            service.parseAndUpdate(resume, new byte[]{1});

            assertThat(resume.getExtractedText()).isNull();
            assertThat(resume.getResumeSkills()).isNull();
        }

        @Test
        @DisplayName("LLM 호출 실패 시 키워드 추출 결과를 유지한다")
        void LLM_호출_실패_키워드_유지() {
            String text = "저는 Java를 사용합니다.";
            when(pdfParserUtil.extractText(any())).thenReturn(text);
            when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                    .thenThrow(new RuntimeException("API 오류"));

            Resumes resume = Resumes.builder().id(1L).build();
            service.parseAndUpdate(resume, new byte[]{1});

            assertThat(resume.getExtractedText()).isEqualTo(text);
            assertThat(resume.getResumeSkills()).contains("Java");
        }

        @Test
        @DisplayName("키워드 부족 + LLM 성공 시 결과를 병합한다")
        void 키워드_LLM_병합() {
            String text = "저는 Java와 Git을 사용합니다.";
            when(pdfParserUtil.extractText(any())).thenReturn(text);
            when(anthropicClient.complete(anyString(), anyString(), anyInt()))
                    .thenReturn("[\"Java\", \"Spring\", \"Docker\"]");

            Resumes resume = Resumes.builder().id(1L).build();
            service.parseAndUpdate(resume, new byte[]{1});

            assertThat(resume.getResumeSkills()).contains("Java", "Git", "Spring", "Docker");
        }
    }
}
