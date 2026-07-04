package com.jobai.backend.domain.publicInstitution.controller;

import com.jobai.backend.domain.publicInstitution.dto.PublicJobPostingDetailResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "PublicJobPosting", description = "공공기관 채용공고 조회 API")
@SecurityRequirement(name = "cookieAuth")
public interface PublicJobPostingControllerDocs {

    @Operation(
            summary = "공공기관 채용공고 상세 조회",
            description = """
                    채용공고 목록에서 특정 공고를 선택했을 때 보여줄 상세 정보를 반환합니다.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **htmlContent 안내**: 원본 공고의 직무기술서(PDF)를 파싱한 텍스트/HTML 본문입니다.
                    "공고 상세" 영역에는 이 필드를 그대로 렌더링하면 됩니다.

                    **참고**: `companyType`은 현재 수집 단계에서 대부분 비어있을 수 있고,
                    `endDate`가 null이면 상시모집 등 마감일이 없는 공고입니다.
                    """
    )
    @Parameter(name = "id", description = "공공기관 채용공고 고유 ID", required = true, example = "1")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "상세 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "id": 1,
                                        "title": "2026년도 하반기 대졸수준 채용공고",
                                        "companyName": "한국전력공사",
                                        "companyType": "공기업",
                                        "recrutType": "정규직",
                                        "workExperience": "신입",
                                        "workRegion": "서울,광주,전남",
                                        "beginDate": "2026-05-15",
                                        "endDate": null,
                                        "jobRole": "IT/인터넷",
                                        "applyQualification": "학력 및 전공 무관, 병역필 또는 면제자",
                                        "disqualificationReason": "공사 인사규정 제10조 결격사유에 해당하는 자",
                                        "applicationMethod": "온라인 접수(공사 채용 홈페이지)",
                                        "applyLink": "https://recruit.kepco.co.kr",
                                        "isClosed": false,
                                        "htmlContent": "<p>모집분야 및 인원...</p>"
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
                    description = "존재하지 않는 채용공고",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_404_001",
                                      "message": "해당 채용공고를 찾을 수 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<PublicJobPostingDetailResponse> getPublicJobDetail(Long id);
}
