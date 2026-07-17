package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.classify.EmploymentType;
import com.jobai.backend.domain.crawler.classify.ExperienceLevel;
import com.jobai.backend.domain.crawler.classify.JobCategory;
import com.jobai.backend.domain.crawler.classify.JobClassifier;
import com.jobai.backend.domain.crawler.classify.JobClassifier.ClassificationResult;
import com.jobai.backend.domain.crawler.classify.Region;
import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateJobPostingService {

    private final PrivateJobPostingRepository repository;
    private final JobClassifier jobClassifier;

    /**
     * 한 회사의 수집 결과를 DB 에 반영하고, 무엇이 바뀌었는지 {@link SaveResult} 로 돌려준다.
     * <ul>
     *   <li>신규(키 없음): insert → inserted 목록에 모음("신규만 export" 대상)</li>
     *   <li>기존(키 있음): 내용이 실제로 바뀐 경우(또는 마감 부활)에만 update, 아니면 lastSeenAt 만 갱신</li>
     *   <li>이번 수집에 없는 기존 공고: 마감 처리(is_closed)</li>
     * </ul>
     *
     * <p>기존 공고는 회사 단위로 <b>한 번에</b> 조회해 Map 으로 들고 upsert·마감에 모두 재사용한다.
     * (공고마다 개별 조회하던 N+1 제거.)
     */
    @Transactional
    public SaveResult saveAll(String company, List<JobRecord> records) {
        if (records == null || records.isEmpty()) {
            return SaveResult.empty();   // 수집 실패/0건 시 대량 오마감 방지
        }
        LocalDateTime now = LocalDateTime.now();

        // 0) 회사 전체를 한 번에 조회 → source_job_id 기준 Map (N+1 제거, upsert·마감 공용)
        Map<String, PrivateJobPosting> existingMap = new LinkedHashMap<>();
        for (PrivateJobPosting p : repository.findAllByCompany(company)) {
            existingMap.put(p.getSourceJobId(), p);
        }

        List<PrivateJobPosting> inserted = new ArrayList<>();
        int updatedCount = 0;
        Set<String> seenJobIds = new HashSet<>();   // 이번에 본 source_job_id

        // 1) upsert + 분류
        for (JobRecord r : records) {
            Object rawJobId = r.getJobId();
            if (rawJobId == null || String.valueOf(rawJobId).isBlank()) {
                throw new IllegalArgumentException("upsert 키인 job_id 가 없습니다");
            }
            String sourceJobId = String.valueOf(rawJobId).trim();
            // 같은 배치에 중복 job_id 가 오면 두 번째부터 건너뛴다.
            // (안 거르면 신규 처리가 두 번 일어나 유니크 키 충돌 → 트랜잭션 전체 롤백)
            if (!seenJobIds.add(sourceJobId)) {
                log.warn("[{}] 중복 job_id 무시: {}", company, sourceJobId);
                continue;
            }

            String title = str(r.getTitle());
            String location = str(r.get("location"));
            String rawEmpType = str(r.get("employee_type"));
            EmploymentType normalizedEmp = EmploymentType.fromRawValue(rawEmpType);
            String employmentType = normalizedEmp != null ? normalizedEmp.getLabel() : null;
            String description = str(r.getDescription());
            String applyUrl = str(r.getApplyUrl());
            LocalDate deadline = parseDeadline(r.get("deadline"));

            PrivateJobPosting existing = existingMap.get(sourceJobId);

            if (existing != null) {
                // 기존: 내용이 같고 + 이미 열려있으면(마감 아님) UPDATE 스킵, lastSeenAt 만 갱신.
                // 마감(isClosed=true)됐던 공고가 다시 보이면 내용 같아도 되살려야 하므로 update.
                boolean sameContent = existing.hasSameContent(
                        title, location, employmentType, description, applyUrl, deadline);
                if (sameContent && !existing.isClosed()) {
                    existing.touch(now);
                } else {
                    existing.updateDetail(title, location, employmentType,
                             description, applyUrl, deadline, now);
                    updatedCount++;
                }
                // save 안 해도 @Transactional 변경 감지로 자동 UPDATE
            } else {
                // 신규 → 추가
                PrivateJobPosting entity = PrivateJobPosting.builder()
                        .company(company)
                        .sourceJobId(sourceJobId)
                        .title(title)
                        .location(location)
                        .employmentType(employmentType)
                        .description(description)
                        .applyUrl(applyUrl)
                        .deadline(deadline)
                        .isClosed(false)
                        .lastSeenAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                repository.save(entity);
                inserted.add(entity);
                // 방금 저장한 신규도 Map 에 반영 → 혹시 같은 job_id 가 또 오면 기존으로 처리(INSERT 중복 차단)
                existingMap.put(sourceJobId, entity);
            }
        }

        // 2) 마감 처리: DB 엔 있는데 이번 수집에 없는 공고 (이미 가진 Map 재사용 → 추가 조회 없음)
        int closedCount = 0;
        for (PrivateJobPosting p : existingMap.values()) {
            if (!seenJobIds.contains(p.getSourceJobId()) && !p.isClosed()) {
                p.markClosed(now);
                closedCount++;
            }
        }

        SaveResult result = new SaveResult(inserted, updatedCount, closedCount);
        log.info("[{}] 저장 완료 — {}", company, result);
        return result;
    }

    // null 안전 문자열 변환 (리스트면 첫 요소 등은 우선 단순화)
    private String str(Object v) {
        if (v == null) return null;
        if (v instanceof List<?> list) {
            if (list.isEmpty()) return null;
            Object first = list.get(0);
            if (first == null) return null;
            String result = String.valueOf(first);
            return "null".equals(result) ? null : result;
        }
        String result = String.valueOf(v);
        return "null".equals(result) ? null : result;
    }

    // 마감일 문자열 → LocalDate. 형식이 회사마다 달라 실패하면 null.
    private LocalDate parseDeadline(Object v) {
        if (v instanceof List<?> list) {           // str() 과 동일하게
            v = list.isEmpty() ? null : list.get(0);
        }
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || s.startsWith("9999")) return null;   // 상시채용 등
        // 흔한 형식들 시도
        String[] patterns = {"yyyy-MM-dd", "yyyy.MM.dd", "yyyy-MM-dd'T'HH:mm:ss", "yyyy.MM.dd HH:mm:ss"};
        for (String p : patterns) {
            try {
                // 시간 포함 형식은 앞 10자만 잘라 날짜로
                String datePart = s.length() >= 10 ? s.substring(0, 10) : s;
                return LocalDate.parse(datePart,
                        DateTimeFormatter.ofPattern(p.length() > 10 ? p.substring(0, 10) : p));
            } catch (Exception ignore) {
                // 다음 패턴 시도
            }
        }
        return null;   // 다 실패하면 null (저장은 되게)
    }

    /** 분류 결과(jobCategory)가 갱신된 엔티티들을 저장한다. */
    @Transactional
    public void saveClassified(List<PrivateJobPosting> postings) {
        repository.saveAll(postings);
    }

    /**
     * 아직 분류 안 된(jobCategory=null) 공고들을 일괄 분류한다.
     * 페이지 단위로 끊어 읽고, 각 페이지를 LLM 으로 분류해 jobCategory 를 채운다.
     *
     * @param batchPageSize 한 번에 읽어 분류할 공고 수
     * @return 분류한 총 공고 수
     */
    // @Transactional 없음 — LLM 호출이 DB 트랜잭션을 잡지 않게
    public int classifyUnclassified(int batchPageSize) {
        int total = 0;
        long lastId = 0L;
        int consecutiveFailures = 0;
        Pageable pageable = PageRequest.of(0, batchPageSize, Sort.by("id").ascending());

        while (true) {
            Page<PrivateJobPosting> page =
                    repository.findNeedsClassification(VALID_LABELS, lastId, pageable);
            List<PrivateJobPosting> batch = page.getContent();
            if (batch.isEmpty()) break;

            lastId = batch.get(batch.size() - 1).getId();

            List<String> titles = batch.stream()
                    .map(PrivateJobPosting::getTitle)
                    .toList();
            List<ClassificationResult> results = jobClassifier.classify(titles);

            int classifiedThisRound = 0;
            for (int i = 0; i < batch.size(); i++) {
                ClassificationResult result = results.get(i);
                if (result == null) continue;

                PrivateJobPosting posting = batch.get(i);
                posting.classifyAs(result.category());

                if (!isNormalizedEmploymentType(posting.getEmploymentType())) {
                    posting.setNormalizedEmploymentType(result.employmentType());
                }
                posting.setExperienceLevel(result.experienceLevel());
                classifiedThisRound++;
            }
            repository.saveAll(batch);
            total += classifiedThisRound;

            if (classifiedThisRound == 0) {
                consecutiveFailures++;
                log.warn("[분류] 이번 배치 전부 실패 — 건너뜀 ({}/3). 누적 {}", consecutiveFailures, total);
                if (consecutiveFailures >= 3) {
                    log.error("[분류] 연속 3회 실패 — 중단. 누적 {}", total);
                    break;
                }
            } else {
                consecutiveFailures = 0;
            }
            log.info("[분류] {}건 처리 (누적 {})", classifiedThisRound, total);
        }
        return total;
    }

    // 재분류 대상 판별 기준: jobCategory 가 이 목록에 없으면(= null 또는 회사원본명) 재분류.
    // JobCategory enum 에서 라벨을 추출해 택소노미 변경 시 자동 반영되도록 한다.
    // "미분류"를 포함하는 이유: 진짜 판단불가(LLM 이 "미분류" 응답)는 최종 상태로 두어 무한 재분류를 막는다.
    // 호출 실패분은 "미분류"가 아니라 null 로 남으므로(JobClassifier 가 실패 시 null 반환), 다음 실행에 자동 재시도된다.
    private static final List<String> VALID_LABELS = Arrays.stream(JobCategory.values())
            .map(JobCategory::getLabel)
            .toList();

    private static final List<String> VALID_EMP_LABELS = Arrays.stream(EmploymentType.values())
            .map(EmploymentType::getLabel)
            .toList();

    private boolean isNormalizedEmploymentType(String empType) {
        return empType != null && VALID_EMP_LABELS.contains(empType);
    }

    /**
     * jobCategory는 분류됐지만 employmentType/experienceLevel이 null인 공고를 LLM으로 재분류한다.
     */
    // @Transactional 없음 — LLM 호출이 DB 트랜잭션을 잡지 않게
    public int classifyMissingEmploymentTypes(int batchPageSize) {
        int total = 0;
        long lastId = 0L;
        int consecutiveFailures = 0;
        Pageable pageable = PageRequest.of(0, batchPageSize, Sort.by("id").ascending());

        while (true) {
            Page<PrivateJobPosting> page =
                    repository.findNeedsEmploymentTypeClassification(VALID_LABELS, lastId, pageable);
            List<PrivateJobPosting> batch = page.getContent();
            if (batch.isEmpty()) break;

            lastId = batch.get(batch.size() - 1).getId();

            List<String> titles = batch.stream()
                    .map(PrivateJobPosting::getTitle)
                    .toList();
            List<ClassificationResult> results = jobClassifier.classify(titles);

            int classified = 0;
            for (int i = 0; i < batch.size(); i++) {
                ClassificationResult result = results.get(i);
                if (result == null) continue;

                PrivateJobPosting posting = batch.get(i);
                if (!isNormalizedEmploymentType(posting.getEmploymentType())) {
                    posting.setNormalizedEmploymentType(result.employmentType());
                }
                posting.setExperienceLevel(result.experienceLevel());
                classified++;
            }
            repository.saveAll(batch);
            total += classified;

            if (classified == 0) {
                consecutiveFailures++;
                log.warn("[고용형태/경력 분류] 이번 배치 전부 실패 — 건너뜀 ({}/3). 누적 {}", consecutiveFailures, total);
                if (consecutiveFailures >= 3) {
                    log.error("[고용형태/경력 분류] 연속 3회 실패 — 중단. 누적 {}", total);
                    break;
                }
            } else {
                consecutiveFailures = 0;
            }
            log.info("[고용형태/경력 분류] {}건 처리 (누적 {})", classified, total);
        }
        return total;
    }

    private static final List<String> VALID_REGION_LABELS = Arrays.stream(Region.values())
            .map(Region::getLabel)
            .toList();

    /**
     * location이 아직 정규화되지 않은 공고를 LLM으로 일괄 지역 분류하여 location에 덮어쓴다.
     *
     * @param batchPageSize 한 번에 읽어 분류할 공고 수
     * @return 분류한 총 공고 수
     */
    public int classifyMissingRegions(int batchPageSize) {
        int total = 0;
        long lastId = 0L;
        int consecutiveFailures = 0;
        Pageable pageable = PageRequest.of(0, batchPageSize, Sort.by("id").ascending());

        while (true) {
            Page<PrivateJobPosting> page = repository.findNeedsRegionClassification(VALID_REGION_LABELS, lastId, pageable);
            List<PrivateJobPosting> batch = page.getContent();
            if (batch.isEmpty()) break;

            lastId = batch.get(batch.size() - 1).getId();

            List<String> locations = batch.stream()
                    .map(PrivateJobPosting::getLocation)
                    .toList();
            List<Region> results = jobClassifier.classifyRegions(locations);

            int classified = 0;
            for (int i = 0; i < batch.size(); i++) {
                Region region = results.get(i);
                if (region == null) continue;

                batch.get(i).setNormalizedLocation(region);
                classified++;
            }
            repository.saveAll(batch);
            total += classified;

            if (classified == 0) {
                consecutiveFailures++;
                log.warn("[지역 분류] 이번 배치 전부 실패 — 건너뜀 ({}/3). 누적 {}", consecutiveFailures, total);
                if (consecutiveFailures >= 3) {
                    log.error("[지역 분류] 연속 3회 실패 — 중단. 누적 {}", total);
                    break;
                }
            } else {
                consecutiveFailures = 0;
            }
            log.info("[지역 분류] {}건 처리 (누적 {})", classified, total);
        }
        return total;
    }
}