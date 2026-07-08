package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.dto.ResumeResponseDTO;
import com.jobai.backend.domain.member.service.ResumeService;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import com.jobai.backend.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeControllerTest {

    private MockMvc mockMvc;
    private ResumeService resumeService;

    private static final String EMAIL = "test@jobai.com";

    @BeforeEach
    void setUp() {
        resumeService = Mockito.mock(ResumeService.class);
        ResumeController controller = new ResumeController(resumeService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // JwtAuthenticationFilter와 동일하게 principal 자리에 email 문자열을 넣는다.
    // standalone MockMvc에는 보안 필터 체인이 없으므로 SecurityMockMvcRequestPostProcessors.authentication()이
    // 세션에만 컨텍스트를 저장하고 SecurityContextHolder에는 반영되지 않는다. 따라서 직접 설정한다.
    private RequestPostProcessor asUser() {
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            EMAIL, null, Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))));
            return request;
        };
    }

    // -------------------------------------------------------
    // GET /api/v1/members/resumes — 목록 조회
    // -------------------------------------------------------

    @Test
    @DisplayName("목록 조회: 이력서가 있으면 200 + 리스트를 반환한다")
    void getResumes_success() throws Exception {
        ResumeResponseDTO.ResumeListDTO responseDto = ResumeResponseDTO.ResumeListDTO.builder()
                .resumes(List.of(
                        ResumeResponseDTO.ResumeItemDTO.builder()
                                .resumeId(1L)
                                .originalFilename("이력서.pdf")
                                .storedFileUrl("https://bucket.s3.ap-northeast-2.amazonaws.com/resumes/1/key.pdf")
                                .fileSize("1.2 MB")
                                .isActive(true)
                                .uploadedAt(LocalDate.of(2026, 6, 6))
                                .build()
                ))
                .build();
        when(resumeService.getResumes(EMAIL)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/members/resumes").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.resumes[0].resumeId").value(1))
                .andExpect(jsonPath("$.result.resumes[0].originalFilename").value("이력서.pdf"))
                .andExpect(jsonPath("$.result.resumes[0].isActive").value(true));
    }

    @Test
    @DisplayName("목록 조회: 이력서가 없으면 빈 리스트를 반환한다")
    void getResumes_empty() throws Exception {
        when(resumeService.getResumes(EMAIL))
                .thenReturn(ResumeResponseDTO.ResumeListDTO.builder().resumes(List.of()).build());

        mockMvc.perform(get("/api/v1/members/resumes").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resumes").isArray())
                .andExpect(jsonPath("$.result.resumes").isEmpty());
    }

    // -------------------------------------------------------
    // POST /api/v1/members/resumes — 업로드
    // -------------------------------------------------------

    @Test
    @DisplayName("업로드: 성공 시 201 + resumeId를 반환한다")
    void uploadResume_success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "이력서.pdf", "application/pdf", "%PDF-1.4 dummy".getBytes());
        when(resumeService.uploadResume(eq(EMAIL), any())).thenReturn(10L);

        mockMvc.perform(multipart("/api/v1/members/resumes").file(file).with(asUser()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.resumeId").value(10));
    }

    @Test
    @DisplayName("업로드: PDF가 아니면 400을 반환한다")
    void uploadResume_notPdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "이력서.txt", "text/plain", "그냥 텍스트 파일입니다".getBytes());
        when(resumeService.uploadResume(eq(EMAIL), any()))
                .thenThrow(new GeneralException(GeneralErrorCode.BAD_REQUEST, "PDF 파일만 업로드할 수 있습니다."));

        mockMvc.perform(multipart("/api/v1/members/resumes").file(file).with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }

    @Test
    @DisplayName("업로드: 존재하지 않는 회원이면 404를 반환한다")
    void uploadResume_memberNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "이력서.pdf", "application/pdf", "%PDF-1.4".getBytes());
        when(resumeService.uploadResume(eq(EMAIL), any()))
                .thenThrow(new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND));

        mockMvc.perform(multipart("/api/v1/members/resumes").file(file).with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404_002"));
    }

    // -------------------------------------------------------
    // PATCH /api/v1/members/resumes/{id}/activate — 활성화
    // -------------------------------------------------------

    @Test
    @DisplayName("활성화: 성공 시 200을 반환한다")
    void activateResume_success() throws Exception {
        mockMvc.perform(patch("/api/v1/members/resumes/1/activate").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result").value("이력서가 활성화되었습니다."));

        verify(resumeService).activateResume(EMAIL, 1L);
    }

    @Test
    @DisplayName("활성화: 존재하지 않는 이력서면 404를 반환한다")
    void activateResume_notFound() throws Exception {
        doThrow(new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 이력서를 찾을 수 없습니다."))
                .when(resumeService).activateResume(EMAIL, 999L);

        mockMvc.perform(patch("/api/v1/members/resumes/999/activate").with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404_001"));
    }

    @Test
    @DisplayName("활성화: 다른 회원의 이력서면 403을 반환한다")
    void activateResume_forbidden() throws Exception {
        doThrow(new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 이력서에 대한 권한이 없습니다."))
                .when(resumeService).activateResume(EMAIL, 5L);

        mockMvc.perform(patch("/api/v1/members/resumes/5/activate").with(asUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_001"));
    }

    // -------------------------------------------------------
    // DELETE /api/v1/members/resumes/{id} — 삭제
    // -------------------------------------------------------

    @Test
    @DisplayName("삭제: 성공 시 200을 반환한다")
    void deleteResume_success() throws Exception {
        mockMvc.perform(delete("/api/v1/members/resumes/1").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("이력서가 삭제되었습니다."));

        verify(resumeService).deleteResume(EMAIL, 1L);
    }

    @Test
    @DisplayName("삭제: 존재하지 않는 이력서면 404를 반환한다")
    void deleteResume_notFound() throws Exception {
        doThrow(new GeneralException(GeneralErrorCode.NOT_FOUND, "해당 이력서를 찾을 수 없습니다."))
                .when(resumeService).deleteResume(EMAIL, 999L);

        mockMvc.perform(delete("/api/v1/members/resumes/999").with(asUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404_001"));
    }

    @Test
    @DisplayName("삭제: 다른 회원의 이력서면 403을 반환한다")
    void deleteResume_forbidden() throws Exception {
        doThrow(new GeneralException(GeneralErrorCode.FORBIDDEN, "해당 이력서에 대한 권한이 없습니다."))
                .when(resumeService).deleteResume(EMAIL, 5L);

        mockMvc.perform(delete("/api/v1/members/resumes/5").with(asUser()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_403_001"));
    }
}
