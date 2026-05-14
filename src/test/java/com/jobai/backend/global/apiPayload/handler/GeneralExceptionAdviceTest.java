package com.jobai.backend.global.apiPayload.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobai.backend.global.apiPayload.code.GeneralErrorCode;
import com.jobai.backend.global.apiPayload.exception.GeneralException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GeneralExceptionAdviceTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GeneralExceptionAdvice())
                .build();
    }

    // -------------------------------------------------------
    // 테스트용 더미 컨트롤러
    // -------------------------------------------------------

    @RestController
    @RequestMapping("/test")
    static class TestController {

        record TestRequest(@NotBlank(message = "이름은 필수입니다") String name) {}

        @GetMapping("/general-exception")
        public void generalException() {
            throw new GeneralException(GeneralErrorCode.NOT_FOUND);
        }

        @PostMapping("/valid")
        public void validBody(@Valid @RequestBody TestRequest request) {}

        @GetMapping("/type-mismatch/{id}")
        public void typeMismatch(@PathVariable Integer id) {}

        @GetMapping("/missing-param")
        public void missingParam(@RequestParam String name) {}

        @GetMapping("/server-error")
        public void serverError() {
            throw new RuntimeException("unexpected");
        }
    }

    // -------------------------------------------------------
    // GeneralException (커스텀 예외)
    // -------------------------------------------------------

    @Test
    void generalException_returns404() throws Exception {
        mockMvc.perform(get("/test/general-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_404_001"))
                .andExpect(jsonPath("$.errorDetail[0]").value("요청한 리소스를 찾을 수 없습니다."));
    }

    // -------------------------------------------------------
    // BindException / MethodArgumentNotValidException (@Valid RequestBody)
    // -------------------------------------------------------

    @Test
    void validBody_blankName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_002"))
                .andExpect(jsonPath("$.errorDetail[0]").value("name: 이름은 필수입니다"));
    }

    // -------------------------------------------------------
    // MethodArgumentTypeMismatchException (타입 미스매치)
    // -------------------------------------------------------

    @Test
    void typeMismatch_stringToInteger_returns400() throws Exception {
        mockMvc.perform(get("/test/type-mismatch/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_002"))
                .andExpect(jsonPath("$.errorDetail[0]", containsString("타입이 올바르지 않습니다")));
    }

    // -------------------------------------------------------
    // MissingServletRequestParameterException (필수 파라미터 누락)
    // -------------------------------------------------------

    @Test
    void missingParam_returns400() throws Exception {
        mockMvc.perform(get("/test/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_002"))
                .andExpect(jsonPath("$.errorDetail[0]").value("name: 필수 파라미터가 누락되었습니다."));
    }

    // -------------------------------------------------------
    // HttpRequestMethodNotSupportedException (405)
    // -------------------------------------------------------

    @Test
    void methodNotSupported_returns405() throws Exception {
        mockMvc.perform(delete("/test/general-exception"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_405_001"))
                .andExpect(jsonPath("$.errorDetail[0]", containsString("DELETE")));
    }

    // -------------------------------------------------------
    // HttpMessageNotReadableException (JSON 파싱 실패)
    // -------------------------------------------------------

    @Test
    void notReadable_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_001"))
                .andExpect(jsonPath("$.errorDetail[0]").value("요청 본문(JSON)을 올바르게 작성해 주세요."));
    }

    // -------------------------------------------------------
    // HttpMediaTypeNotSupportedException (415)
    // -------------------------------------------------------

    @Test
    void mediaTypeNotSupported_textPlain_returns415() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_415_001"));
    }

    // -------------------------------------------------------
    // Exception (500 fallback)
    // -------------------------------------------------------

    @Test
    void unhandledException_returns500() throws Exception {
        mockMvc.perform(get("/test/server-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_500_001"))
                .andExpect(jsonPath("$.errorDetail").doesNotExist());
    }
}
