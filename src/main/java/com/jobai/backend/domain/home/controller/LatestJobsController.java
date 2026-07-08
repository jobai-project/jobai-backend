package com.jobai.backend.domain.home.controller;

import com.jobai.backend.domain.home.dto.LatestJobsResponse;
import com.jobai.backend.domain.home.service.LatestJobsService;
import com.jobai.backend.global.apiPayload.ApiResponse;
import com.jobai.backend.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/home")
public class LatestJobsController implements LatestJobsControllerDocs {

    private final LatestJobsService latestJobsService;

    @GetMapping("/latest-jobs")
    public ApiResponse<LatestJobsResponse> getLatestJobs(
            @RequestParam(required = false) List<String> companyTypes,
            @RequestParam(required = false) List<String> locations,
            @RequestParam(required = false) List<String> employmentTypes,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "18") int size
    ) {
        LatestJobsResponse response = latestJobsService.getLatestJobs(companyTypes, locations, employmentTypes, offset, size);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
