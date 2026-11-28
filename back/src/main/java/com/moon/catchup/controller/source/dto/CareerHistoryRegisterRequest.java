package com.moon.catchup.controller.source.dto;

import java.time.YearMonth;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 이력 등록의 유형별 입력을 받는다. */
@Getter
@Schema(description = "이력 소스 등록 요청")
public final class CareerHistoryRegisterRequest extends SourceRegisterRequest {
    /** 소속명 */
    @JsonProperty
    @Schema(description = "소속명", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object organization;

    /** 이력 구분 */
    @JsonProperty
    @Schema(description = "이력 구분", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object historyType;

    /** 시작 연월 (YYYY-MM, 시간대 없음) */
    @JsonProperty
    @JsonFormat(pattern = "uuuu-MM", lenient = OptBoolean.FALSE)
    @Schema(description = "시작 연월 (YYYY-MM, 시간대 없음)", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private YearMonth startMonth;

    /** 직무·전공·과정명 */
    @JsonProperty
    @Schema(description = "직무·전공·과정명", type = "string")
    private Object role;

    /** 종료 연월 (YYYY-MM, 시간대 없음) */
    @JsonProperty
    @JsonFormat(pattern = "uuuu-MM", lenient = OptBoolean.FALSE)
    @Schema(description = "종료 연월 (YYYY-MM, 시간대 없음)", type = "string")
    private YearMonth endMonth;

    /** 진행 중 여부 */
    @JsonProperty
    @Schema(description = "진행 중 여부", type = "boolean")
    private Object ongoing;

    /** 상세 설명 */
    @JsonProperty
    @Schema(description = "상세 설명", type = "string")
    private Object description;

    /**
     * 이력 등록 필드를 기존 상세 저장 키에 연결한다.
     *
     * @param details 요청 검증에 사용할 상세 입력
     */
    @Override
    protected void addDetails(Map<String, Object> details) {
        details.put("organization", this.organization);
        details.put("historyType", this.historyType);
        details.put("startMonth", this.startMonth);
        details.put("role", this.role);
        details.put("endMonth", this.endMonth);
        details.put("ongoing", this.ongoing);
        details.put("description", this.description);
    }
}
