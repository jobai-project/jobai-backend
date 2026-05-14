package com.jobai.backend.global.apiPayload.code;

import org.springframework.http.HttpStatus;

public interface BaseSuccessCode extends BaseCode {

    HttpStatus getHttpStatus();

}
