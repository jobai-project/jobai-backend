package com.jobai.backend.global.apiPayload.exception;

import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralExceptionTest {

    // -------------------------------------------------------
    // 첫 번째 생성자 - GeneralException(BaseErrorCode)
    // -------------------------------------------------------

    @Test
    void constructor_withErrorCode_messageIsErrorCodeMessage() {
        GeneralException ex = new GeneralException(GeneralErrorCode.NOT_FOUND);

        assertThat(ex.getMessage()).isEqualTo(GeneralErrorCode.NOT_FOUND.getMessage());
        assertThat(ex.getErrorCode()).isEqualTo(GeneralErrorCode.NOT_FOUND);
    }

    @Test
    void constructor_withNullErrorCode_throwsNullPointerException() {
        assertThatThrownBy(() -> new GeneralException(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("errorCode must not be null");
    }

    // -------------------------------------------------------
    // 두 번째 생성자 - GeneralException(BaseErrorCode, String)
    // -------------------------------------------------------

    @Test
    void constructor_withDetailMessage_usesDetailMessage() {
        GeneralException ex = new GeneralException(GeneralErrorCode.NOT_FOUND, "ID: 1 회원 없음");

        assertThat(ex.getMessage()).isEqualTo("ID: 1 회원 없음");
        assertThat(ex.getErrorCode()).isEqualTo(GeneralErrorCode.NOT_FOUND);
    }

    @Test
    void constructor_withNullDetailMessage_fallsBackToErrorCodeMessage() {
        GeneralException ex = new GeneralException(GeneralErrorCode.NOT_FOUND, null);

        assertThat(ex.getMessage()).isEqualTo(GeneralErrorCode.NOT_FOUND.getMessage());
    }

    @Test
    void constructor_withBlankDetailMessage_fallsBackToErrorCodeMessage() {
        GeneralException ex = new GeneralException(GeneralErrorCode.NOT_FOUND, "   ");

        assertThat(ex.getMessage()).isEqualTo(GeneralErrorCode.NOT_FOUND.getMessage());
    }

    @Test
    void constructor_withNullErrorCodeAndDetailMessage_throwsNullPointerException() {
        assertThatThrownBy(() -> new GeneralException(null, "detail"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("errorCode must not be null");
    }
}
