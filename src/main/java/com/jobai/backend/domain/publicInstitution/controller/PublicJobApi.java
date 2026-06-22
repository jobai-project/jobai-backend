package com.jobai.backend.domain.publicInstitution.controller;

import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin - 공공기관 채용공고", description = "공공데이터 포털 연동 관리자 API (관리자 전용)")
@SecurityRequirement(name = "cookieAuth")
public interface PublicJobApi {

    @Operation(
            summary = "공공 채용공고 실시간 DB 동기화",
            description = """
                    기재부 공공데이터 API를 호출하여 현재 진행 중인 공공기관 채용공고를 DB에 저장합니다.

                    > ⚠️ **관리자 전용 API입니다.** 일반 사용자는 호출할 수 없습니다.

                    **동작 순서:**
                    1. 공공데이터 포털 채용공고 목록 API 호출 (최대 1000건)
                    2. 각 공고의 상세 정보를 병렬로 조회 (최대 5개 동시)
                    3. PDF 직무기술서 다운로드 및 파싱
                    4. DB에 신규 저장 또는 기존 데이터 업데이트

                    **처리 시간**: 공고 수에 따라 수십 초 소요될 수 있습니다.
                    네트워크 오류 발생 시 개별 공고 단위로 재시도(최대 3회)합니다.

                    **분류 코드**: `R600020` (공공기관 IT/개발 직군)
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "DB 동기화 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_401_001",
                                      "message": "인증이 필요합니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "접근 권한 없음 (관리자만 허용)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_403_001",
                                      "message": "접근 권한이 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<Void> triggerJobSync();
}
