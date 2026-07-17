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

                    **동작 방식 (3단계 파이프라인)**:
                    1. **Query Expansion** — 쿼리에서 인식되지 않는 자연어 표현을 LLM으로 분석하여 관련 키워드를 확장합니다.
                    2. **검색 라우팅** — 모든 토큰이 인식되면 구조화 검색(`KEYWORD`), 하이브리드 모드가 활성화되면 키워드+벡터 검색을 RRF로 병합(`HYBRID`), 그 외 미인식 표현이 있으면 벡터 검색(`VECTOR`)을 수행합니다.
                    3. **Rerank** — Cross-Encoder 모델로 상위 후보를 쿼리 의도에 맞게 재정렬합니다.

                    각 단계는 서버 설정으로 독립 on/off 가능하며, 프론트 입장에서는 동일한 요청/응답 형식을 사용합니다.

                    **예시 쿼리**: `"서울 신입 백엔드"`, `"재택근무 가능한 백엔드"`, `"Java 경험이 있는 백엔드 개발자"`
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
                                        "totalCount": 42,
                                        "jobs": [
                                          {
                                            "id": 32,
                                            "source": "PRIVATE",
                                            "title": "백엔드 개발자 (Java 필수)",
                                            "company": "카카오",
                                            "location": "판교",
                                            "jobCategory": "백엔드",
                                            "employmentType": "정규직",
                                            "experienceLevel": "경력",
                                            "applyUrl": "https://careers.kakao.com/jobs/P-14472",
                                            "deadline": null,
                                            "createdAt": "2026-06-17T15:23:06.828455",
                                            "matchType": "EXACT",
                                            "matchScore": 92
                                          },
                                          {
                                            "id": 101,
                                            "source": "PUBLIC",
                                            "title": "2026년 IT 직군 채용",
                                            "company": "한국전력공사",
                                            "location": "서울",
                                            "jobCategory": null,
                                            "employmentType": "정규직",
                                            "experienceLevel": "신입",
                                            "applyUrl": "https://recruit.kepco.co.kr",
                                            "deadline": "2026-07-16",
                                            "createdAt": "2026-07-02T10:00:00",
                                            "matchType": "SIMILAR",
                                            "matchScore": null
                                          }
                                        ],
                                        "searchInfo": {
                                          "method": "HYBRID",
                                          "matchedCategories": ["백엔드"],
                                          "expandedKeywords": ["원격근무", "리모트워크", "WFH"]
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
    ApiResponse<JobSearchResponse> searchJobs(com.jobai.backend.domain.search.dto.JobSearchRequest request, @Parameter(hidden = true) String email);
}
