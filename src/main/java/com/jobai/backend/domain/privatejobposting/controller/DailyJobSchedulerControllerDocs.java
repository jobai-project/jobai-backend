package com.jobai.backend.domain.privatejobposting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Trigger", description = "테스트용 수동 실행")
public interface DailyJobSchedulerControllerDocs {

    @Operation(
            summary = "새벽 파이프라인 수동 실행",
            description = """
                    사기업 수집 → 공기업 수집 → 직무/고용형태/경력 분류 → 지역 분류 → 임베딩 생성 → 매칭 점수 산출 파이프라인을 즉시 실행합니다.
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
                    jobCategory가 null인 공고를 LLM으로 일괄 분류합니다.
                    최대 100건씩 처리합니다.
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
                    jobCategory는 있지만 employmentType 또는 experienceLevel이 null인 공고를 LLM으로 일괄 분류합니다.
                    최대 100건씩 처리합니다.
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
            summary = "지역 미분류 공고 일괄 분류",
            description = """
                    location은 있지만 region이 null인 공고를 LLM으로 대분류 지역으로 정규화합니다.
                    최대 100건씩 처리합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "분류 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"지역 미분류 공고 12건 분류 완료\"")
                    )
            )
    })
    ResponseEntity<String> classifyMissingRegions();

    @Operation(
            summary = "임베딩 생성",
            description = """
                    임베딩이 아직 생성되지 않은 공고를 찾아 AI 서버를 호출하여 임베딩을 생성합니다.
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
                    신규/변경 공고에 대해 AI 서버를 호출하여 매칭 점수를 산출합니다.
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
                    신규/변경 공기업 공고에 대해 AI 서버(/score/public)를 호출하여 매칭 점수를 산출합니다.
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
                    활성 이력서 중 임베딩이 없는 이력서에 대해 AI 서버를 호출하여 임베딩을 생성합니다.
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

    @Operation(
            summary = "IT 뉴스 카드 수집",
            description = """
                    HackerNews에서 IT 뉴스를 수집하고 LLM으로 요약하여 테크 카드를 생성합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "수집 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"IT 뉴스 카드 수집 완료\"")
                    )
            )
    })
    ResponseEntity<String> collectTechCards();

    @Operation(
            summary = "[테스트] 알림 발송 테스트",
            description = """
                    기존 DB에 저장된 점수를 기반으로 임계값 이상 공고에 대해 알림을 발송합니다.
                    점수 산출은 하지 않으며, 알림 파이프라인(WebSocket·Slack·Discord)만 테스트합니다.

                    > ⚠️ **테스트용 API입니다.** 실 서비스에서는 새벽 2시 스케줄러가 점수 산출 후 자동 발송합니다.

                    **인증 불필요**: `/api/v1/scheduler/**` 경로는 permitAll 설정이므로 인증 없이 호출 가능합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "알림 발송 완료",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"알림 테스트 완료 — 임계값 이상 공고 5건 알림 발송\"")
                    )
            )
    })
    ResponseEntity<String> triggerNotifyTest();
}
