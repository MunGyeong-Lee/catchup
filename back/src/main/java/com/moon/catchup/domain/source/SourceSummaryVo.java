package com.moon.catchup.domain.source;


import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 소스 목록에 표시하는 소스 유형별 요약 정보를 담는다. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class SourceSummaryVo {
    private final long id;
    private final SourceType type;
    private final String name;
    private final String historyType;
    private final YearMonth startMonth;
    private final LocalDate issuedOn;
    private final Instant createdAt;

    /**
     * 저장된 소스 이름과 유형별 요약 필드를 엔티티에서 읽는다.
     *
     * @param source 변환하거나 저장할 소스
     * @return 목록 표시용 소스 요약
     */
    public static SourceSummaryVo from(SourceEntity source) {
        Map<String, Object> fields = SourceDetailsVo.from(source).getFields();
        return new SourceSummaryVo(source.getId(), source.getType(), source.getName(),
                Objects.toString(fields.get("historyType"), null), (YearMonth) fields.get("startMonth"),
                (LocalDate) fields.get("issuedOn"), source.getCreatedAt());
    }
}
