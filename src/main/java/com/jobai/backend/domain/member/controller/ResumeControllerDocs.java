package com.jobai.backend.domain.member.controller;

import com.jobai.backend.domain.member.dto.ResumeResponseDTO;
import com.jobai.backend.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Resume", description = "이력서 관리 API")
@SecurityRequirement(name = "cookieAuth")
public interface ResumeControllerDocs {

    @Operation(
            summary = "이력서 목록 조회",
            description = """
                    로그인한 사용자의 이력서 목록을 업로드 최신순으로 반환합니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    | 필드 | 타입 | 설명 |
                    |---|---|---|
                    | `resumeId` | Long | 이력서 고유 ID |
                    | `originalFilename` | String | 원본 파일명 |
                    | `storedFileUrl` | String | S3 파일 URL |
                    | `fileSize` | String | 파일 크기 (예: 1.2 MB) |
                    | `isActive` | Boolean | 활성화 여부 |
                    | `uploadedAt` | String | 업로드 날짜 (`yyyy-MM-dd`) |
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "목록 조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": {
                                "resumes": [
                                  {
                                    "resumeId": 1,
                                    "originalFilename": "김주훈_이력서_20260606.pdf",
                                    "storedFileUrl": "https://bucket.s3.ap-northeast-2.amazonaws.com/resumes/1/...",
                                    "fileSize": "1.2 MB",
                                    "isActive": true,
                                    "uploadedAt": "2026-06-06"
                                  }
                                ]
                              }
                            }
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ApiResponse<ResumeResponseDTO.ResumeListDTO> getResumes(String email);

    @Operation(
            summary = "이력서 업로드",
            description = """
                    PDF 파일을 업로드합니다. `multipart/form-data` 형식으로 `file` 파트에 첨부하세요.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    - PDF 파일만 허용됩니다.
                    - 새로 업로드한 이력서는 자동으로 활성화(`isActive = true`)되며, 기존에 활성화되어 있던 이력서는 자동으로 비활성화됩니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "업로드 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_201_001",
                              "message": "리소스가 성공적으로 생성되었습니다.",
                              "result": { "resumeId": 3 }
                            }
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "PDF 파일이 아니거나 파일이 비어 있음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ApiResponse<ResumeResponseDTO.UploadResultDTO> uploadResume(String email, MultipartFile file);

    @Operation(
            summary = "이력서 활성화",
            description = """
                    특정 이력서를 활성화합니다. 기존에 활성화된 이력서는 자동으로 비활성화됩니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.
                    """
    )
    @Parameter(name = "resumeId", description = "활성화할 이력서 ID", required = true, example = "1")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "활성화 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": "이력서가 활성화되었습니다."
                            }
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 소유가 아닌 이력서"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이력서를 찾을 수 없음")
    })
    ApiResponse<String> activateResume(String email, Long resumeId);

    @Operation(
            summary = "이력서 삭제",
            description = """
                    특정 이력서를 삭제합니다. S3에서도 파일이 함께 삭제됩니다.

                    **인증 필요**: 유효한 `accessToken` 쿠키가 있어야 합니다.

                    > 삭제는 복구 불가능합니다.
                    """
    )
    @Parameter(name = "resumeId", description = "삭제할 이력서 ID", required = true, example = "1")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "삭제 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {
                              "isSuccess": true,
                              "code": "COMMON_200_001",
                              "message": "성공적으로 요청을 처리했습니다.",
                              "result": "이력서가 삭제되었습니다."
                            }
                            """))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 소유가 아닌 이력서"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "이력서를 찾을 수 없음")
    })
    ApiResponse<String> deleteResume(String email, Long resumeId);
}
