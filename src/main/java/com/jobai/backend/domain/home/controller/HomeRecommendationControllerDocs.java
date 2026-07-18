package com.jobai.backend.domain.home.controller;

import com.jobai.backend.domain.home.dto.HomeRecommendationResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Home", description = "홈 화면 맞춤 공고 추천 API")
@SecurityRequirement(name = "cookieAuth")
public interface HomeRecommendationControllerDocs {

    @Operation(
            summary = "맞춤 채용공고 목록 조회",
            description = """
                    로그인한 사용자에게 공기업/사기업 채용공고를 매칭점수 높은 순으로 정렬해 반환합니다.

                    **인증 필요**: 로그인 후 발급된 accessToken 쿠키가 있어야 합니다.

                    **매칭점수(matchScore) 안내**: 활성 이력서를 기준으로 AI 서버가 산출해 저장한 실제 매칭점수를 우선 반환합니다.
                    신규·변경 공고처럼 배치 또는 비동기 계산을 기다리는 동안 실제 점수가 아직 없을 때만
                    실제 AI 매칭점수가 아직 저장되지 않은 공고는 추천 결과에서 제외합니다.

                    **매칭 근거가 없는 회원 처리**: 온보딩을 완료하지 않았거나 희망직무/희망지역을
                    하나도 설정하지 않은 회원은 매칭점수를 계산할 근거가 없다고 보고, 매 항목의 `matchScore`를
                    `null`로 반환하며 목록 전체를 **최신순(createdAt DESC)**으로 정렬합니다.
                    이 경우 적합도 기준 필터도 적용되지 않습니다(필터 근거 자체가 없으므로).

                    **적합도 기준(matchScoreThreshold) 필터링**: 매칭 근거가 있는 회원에 한해, 온보딩 또는
                    마이페이지 알림 설정(`PATCH /api/v1/members/me/onboarding/notification-settings`)에서
                    설정한 적합도 기준 점수 **이상인 공고만** 반환됩니다. 아직 설정한 적이 없는 회원은 기본값 70이 적용됩니다.
                    `totalCount`/`hasMore`도 이 필터가 적용된 이후의 개수를 기준으로 계산됩니다.

                    **페이지네이션**: `offset`/`size` 기반입니다. 처음 조회는 `offset=0`, "더 불러오기" 클릭 시
                    `offset += size`로 누적 호출하면 됩니다. 응답의 `hasMore`가 `false`면 더 이상 불러올 데이터가 없습니다.

                    **필터 (다중 선택 가능, 미지정 시 전체)**
                    | 파라미터 | 허용 값 | 기본값 |
                    |---|---|---|
                    | `companyTypes` | `PUBLIC`(공기업), `PRIVATE`(사기업) | 둘 다 |
                    | `locations` | 서울, 경기, 인천, 부산, 대구, 광주, 대전, 울산, 세종, 강원, 충북, 충남, 전북, 전남, 경북, 경남, 제주 등 | 전체(필터 없음) |
                    | `employmentTypes` | 인턴, 신입, 경력직, 계약직 | 전체(필터 없음) |
                    """
    )
    @Parameter(name = "companyTypes", description = "기업형태 필터. 값: PUBLIC, PRIVATE (Swagger UI에서는 'Add string item'으로 값을 하나씩 추가하세요. 실제 호출 시에는 companyTypes=PUBLIC&companyTypes=PRIVATE 처럼 반복하거나 콤마로 이어 붙여도 됩니다.)")
    @Parameter(name = "locations", description = "지역 필터. 값 예: 서울, 경기 (Swagger UI에서는 'Add string item'으로 값을 하나씩 추가하세요.)")
    @Parameter(name = "employmentTypes", description = "고용형태 필터. 값: 인턴, 신입, 경력직, 계약직 (Swagger UI에서는 'Add string item'으로 값을 하나씩 추가하세요.)")
    @Parameter(name = "offset", description = "조회 시작 위치 (0부터 시작)", example = "0")
    @Parameter(name = "size", description = "한 번에 불러올 공고 개수 (1~100)", example = "18")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = HomeRecommendationResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "totalCount": 987,
                                        "hasMore": true,
                                        "jobs": [
                                          {
                                            "id": 101,
                                            "source": "PUBLIC",
                                            "companyName": "한국전력공사",
                                            "title": "2026년 신입사원 채용",
                                            "matchScore": 92,
                                            "dDay": 5,
                                            "location": "서울",
                                            "employmentType": "정규직"
                                          },
                                          {
                                            "id": 55,
                                            "source": "PRIVATE",
                                            "companyName": "카카오",
                                            "title": "백엔드 개발자",
                                            "matchScore": 88,
                                            "dDay": null,
                                            "location": "판교",
                                            "employmentType": "경력"
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
    ApiResponse<HomeRecommendationResponse> getRecommendedJobs(
            String email,
            List<String> companyTypes,
            List<String> locations,
            List<String> employmentTypes,
            int offset,
            int size
    );
}
