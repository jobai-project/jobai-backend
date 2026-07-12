package com.jobai.backend.domain.crawler.controller;

import com.jobai.backend.domain.crawler.dto.JobSummaryResponse;
import com.jobai.backend.domain.crawler.dto.PrivateJobDetailResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Private Jobs", description = "사기업 공고 상세 조회 및 LLM 요약 API")
public interface  PrivateJobControllerDocs {

    @Operation(
            summary = "사기업 공고 상세 조회",
            description = """
                    공고 ID로 사기업 채용공고의 상세 정보를 조회한다.
                    원문 description을 포함하며, 캐시된 요약이 있으면 함께 반환한다.
                    """
    )
    @Parameter(name = "id", description = "공고 ID", required = true, example = "1")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "id": 1,
                                        "title": "백엔드 개발자",
                                        "company": "카카오",
                                        "location": "판교",
                                        "employmentType": "경력",
                                        "jobCategory": "백엔드",
                                        "description": "공고 원문 내용...",
                                        "applyUrl": "https://careers.kakao.com/jobs/P-14472",
                                        "deadline": null,
                                        "createdAt": "2026-06-17T15:23:06.828455",
                                        "summary": null
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 공고를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_404_001",
                                      "message": "요청한 리소스를 찾을 수 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<PrivateJobDetailResponse> getJobDetail(Long id);

    @Operation(
            summary = "공고 LLM 요약 조회",
            description = """
                    공고 ID로 구조화된 LLM 요약을 조회한다.
                    첫 호출 시 LLM으로 요약을 생성하고 DB에 캐싱한다.
                    이후 호출에서는 캐시된 요약을 반환한다.
                    """
    )
    @Parameter(name = "id", description = "공고 ID", required = true, example = "1")
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
                                        "jobPostingId": 1,
                                        "title": "백엔드 개발자",
                                        "company": "카카오",
                                        "applyUrl": "https://careers.kakao.com/jobs/P-14472",
                                        "summary": {
                                          "techStack": ["Java", "Spring Boot", "Kubernetes"],
                                          "responsibilities": ["API 설계 및 개발", "시스템 아키텍처 설계"],
                                          "qualifications": ["Java 3년 이상", "Spring 프레임워크 경험"],
                                          "preferredQualifications": ["MSA 경험", "대규모 트래픽 처리 경험"]
                                        }
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "해당 ID의 공고를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_404_001",
                                      "message": "요청한 리소스를 찾을 수 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<JobSummaryResponse> getJobSummary(Long id);
}
