package com.jobai.backend.domain.publicInstitution.dto;

import java.util.List;

/**
 * 2. 채용공고 상세 조회용 API 응답 DTO
 */
public record PublicJobDetailResponse(
        int resultCode,
        String resultMsg,
        DetailItem result // 상세 정보는 단건 Object 형태
) {
    public record DetailItem(
            Long recrutPblntSn,
            String recrutPbancTtl,
            String instNm,
            String recrutSeNm,
            String workRgnNmLst,
            String pbancBgngYmd,
            String pbancEndYmd,
            String srcUrl,
            String scrnprcdrMthdExpln,
            String ncsCdNmLst,
            String hireTypeNmLst,
            String aplyQlfcCn,      // 지원 자격 내용
            String disqlfcRsn,     // 결격 사유 내용
            List<FileItem> files    // 첨부파일 목록 (직무기술서 포함 구역)
    ) {}

    public record FileItem(
            Long recrutAtchFileNo,
            int sortNo,
            String atchFileNm,
            String atchFileType,
            String url
    ) {}
}