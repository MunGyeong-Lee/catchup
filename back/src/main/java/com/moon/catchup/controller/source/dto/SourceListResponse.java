package com.moon.catchup.controller.source.dto;


import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moon.catchup.domain.source.SourceSummaryVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 자료보관에 표시되는 소스 유형별 목록 응답 형태를 정의한다. */
@Schema(description = "소스 목록의 유형별 요약",
        oneOf = {SourceListResponse.ExperienceListResponse.class, SourceListResponse.HistoryListResponse.class,
                SourceListResponse.CertificationListResponse.class, SourceListResponse.TextSourceListResponse.class})
public sealed interface SourceListResponse {
    /**
     * 소스 유형에 맞는 목록 응답으로 요약 도메인 객체를 변환한다.
     *
     * @param source 목록 응답의 원본 소스 요약
     * @return 목록 표시용 소스 응답
     */
    public static SourceListResponse from(SourceSummaryVo source) {
        String sourceType = source.getType().name();
        return switch (source.getType()) {
            case CAREER_HISTORY -> new HistoryListResponse(source.getId(), sourceType, source.getName(),
                    source.getCreatedAt(), source.isHasAttachments(), source.getHistoryType(), source.getStartMonth());
            case EXPERIENCE -> new ExperienceListResponse(source.getId(), sourceType, source.getName(),
                    source.getCreatedAt(), source.isHasAttachments());
            case CERTIFICATION -> new CertificationListResponse(source.getId(), sourceType, source.getName(),
                    source.getCreatedAt(), source.isHasAttachments(), source.getIssuedOn());
            case TARGET_COMPANY, WRITING_STYLE, OTHER_TEXT ->
                    new TextSourceListResponse(source.getId(), sourceType, source.getName(),
                            source.getCreatedAt(), source.isHasAttachments());
        };
    }

    /** 경험 목록에 표시할 공통 식별자와 이름을 전달한다. */
    @Schema(description = "경험 요약")
    @Getter
    @AllArgsConstructor
    public final class ExperienceListResponse implements SourceListResponse {
        @Schema(description = "소스 식별자")
        private final long sourceId;
        @Schema(description = "소스 유형", allowableValues = {"EXPERIENCE"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private final String sourceType;
        @Schema(description = "저장된 소스 이름")
        private final String name;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
        private final Instant createdAt;
        @Schema(description = "저장된 첨부파일이 하나 이상 있는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        private final boolean hasAttachments;
    }

    /** 지원회사, 문체 및 어투, 기타의 목록에 표시할 공통 요약을 전달한다. */
    @Schema(description = "지원회사, 문체 및 어투, 기타의 공통 요약")
    @Getter
    @AllArgsConstructor
    public final class TextSourceListResponse implements SourceListResponse {
        @Schema(description = "소스 식별자")
        private final long sourceId;
        @Schema(description = "소스 유형", allowableValues = {"TARGET_COMPANY", "WRITING_STYLE", "OTHER_TEXT"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        private final String sourceType;
        @Schema(description = "저장된 소스 이름")
        private final String name;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
        private final Instant createdAt;
        @Schema(description = "저장된 첨부파일이 하나 이상 있는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        private final boolean hasAttachments;
    }

    /** 이력 목록에 표시할 구분과 시작 연월을 전달한다. */
    @Schema(description = "이력 요약")
    @Getter
    @AllArgsConstructor
    public final class HistoryListResponse implements SourceListResponse {
        @Schema(description = "소스 식별자")
        private final long sourceId;
        @Schema(description = "소스 유형", allowableValues = {"CAREER_HISTORY"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private final String sourceType;
        @Schema(description = "저장된 소스 이름")
        private final String name;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
        private final Instant createdAt;
        @Schema(description = "저장된 첨부파일이 하나 이상 있는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        private final boolean hasAttachments;
        @Schema(description = "이력 구분")
        private final String historyType;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "시작 연월 (YYYY-MM)", type = "string")
        private final YearMonth startMonth;
    }

    /** 자격증 목록에 표시할 취득일을 전달한다. */
    @Schema(description = "자격증 요약")
    @Getter
    @AllArgsConstructor
    public final class CertificationListResponse implements SourceListResponse {
        @Schema(description = "소스 식별자")
        private final long sourceId;
        @Schema(description = "소스 유형", allowableValues = {"CERTIFICATION"}, requiredMode = Schema.RequiredMode.REQUIRED)
        private final String sourceType;
        @Schema(description = "저장된 소스 이름")
        private final String name;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
        private final Instant createdAt;
        @Schema(description = "저장된 첨부파일이 하나 이상 있는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        private final boolean hasAttachments;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "자격증 취득일 (YYYY-MM-DD)", type = "string", format = "date")
        private final LocalDate issuedOn;
    }
}
