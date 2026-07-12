package com.jobai.backend.domain.techcard.controller;

import com.jobai.backend.domain.techcard.dto.TechCardResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Home", description = "홈 화면 API")
public interface TechCardControllerDocs {

    @Operation(
            summary = "IT 인사이트 카드 조회",
            description = """
                    홈 화면에 표시할 IT 인사이트 카드 3장을 반환합니다.

                    **카드 구성 (순서 고정)**
                    1. 카테고리별 신규 공고 카드: 오늘 수집된 공고를 직무별로 집계
                    2. 신규 공고 현황 카드: 오늘 총 수집 건수 + 관련 공고 목록 (최대 5건)
                    3. 외부 IT 뉴스 카드: HackerNews에서 수집한 최신 트렌드 1장

                    **인증 불필요**: accessToken 쿠키 없이 호출 가능합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TechCardResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "성공적으로 요청을 처리했습니다.",
                                      "result": {
                                        "cards": [
                                          {
                                            "id": null,
                                            "source": "INTERNAL",
                                            "headline": "오늘 백엔드 공고 5건이 새로 올라왔어요",
                                            "subtext": "프론트엔드 3건, AI/ML 2건도 함께 수집됐어요",
                                            "originalUrl": null,
                                            "publishedAt": null,
                                            "createdAt": "2026-07-12T09:00:00",
                                            "relatedJobs": null
                                          },
                                          {
                                            "id": null,
                                            "source": "INTERNAL",
                                            "headline": "오늘 새로 올라온 공고가 12건 있어요",
                                            "subtext": "새로운 채용 기회를 확인해보세요",
                                            "originalUrl": null,
                                            "publishedAt": null,
                                            "createdAt": "2026-07-12T09:00:00",
                                            "relatedJobs": [
                                              {
                                                "id": 123,
                                                "source": "PRIVATE",
                                                "companyName": "카카오",
                                                "title": "백엔드 개발자"
                                              },
                                              {
                                                "id": 124,
                                                "source": "PRIVATE",
                                                "companyName": "토스",
                                                "title": "서버 엔지니어"
                                              }
                                            ]
                                          },
                                          {
                                            "id": 42,
                                            "source": "HACKERNEWS",
                                            "headline": "요즘 Rust가 개발자들 사이에서 화제예요",
                                            "subtext": "시스템 프로그래밍 공고에서 Rust 수요가 늘고 있어요",
                                            "originalUrl": "https://news.ycombinator.com/item?id=...",
                                            "publishedAt": "2026-07-12T06:00:00",
                                            "createdAt": "2026-07-12T07:05:00",
                                            "relatedJobs": null
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<TechCardResponse> getTechCards();
}
