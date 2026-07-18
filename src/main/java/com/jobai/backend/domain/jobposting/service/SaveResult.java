package com.jobai.backend.domain.jobposting.service;

import com.jobai.backend.domain.jobposting.entity.PrivateJobPosting;

import java.util.List;
import java.util.Objects;

/**
 * {@link PrivateJobPostingService#saveAll} 의 결과. 이번 수집 반영으로 무엇이 바뀌었는지 담는다.
 *
 * <ul>
 *   <li>{@link #inserted} — 이번에 새로 추가된(INSERT) 공고. "신규만 export" 의 대상이 된다.</li>
 *   <li>{@link #updatedCount} — 내용이 바뀌어 UPDATE 된 기존 공고 수(마감 부활 포함).</li>
 *   <li>{@link #closedCount} — 이번 수집에 없어 마감 처리된 공고 수.</li>
 * </ul>
 *
 * <p>inserted 는 엔티티 목록이라, 호출하는 쪽에서 source_job_id/title/description 등을 바로 꺼내 쓸 수 있다.
 */
public class SaveResult {

    private final List<PrivateJobPosting> inserted;
    private final int updatedCount;
    private final int closedCount;

    public SaveResult(List<PrivateJobPosting> inserted, int updatedCount, int closedCount) {
        // 방어적 복사 + 불변화: 호출자가 받은 리스트를 바꿔도 결과(개수/export 대상)가 오염되지 않게.
        this.inserted = List.copyOf(Objects.requireNonNull(inserted, "inserted"));
        this.updatedCount = updatedCount;
        this.closedCount = closedCount;
    }

    /** 수집 0건 등으로 아무 변화가 없을 때. */
    public static SaveResult empty() {
        return new SaveResult(List.of(), 0, 0);
    }

    public List<PrivateJobPosting> getInserted() { return inserted; }

    public int getInsertedCount() { return inserted.size(); }

    public int getUpdatedCount() { return updatedCount; }

    public int getClosedCount() { return closedCount; }

    @Override
    public String toString() {
        return "SaveResult{inserted=" + inserted.size()
                + ", updated=" + updatedCount
                + ", closed=" + closedCount + "}";
    }
}