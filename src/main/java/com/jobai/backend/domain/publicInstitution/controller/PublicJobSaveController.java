package com.jobai.backend.domain.publicInstitution.controller;

import com.jobai.backend.domain.publicInstitution.service.JobDataSyncService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class PublicJobSaveController implements PublicJobApi {

    private final JobDataSyncService jobDataSyncService;

    @PostMapping("/job-sync")
    public ApiResponse<Void> triggerJobSync() {

        // 공공데이터 API 호출 및 DB 동기화 로직 실행
        jobDataSyncService.syncPublicJobOpenings();

        // 공통 응답 스펙인 성공 코드를 반환 (Result는 Void이므로 비워둠)
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }
}
