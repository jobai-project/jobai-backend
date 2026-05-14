package com.jobai.backend.global.apiPayload;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------
    // onSuccess(code, result)
    // -------------------------------------------------------

    @Test
    void onSuccess_withResult_isSuccessTrue() {
        ApiResponse<String> response = ApiResponse.onSuccess(GeneralSuccessCode.OK, "data");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCode()).isEqualTo("COMMON_200_001");
        assertThat(response.getMessage()).isEqualTo("성공적으로 요청을 처리했습니다.");
        assertThat(response.getResult()).isEqualTo("data");
        assertThat(response.getErrorDetail()).isNull();
    }

    @Test
    void onSuccess_created_returnsCreatedCode() {
        ApiResponse<String> response = ApiResponse.onSuccess(GeneralSuccessCode.CREATED, "newResource");

        assertThat(response.getCode()).isEqualTo("COMMON_201_001");
        assertThat(response.getResult()).isEqualTo("newResource");
    }

    // -------------------------------------------------------
    // onSuccess(code) - Void 오버로드
    // -------------------------------------------------------

    @Test
    void onSuccess_withoutResult_resultIsNull() {
        ApiResponse<Void> response = ApiResponse.onSuccess(GeneralSuccessCode.OK);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isNull();
        assertThat(response.getErrorDetail()).isNull();
    }

    // -------------------------------------------------------
    // onFailure(code, errorDetail)
    // -------------------------------------------------------

    @Test
    void onFailure_isSuccessFalse() {
        ApiResponse<?> response = ApiResponse.onFailure(
                GeneralErrorCode.VALIDATION_ERROR,
                List.of("name: 비어 있을 수 없습니다")
        );

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("COMMON_400_002");
        assertThat(response.getMessage()).isEqualTo("요청 값 검증에 실패했습니다.");
        assertThat(response.getResult()).isNull();
        assertThat(response.getErrorDetail()).containsExactly("name: 비어 있을 수 없습니다");
    }

    @Test
    void onFailure_multipleErrors_allPresent() {
        List<String> errors = List.of("name: 필수값", "email: 형식 오류");
        ApiResponse<?> response = ApiResponse.onFailure(GeneralErrorCode.VALIDATION_ERROR, errors);

        assertThat(response.getErrorDetail()).hasSize(2).containsExactlyElementsOf(errors);
    }

    @Test
    void onFailure_withNullErrorDetail_errorDetailIsNull() {
        ApiResponse<?> response = ApiResponse.onFailure(GeneralErrorCode.BAD_REQUEST, null);

        assertThat(response.getErrorDetail()).isNull();
    }

    @Test
    void onFailure_defensiveCopy_callerMutationDoesNotAffectResponse() {
        List<String> mutable = new ArrayList<>();
        mutable.add("name: 필수값");

        ApiResponse<?> response = ApiResponse.onFailure(GeneralErrorCode.VALIDATION_ERROR, mutable);

        mutable.add("email: 형식 오류"); // 원본 수정

        assertThat(response.getErrorDetail()).hasSize(1); // 응답 내부는 영향 없음
    }

    // -------------------------------------------------------
    // JSON 직렬화 검증
    // -------------------------------------------------------

    @Test
    void json_isSuccess_key_not_success() throws Exception {
        ApiResponse<String> response = ApiResponse.onSuccess(GeneralSuccessCode.OK, "test");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"isSuccess\":true");
        assertThat(json).doesNotContain("\"success\"");
    }

    @Test
    void json_result_omitted_when_null() throws Exception {
        ApiResponse<?> response = ApiResponse.onFailure(
                GeneralErrorCode.BAD_REQUEST, List.of("error")
        );
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("\"result\"");
    }

    @Test
    void json_errorDetail_omitted_when_null() throws Exception {
        ApiResponse<String> response = ApiResponse.onSuccess(GeneralSuccessCode.OK, "data");
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("errorDetail");
    }

    @Test
    void json_errorDetail_omitted_when_empty_list() throws Exception {
        ApiResponse<?> response = ApiResponse.onFailure(GeneralErrorCode.INTERNAL_SERVER_ERROR, List.of());
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("errorDetail");
    }

    @Test
    void json_propertyOrder_isSuccess_before_code_before_message() throws Exception {
        ApiResponse<String> response = ApiResponse.onSuccess(GeneralSuccessCode.OK, "data");
        String json = objectMapper.writeValueAsString(response);

        int isSuccessIdx = json.indexOf("isSuccess");
        int codeIdx      = json.indexOf("\"code\"");
        int messageIdx   = json.indexOf("\"message\"");
        int resultIdx    = json.indexOf("\"result\"");

        assertThat(isSuccessIdx).isLessThan(codeIdx);
        assertThat(codeIdx).isLessThan(messageIdx);
        assertThat(messageIdx).isLessThan(resultIdx);
    }
}
