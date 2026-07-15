package com.jobai.backend.domain.scrap.controller;

import com.jobai.backend.domain.scrap.dto.ScrapRequestDTO;
import com.jobai.backend.domain.scrap.dto.ScrapResponseDTO;
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

@Tag(name = "Scrap", description = "채용공고 스크랩(북마크) API")
@SecurityRequirement(name = "cookieAuth")
public interface ScrapControllerDocs {

    @Operation(
            summary = "채용공고 스크랩 추가",
            description = """
                    로그인한 사용자가 특정 채용공고를 스크랩합니다. 공고 상세/목록 화면의 스크랩(북마크) 버튼에서 사용하세요.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **멱등(idempotent) 동작**: 이미 스크랩한 공고를 다시 요청해도 에러 없이 기존 스크랩 ID를 그대로 반환합니다
                    (중복 클릭/재시도에 안전).

                    **source 값**: 홈 추천(`GET /api/v1/home/recommended-jobs`), 최신 공고(`GET /api/v1/home/latest-jobs`),
                    검색(`GET /api/search/jobs`) 등 다른 API 응답의 `source`/`id` 필드를 그대로 넘기면 됩니다.
                    """
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ScrapRequestDTO.AddScrapDTO.class),
                    examples = @ExampleObject(value = """
                            {
                              "source": "PUBLIC",
                              "sourceId": 823
                            }
                            """)
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "스크랩 추가 성공(이미 스크랩되어 있었다면 기존 기록 반환)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScrapResponseDTO.AddResultDTO.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_201_001",
                                      "message": "리소스가 성공적으로 생성되었습니다.",
                                      "result": {
                                        "scrapId": 1
                                      }
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 source 값 (PUBLIC/PRIVATE 외의 값)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_400_001",
                                      "message": "source는 PUBLIC 또는 PRIVATE만 가능합니다.",
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
                    description = "존재하지 않는 채용공고 (sourceId가 실제 공고와 매칭 안 됨)",
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
    ApiResponse<ScrapResponseDTO.AddResultDTO> addScrap(String email, ScrapRequestDTO.AddScrapDTO request);

    @Operation(
            summary = "채용공고 스크랩 취소",
            description = """
                    로그인한 사용자가 특정 채용공고의 스크랩을 취소합니다.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **멱등(idempotent) 동작**: 스크랩되어 있지 않은 공고를 취소 요청해도 에러 없이 성공 처리됩니다.
                    """
    )
    @Parameter(name = "source", description = "공고 출처. 값: PUBLIC(공기업), PRIVATE(사기업)", required = true, example = "PUBLIC")
    @Parameter(name = "sourceId", description = "스크랩 취소할 공고의 고유 ID", required = true, example = "823")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "스크랩 취소 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = String.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": "스크랩이 취소되었습니다."
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
    ApiResponse<String> removeScrap(String email, String source, Long sourceId);

    @Operation(
            summary = "내 스크랩 목록 조회",
            description = """
                    로그인한 사용자가 스크랩한 채용공고 목록을 최근 스크랩순으로 반환합니다.
                    공기업/사기업 공고가 함께 섞여서 반환됩니다.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **참고**: 원본 공고가 삭제된 경우(드묾) 목록에서 자동으로 제외됩니다.
                    마감된 공고는 제외되지 않고 그대로 포함되며, `dDay`가 음수로 나타날 수 있습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공 (없으면 빈 배열)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScrapResponseDTO.ScrapListDTO.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "scraps": [
                                          {
                                            "scrapId": 2,
                                            "source": "PRIVATE",
                                            "sourceId": 55,
                                            "companyName": "카카오",
                                            "title": "백엔드 개발자",
                                            "location": "판교",
                                            "employmentType": "경력",
                                            "dDay": null,
                                            "scrappedAt": "2026-07-05T10:00:00"
                                          },
                                          {
                                            "scrapId": 1,
                                            "source": "PUBLIC",
                                            "sourceId": 823,
                                            "companyName": "한국전력공사",
                                            "title": "2026년도 하반기 대졸수준 채용공고",
                                            "location": "서울",
                                            "employmentType": "정규직",
                                            "dDay": 12,
                                            "scrappedAt": "2026-07-04T09:00:00"
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
    ApiResponse<ScrapResponseDTO.ScrapListDTO> getMyScraps(String email);

    @Operation(
            summary = "곧 마감되는 스크랩 공고 조회",
            description = """
                    홈 화면의 곧 마감되는 스크랩 공고 영역에서 사용할 목록을 조회합니다.

                    마감일이 오늘 이후인 스크랩 공고를 대상으로 하며, 최대 3개까지 반환합니다.
                    정렬 기준은 마감일 오름차순, 같은 마감일이면 최근 스크랩한 순, 그래도 같으면 공고 ID 내림차순입니다.
                    마감일이 없거나 이미 지난 공고는 제외됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ScrapResponseDTO.ScrapListDTO.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자",
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
    ApiResponse<ScrapResponseDTO.ScrapListDTO> getUpcomingDeadlineScraps(String email);
}
