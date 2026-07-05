package com.jobai.backend.domain.scrap.controller;

import com.jobai.backend.domain.scrap.dto.ScrapRequestDTO;
import com.jobai.backend.domain.scrap.dto.ScrapResponseDTO;
import com.jobai.backend.domain.scrap.service.ScrapService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/scraps")
public class ScrapController implements ScrapControllerDocs {

    private final ScrapService scrapService;

    @PostMapping
    public ApiResponse<ScrapResponseDTO.AddResultDTO> addScrap(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ScrapRequestDTO.AddScrapDTO request
    ) {
        ScrapResponseDTO.AddResultDTO response = scrapService.addScrap(email, request.getSource(), request.getSourceId());
        return ApiResponse.onSuccess(GeneralSuccessCode.CREATED, response);
    }

    @DeleteMapping("/{source}/{sourceId}")
    public ApiResponse<String> removeScrap(
            @AuthenticationPrincipal String email,
            @PathVariable String source,
            @PathVariable Long sourceId
    ) {
        scrapService.removeScrap(email, source, sourceId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "스크랩이 취소되었습니다.");
    }

    @GetMapping
    public ApiResponse<ScrapResponseDTO.ScrapListDTO> getMyScraps(
            @AuthenticationPrincipal String email
    ) {
        ScrapResponseDTO.ScrapListDTO response = scrapService.getMyScraps(email);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
