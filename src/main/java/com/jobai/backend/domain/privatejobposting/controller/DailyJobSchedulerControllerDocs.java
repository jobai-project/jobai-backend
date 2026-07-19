package com.jobai.backend.domain.privatejobposting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.Map;

@Tag(name = "Trigger", description = "테스트용 수동 실행")
public interface DailyJobSchedulerControllerDocs {

    @Operation(
            summary = "새벽 파이프라인 수동 실행",
            description = """
                    전체 파이프라인을 백그라운드로 즉시 실행합니다.
                    202 Accepted를 즉시 반환하며, 실제 처리는 비동기로 ��행됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "파이프라인 실행 시작됨",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"새벽 파이프라인 실행 시작됨 (백그라운드)\"")
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
            summary = "공고 임베딩 생성",
            description = """
                    임베딩이 아직 생성되지 않은 전체 공고에 대해 AI 서버를 호출하여 임베딩을 생성합니다.
                    백그라운드로 실행되며 미생성 건이 0이 될 때까지 반복 처리합니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "임베딩 생성 시작됨",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"공고 임베딩 생성 시작됨 (백그라운드)\"")
                    )
            )
    })
    ResponseEntity<String> generateEmbeddings();

    @Operation(
            summary = "사기업 매칭 점수 산출",
            description = """
                    신규/변경 사기업 공고에 대해 AI 서버를 호출하여 매칭 점수를 산출합니다.
                    백그라운드로 실행됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "점수 산출 시작됨",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"사기업 매칭 점수 산출 시작됨 (백그라운드)\"")
                    )
            )
    })
    ResponseEntity<String> scorePostings();

    @Operation(
            summary = "공기업 매칭 점수 산출",
            description = """
                    신규/변경 공기업 공고에 대해 AI 서버(/score/public)를 호출하여 매칭 점수를 산출합니다.
                    백그라운드로 실행됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "점수 산출 시작됨",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"공기업 매칭 점수 산출 시작됨 (백그라운드)\"")
                    )
            )
    })
    ResponseEntity<String> scorePublicPostings();

    @Operation(
            summary = "이력서 임베딩 생성",
            description = """
                    활성 이력서 중 ��베딩이 없는 이력서에 대해 AI 서버를 호출하여 임베딩을 생성합니다.
                    동기 ���행(완료 후 응답)합니다.
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
                    백그라운드로 실행됩니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202",
                    description = "수집 시작됨",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = "\"IT 뉴스 카드 수집 시작됨 (백그라운드)\"")
                    )
            )
    })
    ResponseEntity<String> collectTechCards();

    @Operation(
            summary = "[테스트] 알림 발송 테스트",
            description = """
                    기존 DB에 저장된 점수를 기반으로 임계값 이상 공고에 대해 알림을 발송합니다.
                    점수 산출은 하지 않으며, 알림 파이프라인(WebSocket·Slack·Discord)만 테스트합니다.
                    email 파라미터를 지정하면 해당 사용자에게만 발송하고, 생략하면 전체 대상입니다.
                    """,
            parameters = @io.swagger.v3.oas.annotations.Parameter(
                    name = "email",
                    description = "알림을 받을 특정 사용자 이메일 (생략 시 전체 발송)",
                    required = false
            )
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
    ResponseEntity<String> triggerNotifyTest(@io.swagger.v3.oas.annotations.Parameter(hidden = true) String email);

    @Operation(
            summary = "비동기 작업 상태 조회",
            description = """
                    백그라운드로 실행 중인 작업들의 현재 상태와 결과를 조회합니다.
                    상태값: RUNNING(실행 중), COMPLETED(완료), FAILED(실패).
                    한 번도 실행되지 않은 작업은 목록에 나타나지 않습니다.
                    """
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "작업 상태 목록",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {"daily-pipeline":{"status":"COMPLETED","result":"수집: 사기업 32건, 공기업 5건 | 분류: 10건 | 이력서임베딩: 완료 | 소요: 12345ms"}}
                                    """)
                    )
            )
    })
    ResponseEntity<Map<String, Map<String, String>>> getTaskStatus();
}
