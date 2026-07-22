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

                    **인증 선택적**: 로그인 없이도 사용 가능하며, 로그인 시 추가 기능을 제공합니다.

                    **동작 방식 (3단계 파이프라인)**:
                    1. **키워드 분석 (KeywordMatcher)** — 형태소 분석으로 쿼리를 카테고리·지역·경력 등 구조화된 조건으로 파싱합니다. 인식되지 않은 토큰은 다음 단계로 전달됩니다.
                    2. **Query Expansion** — 인식되지 않은 토큰이 있으면 LLM으로 분석하여 `EXACT_REQUIRED`(특정 기술명 필터) 또는 `SEMANTIC_PREFERRED`(의미 유사 키워드) 그룹으로 확장합니다.
                    3. **검색 라우팅 (4-path)** — 분석 결과에 따라 아래 경로 중 하나로 처리됩니다.
                       - **Path A (KEYWORD)**: 모든 토큰이 구조화 조건으로 인식된 경우 — DB 필터 검색
                       - **Path B (HYBRID)**: 구조화 조건 + 확장 키워드가 있는 경우 — DB 필터 + 벡터 검색 후 RRF 병합
                       - **Path C (VECTOR)**: 순수 자연어 쿼리 — 벡터 유사도 검색
                       - **Path D (EXACT-FIRST)**: `EXACT_REQUIRED` 토큰이 있는 경우 — 필수 키워드 포함 공고 우선, 미포함 공고 후순위 병합
                    4. **Rerank** — Cross-Encoder 모델로 그룹 순서(STRICT > RELAXED)를 유지하며 그룹 내 결과를 재정렬합니다.

                    각 단계는 서버 설정으로 독립 on/off 가능하며, 프론트 입장에서는 동일한 요청/응답 형식을 사용합니다.

                    **matchType 값 설명**:
                    - `STRICT`: 카테고리·지역·경력 조건 모두 일치
                    - `UNKNOWN_STRUCTURAL`: 경력 표기가 '미확인'인 공고 (크롤링 시 경력 구분 불가)
                    - `RELAXED_SEMANTIC`: 의미 유사도 기반으로 매칭된 공고
                    - `RELAXED_EXACT`: 필수 키워드 포함 공고 (EXACT_REQUIRED 기반)
                    - `SIMILAR`: 조건 불일치 공고 (참고용으로 하단 노출)
                    - `null`: 단순 키워드 검색(Path A) 결과

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
                                            "matchType": "STRICT",
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
                    description = "인증 토큰이 유효하지 않은 경우 (비로그인 사용자는 정상 응답)",
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
