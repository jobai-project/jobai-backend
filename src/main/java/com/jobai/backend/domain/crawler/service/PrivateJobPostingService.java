package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
            seenJobIds.add(sourceJobId);

            String title = str(r.getTitle());
            String location = str(r.get("location"));
            String employmentType = str(r.get("employee_type"));
            String jobCategory = str(r.get("job_category"));
            String description = str(r.getDescription());
            String applyUrl = str(r.getApplyUrl());
            LocalDate deadline = parseDeadline(r.get("deadline"));

            PrivateJobPosting existing = existingMap.get(sourceJobId);

            if (existing != null) {
                // 기존: 내용이 같고 + 이미 열려있으면(마감 아님) UPDATE 스킵, lastSeenAt 만 갱신.
                // 마감(isClosed=true)됐던 공고가 다시 보이면 내용 같아도 되살려야 하므로 update.
                boolean sameContent = existing.hasSameContent(
                        title, location, employmentType, jobCategory, description, applyUrl, deadline);
                if (sameContent && !existing.isClosed()) {
                    existing.touch(now);
                } else {
                    existing.updateDetail(title, location, employmentType,
                            jobCategory, description, applyUrl, deadline, now);
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
                        .jobCategory(jobCategory)
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
            return list.isEmpty() ? null : String.valueOf(list.get(0));
        }
        return String.valueOf(v);
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
}