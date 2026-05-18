package com.jobai.backend.domain.publicInstitution.controller;

import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "공공기관 채용공고 동기화", description = "공공데이터 연동 API")
public interface PublicJobApi {

    @Operation(summary = "공공 채용공고 실시간 DB 동기화", description = "기재부 공공데이터 API를 호출하여 DB에 저장합니다.")
    ApiResponse<Void> triggerJobSync();
}