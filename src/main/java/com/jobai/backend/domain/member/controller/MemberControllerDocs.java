package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.dto.MemberRequestDTO;
import com.jobai.backend.domain.member.dto.MemberResponseDTO;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member", description = "회원 마이페이지 API")
@SecurityRequirement(name = "cookieAuth")
public interface MemberControllerDocs {

    @Operation(
            summary = "마이페이지 전체 정보 조회",
            description = """
                    로그인한 사용자의 마이페이지 전체 정보를 반환합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **반환 데이터:**
                    - `profile`: 이름, 이메일, 프로필 이미지 URL
                    - `jobPreference`: 경력 구분(신입/경력), 희망 직무 리스트, 희망 지역 리스트
                    - `resumes`: 업로드된 이력서 목록 (파일명, S3 URL, 수정일, 활성 여부)

                    희망 직무/지역이 설정되지 않은 경우 빈 리스트(`[]`)로 반환됩니다.
                    이력서가 없는 경우도 빈 리스트로 반환됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "마이페이지 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MemberResponseDTO.MyPageDTO.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "profile": {
                                          "name": "홍길동",
                                          "email": "user@gmail.com",
                                          "profileImageUrl": "https://lh3.googleusercontent.com/..."
                                        },
                                        "jobPreference": {
                                          "careerType": "신입",
                                          "jobCategories": ["백엔드 개발", "풀스택 개발"],
                                          "locations": ["서울", "경기"]
                                        },
                                        "resumes": [
                                          {
                                            "resumeId": 1,
                                            "originalFilename": "홍길동_이력서.pdf",
                                            "storedFileUrl": "https://s3.amazonaws.com/...",
                                            "updatedAt": "2025-06-01",
                                            "isActive": true
                                          }
                                        ]
                                      }
                                    }
                                    """)
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
    ApiResponse<MemberResponseDTO.MyPageDTO> getMyPage(String email);

    @Operation(
            summary = "희망 공고 조건 설정 (전체 교체)",
            description = """
                    로그인한 사용자의 희망 공고 조건(경력 구분, 직무, 지역)을 업데이트합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **동작 방식**: 기존 설정을 **전체 교체**합니다. 유지하고 싶은 값도 다시 포함해서 보내야 합니다.

                    **careerType 허용 값**: `"신입"`, `"경력"` (자유 텍스트이므로 서버에서 별도 검증 없음)

                    **jobCategories / locations**: 빈 배열(`[]`) 전송 시 기존 설정이 모두 삭제됩니다.
                    """
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MemberRequestDTO.UpdateJobPreferenceDTO.class),
                    examples = @ExampleObject(
                            name = "희망 조건 설정 예시",
                            value = """
                                    {
                                      "careerType": "신입",
                                      "jobCategories": ["백엔드 개발", "풀스택 개발"],
                                      "locations": ["서울", "경기", "인천"]
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "희망 조건 업데이트 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = String.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": "공고 조건 설정이 성공적으로 변경되었습니다."
                                    }
                                    """)
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
    ApiResponse<String> updateJobPreferences(String email, MemberRequestDTO.UpdateJobPreferenceDTO request);

    @Operation(
            summary = "이름 수정",
            description = """
                    로그인한 사용자의 이름을 수정합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **제약 조건:**
                    - 이름은 필수 입력 항목입니다. (공백만 있는 경우 거부)
                    - 최대 20자까지 입력 가능합니다.
                    """
    )
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = MemberRequestDTO.UpdateNameDTO.class),
                    examples = @ExampleObject(
                            name = "이름 수정 예시",
                            value = """
                                    {
                                      "name": "김철수"
                                    }
                                    """
                    )
            )
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이름 수정 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = String.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": "이름이 성공적으로 수정되었습니다."
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "입력값 검증 실패 (이름 누락 또는 20자 초과)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "COMMON_400_001",
                                      "message": "이름은 공백일 수 없습니다.",
                                      "result": null
                                    }
                                    """)
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
    ApiResponse<String> updateName(String email, MemberRequestDTO.UpdateNameDTO request);
}
