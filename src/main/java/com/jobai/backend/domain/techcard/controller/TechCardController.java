package com.jobai.backend.domain.techcard.controller;

import com.jobai.backend.domain.techcard.dto.TechCardResponse;
import com.jobai.backend.domain.techcard.service.TechCardService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class TechCardController implements TechCardControllerDocs {

    private final TechCardService techCardService;

    @Override
    @GetMapping("/tech-cards")
    public ApiResponse<TechCardResponse> getTechCards(@AuthenticationPrincipal String email) {
        TechCardResponse response = techCardService.getTechCards(email);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
