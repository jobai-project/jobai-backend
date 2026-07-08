package com.jobai.backend.domain.publicInstitution.controller;

import com.jobai.backend.domain.publicInstitution.service.JobDataSyncService;
import com.jobai.backend.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicJobSaveControllerTest {

    private MockMvc mockMvc;
    private JobDataSyncService jobDataSyncService;

    @BeforeEach
    void setUp() {
        jobDataSyncService = Mockito.mock(JobDataSyncService.class);
        PublicJobSaveController controller = new PublicJobSaveController(jobDataSyncService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionAdvice())
                .build();
    }

    @Test
    @DisplayName("동기화 성공 시 200을 반환한다")
    void triggerJobSync_success() throws Exception {
        mockMvc.perform(post("/api/v1/admin/job-sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200_001"));

        verify(jobDataSyncService).syncPublicJobOpenings();
    }

    @Test
    @DisplayName("동기화 중 예외가 발생하면 500을 반환한다")
    void triggerJobSync_failure_500() throws Exception {
        doThrow(new RuntimeException("data.go.kr 서비스키 인증 실패"))
                .when(jobDataSyncService).syncPublicJobOpenings();

        mockMvc.perform(post("/api/v1/admin/job-sync"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_500_001"));
    }
}
