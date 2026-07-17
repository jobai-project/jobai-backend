package com.jobai.backend.domain.home.controller;

import com.jobai.backend.domain.home.dto.ScrapRankingResponse;
import com.jobai.backend.domain.home.service.ScrapRankingService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class ScrapRankingController implements ScrapRankingControllerDocs {

    private final ScrapRankingService scrapRankingService;

    @GetMapping("/scrap-rankings")
    public ApiResponse<ScrapRankingResponse> getScrapRankings() {
        ScrapRankingResponse response = scrapRankingService.getPopularScraps();
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
