package com.jobai.backend.domain.auth.controller;

import com.jobai.backend.domain.auth.dto.AuthResponse;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Auth", description = "인증 관련 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "구글 로그인 URL 조회",
            description = """
                    구글 소셜 로그인을 시작하기 위한 리디렉션 URL을 반환합니다.

                    **인증 불필요**: 로그인 전에 호출하는 API이므로 accessToken 쿠키 없이 호출 가능합니다.

                    **사용 흐름:**
                    1. 이 API를 호출하여 `googleLoginUrl`을 받습니다.
                    2. 프론트엔드에서 해당 URL로 사용자를 리디렉션합니다.
                    3. 구글 로그인 완료 후 서버가 자동으로 `accessToken`을 **HttpOnly 쿠키**로 발급합니다.
                    4. 이후 모든 인증이 필요한 API는 해당 쿠키를 자동으로 첨부하면 됩니다.

                    > ⚠️ **주의**: Swagger UI에서 직접 리디렉션을 따라가면 로그인이 완료되지 않습니다.
                    > 실제 브라우저에서 해당 URL로 이동해야 합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "구글 로그인 URL 반환 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.LoginUrl.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "googleLoginUrl": "/oauth2/authorization/google"
                                      }
                                    }
                                    """)
                    )
            )
    })
    ApiResponse<AuthResponse.LoginUrl> getGoogleLoginUrl();

    @Operation(
            summary = "로그아웃",
            description = """
                    현재 로그인된 사용자를 로그아웃 처리합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    **동작 방식**: 서버에서 `accessToken` 쿠키를 만료(Max-Age=0)시켜 삭제합니다.
                    클라이언트는 별도로 쿠키를 삭제할 필요가 없습니다.
                    """
    )
    @SecurityRequirement(name = "cookieAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공 (Set-Cookie 헤더로 쿠키 만료 처리됨)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
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
    ApiResponse<Void> logout(HttpServletResponse response);

    @Operation(
            summary = "로그인한 내 정보 조회",
            description = """
                    현재 로그인된 사용자의 기본 정보(이메일, 이름)를 반환합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    로그인 여부 확인 또는 헤더에 사용자 이름을 표시할 때 활용하세요.
                    상세 프로필은 `/api/v1/members/me`를 사용하세요.

                    **onboardingCompleted**: 온보딩(4단계) 완료 여부입니다. `false`이면 온보딩 화면으로 안내하세요.
                    """
    )
    @SecurityRequirement(name = "cookieAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "내 정보 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.MemberInfo.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "COMMON_200_001",
                                      "message": "요청이 성공적으로 처리되었습니다.",
                                      "result": {
                                        "email": "user@gmail.com",
                                        "name": "홍길동",
                                        "onboardingCompleted": false
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
                    description = "쿠키는 있으나 DB에 존재하지 않는 회원",
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
    ApiResponse<AuthResponse.MemberInfo> getMyInfo(String email);
}
