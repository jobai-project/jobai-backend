package com.jobai.backend.domain.clawler.engine;

import com.jobai.backend.domain.clawler.model.JobRecord;
import com.jobai.backend.domain.clawler.spec.CrawlSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1층(결정적) 점검 — 비용 0.
 * 통과하면 빈 리스트, 문제 있으면 사유 목록.
 */
public final class RecordChecker {

    private static final List<String> DEFAULT_REQUIRED = List.of("job_id", "title", "updated_at");

    private RecordChecker() {}

    public static List<String> check(List<JobRecord> records, CrawlSpec spec) {
        List<String> required = (spec != null && spec.getRequired() != null && !spec.getRequired().isEmpty())
                ? spec.getRequired() : DEFAULT_REQUIRED;

        List<String> issues = new ArrayList<>();
        int n = records.size();
        if (n == 0) {
            issues.add("0건 수집 — url / 경로 / 필터 확인 필요");
            return issues;
        }
        for (String f : required) {
            int missing = 0;
            for (JobRecord r : records) {
                if (!r.has(f)) missing++;
            }
            if (missing > 0) {
                issues.add("필수필드 '" + f + "' 비어있음: " + missing + "/" + n);
            }
        }
        // job_id 중복
        Set<String> ids = new HashSet<>();
        for (JobRecord r : records) {
            ids.add(String.valueOf(r.getJobId()));
        }
        if (ids.size() != n) {
            issues.add("job_id 중복: 고유 " + ids.size() + " / 전체 " + n);
        }
        return issues;
    }
}

