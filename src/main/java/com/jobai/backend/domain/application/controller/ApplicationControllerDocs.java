package com.jobai.backend.domain.application.controller;

import com.jobai.backend.domain.application.dto.ApplicationRequestDTO;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Application", description = "입사 지원 현황 관리 API")
@SecurityRequirement(name = "cookieAuth")
public interface ApplicationControllerDocs {

    @Operation(
            summary = "입사 지원 현황 추가",
            description = """
                    로그인한 사용자의 입사 지원 현황을 추가합니다.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **status 필드 허용 값:**
                    | 값 | 의미 |
                    |---|---|
                    | `PLANNED` | 지원 예정 |
                    | `APPLIED` | 서류 지원 완료 |
                    | `DOCUMENT_PASSED` | 서류 합격 |
                    | `INTERVIEW_PASSED` | 면접 합격 |
                    | `FINAL_ACCEPTED` | 최종 합격 |
                    | `DOCUMENT_REJECTED` | 서류 불합격 |
                    | `INTERVIEW_REJECTED` | 면접 불합격 |

                    **날짜 형식**: `yyyy-MM-dd` (예: `2025-07-01`)

                    `appliedAt`, `interviewAt`, `memo`는 선택 입력 항목입니다.
                    """
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApplicationRequestDTO.CreateApplicationDTO.class),
                    examples = @ExampleObject(
                            name = "지원 현황 추가 예시",
                            value = """
                                    {
                                      "companyName": "카카오",
                                      "jobTitle": "백엔드 개발자",
                                      "status": "APPLIED",
                                      "appliedAt": "2025-06-15",
                                      "interviewAt": "2025-06-25",
                                      "memo": "코딩 테스트 통과 후 지원"
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "지원 현황 추가 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_201_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": "입사 지원 현황에 성공적으로 추가되었습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 (companyName, jobTitle, status 누락 또는 유효하지 않은 status 값)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_400_001",
                                      "message": "회사명은 필수 입력 항목입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자 (로그인 필요)",
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
                    responseCode = "404",
                    description = "존재하지 않는 회원",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "MEMBER_404_001",
                                      "message": "존재하지 않는 회원입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<String> createApplication(
            String email,
            ApplicationRequestDTO.CreateApplicationDTO request
    );
}
