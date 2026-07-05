package com.jobai.backend.domain.home.controller;

import com.jobai.backend.domain.home.dto.LatestJobsResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Home", description = "홈 화면 맞춤 공고 추천 API")
public interface LatestJobsControllerDocs {

    @Operation(
            summary = "최신 채용공고 목록 조회 (비로그인 공개 API)",
            description = """
                    로그인 여부와 무관하게 누구나 호출할 수 있는 공개 API입니다.
                    회원별 맞춤 매칭점수 없이 공기업/사기업 채용공고를 **최신순(createdAt DESC)**으로 반환합니다.

                    **인증 불필요**: accessToken 쿠키 없이 호출 가능합니다.

                    **로그인한 사용자용 맞춤 추천이 필요하면** `GET /api/v1/home/recommended-jobs`를 사용하세요.
                    그 API는 매칭점수 기반 정렬과 적합도 기준 필터를 지원합니다.

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
    @Parameter(name = "companyTypes", description = "기업형태 필터. 값: PUBLIC, PRIVATE (Swagger UI에서는 'Add string item'으로 값을 하나씩 추가하세요.)")
    @Parameter(name = "locations", description = "지역 필터. 값 예: 서울, 경기 (Swagger UI에서는 'Add string item'으로 값을 하나씩 추가하세요.)")
    @Parameter(name = "employmentTypes", description = "고용형태 필터. 값: 인턴, 신입, 경력직, 계약직 (Swagger UI에서는 'Add string item'으로 값을 하나씩 추가하세요.)")
    @Parameter(name = "offset", description = "조회 시작 위치 (0부터 시작)", example = "0")
    @Parameter(name = "size", description = "한 번에 불러올 공고 개수", example = "18")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LatestJobsResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "totalCount": 1234,
                                        "hasMore": true,
                                        "jobs": [
                                          {
                                            "id": 823,
                                            "source": "PUBLIC",
                                            "companyName": "한국전력공사",
                                            "title": "2026년도 하반기 대졸수준 채용공고",
                                            "dDay": 12,
                                            "location": "서울",
                                            "employmentType": "정규직"
                                          },
                                          {
                                            "id": 55,
                                            "source": "PRIVATE",
                                            "companyName": "카카오",
                                            "title": "백엔드 개발자",
                                            "dDay": null,
                                            "location": "판교",
                                            "employmentType": "경력"
                                          }
                                        ]
                                      }
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<LatestJobsResponse> getLatestJobs(
            java.util.List<String> companyTypes,
            java.util.List<String> locations,
            java.util.List<String> employmentTypes,
            int offset,
            int size
    );
}
