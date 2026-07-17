package com.jobai.backend.domain.home.controller;

import com.jobai.backend.domain.home.dto.ScrapRankingResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Home", description = "홈 화면 API")
public interface ScrapRankingControllerDocs {

    @Operation(
            summary = "실시간 스크랩 순위 조회",
            description = """
                    전체 사용자의 스크랩 수를 기준으로 인기 공고를 최대 5개까지 반환합니다.

                    프론트에서는 응답 배열을 그대로 받아 1~5위 애니메이션 노출에 사용하면 됩니다.
                    정렬 기준은 스크랩 수 내림차순이며, 동률이면 최근 스크랩된 공고를 우선합니다.
                    마감 처리된 공고는 순위에서 제외합니다.

                    인증 없이 호출 가능한 홈 화면 공개 API입니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScrapRankingResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "rankings": [
                                          {
                                            "rank": 1,
                                            "source": "PRIVATE",
                                            "sourceId": 55,
                                            "title": "Java 백엔드 개발자",
                                            "companyName": "카카오페이",
                                            "scrapCount": 18
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<ScrapRankingResponse> getScrapRankings();
}
