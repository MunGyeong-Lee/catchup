package com.moon.catchup.careeritem.dto;


import com.moon.catchup.careeritem.domain.CareerItemSummaryVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 이력, 경험, 자격증의 목록 응답 형태를 정의한다. */
@Schema(description = "자료 목록의 유형별 요약",
        oneOf = {ItemSummaryResponse.SummaryResponse.class, ItemSummaryResponse.HistorySummaryResponse.class,
                ItemSummaryResponse.CertificationSummaryResponse.class})
public sealed interface ItemSummaryResponse {
    /** 자료 유형에 맞는 목록 응답으로 요약 도메인 객체를 변환한다. */
    public static ItemSummaryResponse from(CareerItemSummaryVo item) {
        String careerItemType = item.getType().name();
        return switch (item.getType()) {
            case CAREER_HISTORY -> new HistorySummaryResponse(item.getId(), careerItemType, item.getDisplayName(),
                    item.getHistoryType(), item.getStartMonth());
            case EXPERIENCE -> new SummaryResponse(item.getId(), careerItemType, item.getDisplayName());
            case CERTIFICATION -> new CertificationSummaryResponse(item.getId(), careerItemType, item.getDisplayName(),
                    item.getIssuedOn());
        };
    }

    @Schema(description = "경험 요약")
    @Getter
    @AllArgsConstructor
    public final class SummaryResponse implements ItemSummaryResponse {
        @Schema(description = "자료 식별자")
        private final long careerItemId;
        @Schema(description = "자료 유형")
        private final String careerItemType;
        @Schema(description = "목록 표시 이름")
        private final String displayName;
    }

    @Schema(description = "이력 요약")
    @Getter
    @AllArgsConstructor
    public final class HistorySummaryResponse implements ItemSummaryResponse {
        @Schema(description = "자료 식별자")
        private final long careerItemId;
        @Schema(description = "자료 유형")
        private final String careerItemType;
        @Schema(description = "목록 표시 이름")
        private final String displayName;
        @Schema(description = "이력 구분")
        private final String historyType;
        @Schema(description = "시작 연월")
        private final String startMonth;
    }

    @Schema(description = "자격증 요약")
    @Getter
    @AllArgsConstructor
    public final class CertificationSummaryResponse implements ItemSummaryResponse {
        @Schema(description = "자료 식별자")
        private final long careerItemId;
        @Schema(description = "자료 유형")
        private final String careerItemType;
        @Schema(description = "목록 표시 이름")
        private final String displayName;
        @Schema(description = "자격증 취득일")
        private final String issuedOn;
    }
}
