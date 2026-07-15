package com.jobai.backend.domain.search.controller;

import com.jobai.backend.domain.search.dto.JobSearchResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Job Search", description = "자연어 공고 검색 API")
@SecurityRequirement(name = "cookieAuth")
public interface JobSearchControllerDocs {

    @Operation(
            summary = "자연어 공고 검색",
            description = """
                    자연어 쿼리를 입력받아 공기업/사기업 채용공고를 통합 검색합니다.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **동작 방식**:
                    쿼리에서 직무 카테고리/지역/경력 키워드를 자동으로 추출합니다.
                    모든 토큰이 인식되면 구조화 검색(`method: "KEYWORD"`)을 쓰고,
                    "혼자 일하기 좋은" 처럼 인식 안 되는 표현이 섞이면 의미 기반 벡터 검색(`method: "VECTOR"`)으로 전환됩니다.
                    두 방식 모두 프론트 입장에서는 같은 요청/응답 형식을 씁니다.

                    **예시 쿼리**: `"서울 신입 백엔드"`, `"판교에서 혼자 일하기 좋은 경력직 백엔드"`
                    """
    )
    @Parameter(name = "query", description = "검색어(자연어 문장 또는 키워드 조합)", required = true, example = "서울 신입 백엔드")
    @Parameter(name = "page", description = "페이지 번호 (0부터 시작)", required = true, example = "0")
    @Parameter(name = "size", description = "페이지당 결과 수 (1~100)", required = true, example = "20")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "검색 성공 (결과가 없으면 jobs는 빈 배열)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = JobSearchResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "totalCount": 2,
                                        "jobs": [
                                          {
                                            "id": 55,
                                            "source": "PRIVATE",
                                            "title": "백엔드 개발자",
                                            "company": "카카오",
                                            "location": "판교",
                                            "jobCategory": "백엔드",
                                            "employmentType": "경력",
                                            "applyUrl": "https://careers.kakao.com/jobs/P-14472",
                                            "deadline": null,
                                            "createdAt": "2026-06-17T15:23:06.828455"
                                          },
                                          {
                                            "id": 101,
                                            "source": "PUBLIC",
                                            "title": "2026년 신입사원 채용",
                                            "company": "한국전력공사",
                                            "location": "서울",
                                            "jobCategory": null,
                                            "employmentType": "정규직",
                                            "applyUrl": "https://recruit.kepco.co.kr",
                                            "deadline": "2026-07-16",
                                            "createdAt": "2026-07-02T10:00:00"
                                          }
                                        ],
                                        "searchInfo": {
                                          "method": "KEYWORD",
                                          "matchedCategories": ["백엔드"],
                                          "expandedKeywords": []
                                        }
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 (query 누락, page 음수, size 범위 초과 등)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_400_002",
                                      "message": "요청 값 검증에 실패했습니다.",
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
            )
    })
    ApiResponse<JobSearchResponse> searchJobs(com.jobai.backend.domain.search.dto.JobSearchRequest request, String email);
}
