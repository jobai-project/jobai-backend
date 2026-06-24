package com.jobai.backend.domain.application.controller;

import com.jobai.backend.domain.application.dto.ApplicationRequestDTO;
import com.jobai.backend.domain.application.dto.ApplicationResponseDTO;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    ApiResponse<ApplicationResponseDTO.CreateResultDTO> createApplication(
            String email,
            ApplicationRequestDTO.CreateApplicationDTO request
    );

    @Operation(
            summary = "입사 지원 현황 목록 조회",
            description = """
                    로그인한 사용자의 모든 입사 지원 현황 목록을 반환합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **반환 데이터 (`applications` 배열 내 각 항목):**
                    | 필드 | 타입 | 설명 |
                    |---|---|---|
                    | `applicationId` | Long | 지원 현황 고유 ID (수정·삭제 시 사용) |
                    | `companyName` | String | 회사명 |
                    | `jobTitle` | String | 지원 직무명 |
                    | `status` | String | 지원 단계 (영문 enum 값) |
                    | `statusLabel` | String | 지원 단계 한글 텍스트 (UI에 바로 표시 가능) |
                    | `appliedAt` | String | 지원일 (`yyyy-MM-dd`, null 가능) |
                    | `interviewAt` | String | 면접일 (`yyyy-MM-dd`, null 가능) |
                    | `memo` | String | 메모 (null 가능) |

                    지원 현황이 없으면 `applications`는 빈 배열(`[]`)로 반환됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "applications": [
                                          {
                                            "applicationId": 1,
                                            "companyName": "카카오",
                                            "jobTitle": "백엔드 개발자",
                                            "status": "APPLIED",
                                            "statusLabel": "서류 지원 완료",
                                            "appliedAt": "2025-06-15",
                                            "interviewAt": "2025-06-25",
                                            "memo": "코딩 테스트 통과 후 지원"
                                          },
                                          {
                                            "applicationId": 2,
                                            "companyName": "네이버",
                                            "jobTitle": "프론트엔드 개발자",
                                            "status": "PLANNED",
                                            "statusLabel": "지원 예정",
                                            "appliedAt": null,
                                            "interviewAt": null,
                                            "memo": null
                                          }
                                        ]
                                      }
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
    ApiResponse<ApplicationResponseDTO.ApplicationListDTO> getApplications(String email);

    @Operation(
            summary = "입사 지원 현황 수정",
            description = """
                    특정 지원 현황 항목을 수정합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **부분 수정 지원**: 변경하고 싶은 필드만 보내면 됩니다. 보내지 않은 필드(`null`)는 기존 값을 유지합니다.

                    **applicationId**: URL 경로에 포함합니다. (예: `PATCH /api/v1/applications/1`)

                    **본인 소유 확인**: 다른 사용자의 지원 현황은 수정할 수 없습니다. (403 반환)

                    **status 허용 값:**
                    | 값 | 의미 |
                    |---|---|
                    | `PLANNED` | 지원 예정 |
                    | `APPLIED` | 서류 지원 완료 |
                    | `DOCUMENT_PASSED` | 서류 합격 |
                    | `INTERVIEW_PASSED` | 면접 합격 |
                    | `FINAL_ACCEPTED` | 최종 합격 |
                    | `DOCUMENT_REJECTED` | 서류 불합격 |
                    | `INTERVIEW_REJECTED` | 면접 불합격 |
                    """
    )
    @Parameter(name = "applicationId", description = "수정할 지원 현황의 고유 ID", required = true, example = "1")
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApplicationRequestDTO.UpdateApplicationDTO.class),
                    examples = {
                            @ExampleObject(
                                    name = "상태만 변경",
                                    value = """
                                            {
                                              "status": "DOCUMENT_PASSED"
                                            }
                                            """
                            ),
                            @ExampleObject(
                                    name = "여러 필드 동시 변경",
                                    value = """
                                            {
                                              "companyName": "라인",
                                              "jobTitle": "iOS 개발자",
                                              "status": "INTERVIEW_PASSED",
                                              "interviewAt": "2025-07-10",
                                              "memo": "1차 면접 통과"
                                            }
                                            """
                            )
                    }
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": "입사 지원 현황이 성공적으로 수정되었습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 (회사명·직무명 50자 초과 등)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_400_001",
                                      "message": "회사명은 50글자 이하여야 합니다.",
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
                    responseCode = "403",
                    description = "본인 소유가 아닌 지원 현황 수정 시도",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 지원 현황 ID 또는 회원",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "APPLICATION_404_001",
                                      "message": "존재하지 않는 지원 현황입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<String> updateApplication(String email, Long applicationId, ApplicationRequestDTO.UpdateApplicationDTO request);

    @Operation(
            summary = "입사 지원 현황 삭제",
            description = """
                    특정 지원 현황 항목을 삭제합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **applicationId**: URL 경로에 포함합니다. (예: `DELETE /api/v1/applications/1`)

                    **본인 소유 확인**: 다른 사용자의 지원 현황은 삭제할 수 없습니다. (403 반환)

                    > ⚠️ **삭제는 복구 불가능합니다.** 삭제 전 사용자에게 확인을 요청하는 것을 권장합니다.
                    """
    )
    @Parameter(name = "applicationId", description = "삭제할 지원 현황의 고유 ID", required = true, example = "1")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "삭제 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": "입사 지원 기록이 성공적으로 삭제되었습니다."
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
                    responseCode = "403",
                    description = "본인 소유가 아닌 지원 현황 삭제 시도",
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
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않는 지원 현황 ID 또는 회원",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "APPLICATION_404_001",
                                      "message": "존재하지 않는 지원 현황입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<String> deleteApplication(String email, Long applicationId);

    @Operation(
            summary = "입사 지원 현황 요약 조회",
            description = """
                    로그인한 사용자의 전체 지원 현황을 분석해 평균 진행도와 계산 대상 공고 수를 반환합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **계산 제외 대상**: `PLANNED`(지원 예정), `DOCUMENT_REJECTED`(서류 불합격), `INTERVIEW_REJECTED`(면접 불합격)

                    **진행도 점수 기준 (status별):**
                    | status | 점수 |
                    |---|---|
                    | `APPLIED` | 10 |
                    | `DOCUMENT_PASSED` | 40 |
                    | `INTERVIEW_PASSED` | 70 |
                    | `FINAL_ACCEPTED` | 100 |

                    계산 대상 공고가 없으면 `averageProgress: 0.0`, `totalCalculatedCount: 0` 으로 반환됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "요약 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "averageProgress": 40.0,
                                        "totalCalculatedCount": 3
                                      }
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
            )
    })
    ApiResponse<ApplicationResponseDTO.ApplicationSummaryDTO> getApplicationSummary(String email);

    @Operation(
            summary = "다가오는 면접 일정 조회",
            description = """
                    로그인한 사용자의 면접 일정 중 오늘 이후(오늘 포함)인 항목을 가장 빠른 순으로 최대 3개 반환합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **반환 데이터 (`schedules` 배열 내 각 항목):**
                    | 필드 | 타입 | 설명 |
                    |---|---|---|
                    | `applicationId` | Long | 지원 현황 고유 ID |
                    | `companyName` | String | 회사명 |
                    | `jobTitle` | String | 지원 직무명 |
                    | `interviewAt` | String | 면접일 (`yyyy-MM-dd`) |
                    | `daysLeft` | Long | 남은 일수 (0 = D-Day, 3 = D-3) |

                    `interviewAt`이 설정되지 않은 지원 현황은 결과에 포함되지 않습니다.
                    면접 일정이 없으면 `schedules`는 빈 배열(`[]`)로 반환됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "다가오는 일정 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "schedules": [
                                          {
                                            "applicationId": 3,
                                            "companyName": "카카오",
                                            "jobTitle": "백엔드 개발자",
                                            "interviewAt": "2026-06-24",
                                            "daysLeft": 0
                                          },
                                          {
                                            "applicationId": 9,
                                            "companyName": "토스",
                                            "jobTitle": "백엔드 개발자",
                                            "interviewAt": "2026-06-27",
                                            "daysLeft": 3
                                          }
                                        ]
                                      }
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
            )
    })
    ApiResponse<ApplicationResponseDTO.UpcomingScheduleListDTO> getUpcomingSchedules(String email);
}
