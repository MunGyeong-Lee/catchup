package com.moon.catchup.domain.source;


import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 소스 목록에 표시하는 소스 유형별 요약 정보를 담는다. */
@Getter
@EqualsAndHashCode
public final class SourceSummaryVo {
    private final long id;
    private final SourceType type;
    private final String name;
    private final String historyType;
    private final YearMonth startMonth;
    private final LocalDate issuedOn;
    private final Instant createdAt;
    private final boolean hasAttachments;

    /**
     * 저장된 이름과 유형별 요약 필드를 초기화한다.
     *
     * @param source 목록에 표시할 소스 엔티티
     * @param hasAttachments 저장된 첨부 메타데이터가 있는지 여부
     */
    private SourceSummaryVo(SourceEntity source, boolean hasAttachments) {
        Map<String, Object> fields = SourceDetailsVo.from(source).getFields();
        this.id = source.getId();
        this.type = source.getType();
        this.name = source.getName();
        this.historyType = Objects.toString(fields.get("historyType"), null);
        this.startMonth = (YearMonth) fields.get("startMonth");
        this.issuedOn = (LocalDate) fields.get("issuedOn");
        this.createdAt = source.getCreatedAt();
        this.hasAttachments = hasAttachments;
    }

    /**
     * 저장된 소스 이름과 유형별 요약 필드를 엔티티에서 읽는다.
     *
     * @param source 목록에 표시할 소스 엔티티
     * @param hasAttachments 저장된 첨부 메타데이터가 있는지 여부
     * @return 목록 표시용 소스 요약
     */
    public static SourceSummaryVo from(SourceEntity source, boolean hasAttachments) {
        return new SourceSummaryVo(source, hasAttachments);
    }
}
