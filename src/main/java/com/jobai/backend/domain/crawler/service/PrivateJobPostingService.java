package com.jobai.backend.domain.crawler.service;

import com.jobai.backend.domain.crawler.entity.PrivateJobPosting;
import com.jobai.backend.domain.crawler.model.JobRecord;
import com.jobai.backend.domain.crawler.repository.PrivateJobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PrivateJobPostingService {

    private final PrivateJobPostingRepository repository;

    /**
     * 한 회사의 수집 결과를 DB 에 반영.
     *  - 신규: insert / 기존: update (upsert)
     *  - 이번 수집에 없는 기존 공고: 마감 처리(is_closed)
     */
    @Transactional
    public void saveAll(String company, List<JobRecord> records) {
        LocalDateTime now = LocalDateTime.now();
        Set<String> seenJobIds = new HashSet<>();   // 이번에 본 source_job_id 모음

        // 1) upsert
        for (JobRecord r : records) {
            String sourceJobId = String.valueOf(r.getJobId());
            seenJobIds.add(sourceJobId);

            Optional<PrivateJobPosting> existing =
                    repository.findByCompanyAndSourceJobId(company, sourceJobId);

            if (existing.isPresent()) {
                // 기존 → 갱신
                existing.get().updateDetail(
                        str(r.getTitle()), str(r.get("location")), str(r.get("employee_type")),
                        str(r.get("job_category")), str(r.getDescription()), str(r.getApplyUrl()),
                        parseDeadline(r.get("deadline")), now);
                // save 안 해도 @Transactional 안에서 변경 감지로 자동 UPDATE
            } else {
                // 신규 → 추가
                PrivateJobPosting entity = PrivateJobPosting.builder()
                        .company(company)
                        .sourceJobId(sourceJobId)
                        .title(str(r.getTitle()))
                        .location(str(r.get("location")))
                        .employmentType(str(r.get("employee_type")))
                        .jobCategory(str(r.get("job_category")))
                        .description(str(r.getDescription()))
                        .applyUrl(str(r.getApplyUrl()))
                        .deadline(parseDeadline(r.get("deadline")))
                        .isClosed(false)
                        .lastSeenAt(now)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                repository.save(entity);
            }
        }

        // 2) 마감 처리: DB 엔 있는데 이번 수집에 없는 공고
        List<PrivateJobPosting> dbAll = repository.findAllByCompany(company);
        for (PrivateJobPosting p : dbAll) {
            if (!seenJobIds.contains(p.getSourceJobId()) && !p.isClosed()) {
                p.markClosed(now);   // 이번에 안 보임 → 마감
            }
        }
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