package com.moon.catchup.controller.source.dto;


import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moon.catchup.domain.source.SourceSummaryVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 이력, 경험, 자격증의 목록 응답 형태를 정의한다. */
@Schema(description = "소스 목록의 유형별 요약",
        oneOf = {SourceListResponse.ExperienceListResponse.class, SourceListResponse.HistoryListResponse.class,
                SourceListResponse.CertificationListResponse.class})
public sealed interface SourceListResponse {
    /**
     * 소스 유형에 맞는 목록 응답으로 요약 도메인 객체를 변환한다.
     *
     * @param item 목록 응답의 원본 소스 요약
     * @return 목록 표시용 소스 응답
     * @throws IllegalStateException 소스 유형의 전용 목록 응답이 아직 정의되지 않은 경우
     */
    public static SourceListResponse from(SourceSummaryVo item) {
        String sourceType = item.getType().name();
        return switch (item.getType()) {
            case CAREER_HISTORY -> new HistoryListResponse(item.getId(), sourceType, item.getDisplayName(),
                    item.getCreatedAt(), item.getHistoryType(), item.getStartMonth());
            case EXPERIENCE -> new ExperienceListResponse(item.getId(), sourceType, item.getDisplayName(),
                    item.getCreatedAt());
            case CERTIFICATION -> new CertificationListResponse(item.getId(), sourceType, item.getDisplayName(),
                    item.getCreatedAt(), item.getIssuedOn());
            case TARGET_COMPANY, WRITING_STYLE, OTHER_TEXT ->
                    throw new IllegalStateException("신규 소스 유형의 전용 목록 응답이 아직 정의되지 않았습니다.");
        };
    }

    /** 경험 목록에 표시할 공통 식별자와 이름을 전달한다. */
    @Schema(description = "경험 요약")
    @Getter
    @AllArgsConstructor
    public final class ExperienceListResponse implements SourceListResponse {
        @Schema(description = "소스 식별자")
        private final long sourceId;
        @Schema(description = "소스 유형")
        private final String sourceType;
        @Schema(description = "목록 표시 이름")
        private final String displayName;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
        private final Instant createdAt;
    }

    /** 이력 목록에 표시할 구분과 시작 연월을 전달한다. */
    @Schema(description = "이력 요약")
    @Getter
    @AllArgsConstructor
    public final class HistoryListResponse implements SourceListResponse {
        @Schema(description = "소스 식별자")
        private final long sourceId;
        @Schema(description = "소스 유형")
        private final String sourceType;
        @Schema(description = "목록 표시 이름")
        private final String displayName;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
        private final Instant createdAt;
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
        @Schema(description = "소스 유형")
        private final String sourceType;
        @Schema(description = "목록 표시 이름")
        private final String displayName;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
        private final Instant createdAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "자격증 취득일 (YYYY-MM-DD)", type = "string", format = "date")
        private final LocalDate issuedOn;
    }
}
