package com.jobai.backend.domain.publicInstitution.dto;

import java.util.List;

public record PublicJobListResponse(int resultCode,
                                    String resultMsg,
                                    int totalCount,
                                    List<Item> result // response/body 계층 없이 최상위의 result 배열을 바로 매핑
) {
    public record Item(
            Long recrutPblntSn,      // 채용공고 일련번호 (고유 ID)
            String recrutPbancTtl,   // 채용공고 제목
            String instNm,           // 기관명
            String recrutSeNm,       // 채용구분명 (신입/경력)
            String workRgnNmLst,     // 근무지역명 리스트 (예: 광주, 울산, 서울)
            String pbancBgngYmd,     // 공고시작일자 (YYYYMMDD)
            String pbancEndYmd       // 공고종료일자 (YYYYMMDD)
    ) {}
}
