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
                    홈 화면에 표시할 IT 인사이트 카드를 반환합니다 (데이터 유무에 따라 최대 3장).

                    **카드 구성**
                    1. 신규 공고 카드: 오늘 수집된 공고 총 건수
                    2~3. 테크 뉴스 카드: HackerNews/GeekNews에서 수집한 뉴스 중 랜덤 2건 (새로고침마다 변경)

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
                                            "badge": "신규 공고",
                                            "headline": "오늘 새로 올라온 공고가 12건 있어요",
                                            "subtext": "새로운 채용 기회를 확인해보세요",
                                            "originalUrl": null,
                                            "publishedAt": null,
                                            "createdAt": "2026-07-14T09:00:00",
                                            "relatedJobs": null
                                          },
                                          {
                                            "id": 42,
                                            "source": "HACKERNEWS",
                                            "badge": "테크 뉴스",
                                            "headline": "구글, AI 코딩 도우미 무료로 풀었어요",
                                            "subtext": "VS Code에서 바로 쓸 수 있어요",
                                            "originalUrl": "https://news.ycombinator.com/item?id=...",
                                            "publishedAt": "2026-07-14T06:00:00",
                                            "createdAt": "2026-07-14T07:05:00",
                                            "relatedJobs": null
                                          },
                                          {
                                            "id": 55,
                                            "source": "GEEKNEWS",
                                            "badge": "테크 뉴스",
                                            "headline": "요즘 Go 언어 인기가 심상치 않아요",
                                            "subtext": "백엔드 공고에서 Go 언급이 늘고 있어요",
                                            "originalUrl": "https://news.hada.io/topic?id=...",
                                            "publishedAt": "2026-07-14T08:00:00",
                                            "createdAt": "2026-07-14T09:00:00",
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
