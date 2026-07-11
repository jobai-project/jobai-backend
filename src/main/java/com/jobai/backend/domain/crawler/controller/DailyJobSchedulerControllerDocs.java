package com.jobai.backend.domain.crawler.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Scheduler", description = "새벽 파이프라인 수동 실행")
public interface DailyJobSchedulerControllerDocs {

    @Operation(
            summary = "새벽 파이프라인 수동 실행",
            description = """
                    사기업 수집 → 공기업 수집 → 임베딩 생성 → 매칭 점수 산출 파이프라인을 즉시 실행한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "파이프라인 실행 성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"새벽 파이프라인 실행 완료\"")
                    )
            )
    })
    ResponseEntity<String> triggerDailyPipeline();

    @Operation(
            summary = "미분류 공고 일괄 분류",
            description = """
                    jobCategory가 null인 공고를 LLM으로 일괄 분류한다.
                    최대 100건씩 처리한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "분류 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"미분류 공고 15건 분류 완료\"")
                    )
            )
    })
    ResponseEntity<String> classifyUnclassified();

    @Operation(
            summary = "고용형태/경력 미분류 공고 일괄 분류",
            description = """
                    jobCategory는 있지만 employmentType 또는 experienceLevel이 null인 공고를 LLM으로 일괄 분류한다.
                    최대 100건씩 처리한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "분류 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"고용형태/경력 미분류 공고 10건 분류 완료\"")
                    )
            )
    })
    ResponseEntity<String> classifyMissingEmploymentTypes();

    @Operation(
            summary = "임베딩 생성",
            description = """
                    임베딩이 아직 생성되지 않은 공고를 찾아 AI 서버를 호출하여 임베딩을 생성한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "임베딩 생성 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"임베딩 생성 완료\"")
                    )
            )
    })
    ResponseEntity<String> generateEmbeddings();

    @Operation(
            summary = "매칭 점수 산출",
            description = """
                    신규/변경 공고에 대해 AI 서버를 호출하여 매칭 점수를 산출한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "점수 산출 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"매칭 점수 산출 완료\"")
                    )
            )
    })
    ResponseEntity<String> scorePostings();

    @Operation(
            summary = "공기업 매칭 점수 산출",
            description = """
                    신규/변경 공기업 공고에 대해 AI 서버(/score/public)를 호출하여 매칭 점수를 산출한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "점수 산출 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"공기업 매칭 점수 산출 완료\"")
                    )
            )
    })
    ResponseEntity<String> scorePublicPostings();

    @Operation(
            summary = "이력서 임베딩 생성",
            description = """
                    활성 이력서 중 임베딩이 없는 이력서에 대해 AI 서버를 호출하여 임베딩을 생성한다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이력서 임베딩 생성 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"이력서 임베딩 완료: 3/3건 성공\"")
                    )
            )
    })
    ResponseEntity<String> generateResumeEmbeddings();
}
