package com.moon.catchup.controller.source.dto;

import java.time.YearMonth;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 경험 등록의 유형별 입력을 받는다. */
@Getter
@Schema(description = "경험 소스 등록 요청")
public final class ExperienceRegisterRequest extends SourceRegisterRequest {
    /** 경험 제목 */
    @JsonProperty
    @Schema(description = "경험 제목", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object title;

    /** 기여 */
    @JsonProperty
    @Schema(description = "기여", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object contribution;

    /** 시작 연월 (YYYY-MM, 시간대 없음) */
    @JsonProperty
    @JsonFormat(pattern = "uuuu-MM", lenient = OptBoolean.FALSE)
    @Schema(description = "시작 연월 (YYYY-MM, 시간대 없음)", type = "string")
    private YearMonth startMonth;

    /** 종료 연월 (YYYY-MM, 시간대 없음) */
    @JsonProperty
    @JsonFormat(pattern = "uuuu-MM", lenient = OptBoolean.FALSE)
    @Schema(description = "종료 연월 (YYYY-MM, 시간대 없음)", type = "string")
    private YearMonth endMonth;

    /** 소속 또는 팀 */
    @JsonProperty
    @Schema(description = "소속 또는 팀", type = "string")
    private Object organization;

    /** 결과와 성과 */
    @JsonProperty
    @Schema(description = "결과와 성과", type = "string")
    private Object outcome;

    /** 사용 기술 */
    @JsonProperty
    @Schema(description = "사용 기술", type = "string")
    private Object technologies;

    /** 관련 링크 */
    @JsonProperty
    @Schema(description = "관련 링크", type = "string")
    private Object relatedLink;

    /**
     * 경험 등록 필드를 기존 상세 저장 키에 연결한다.
     *
     * @param details 요청 검증에 사용할 상세 입력
     */
    @Override
    protected void addDetails(Map<String, Object> details) {
        details.put("title", this.title);
        details.put("contribution", this.contribution);
        details.put("startMonth", this.startMonth);
        details.put("endMonth", this.endMonth);
        details.put("organization", this.organization);
        details.put("outcome", this.outcome);
        details.put("technologies", this.technologies);
        details.put("relatedLink", this.relatedLink);
    }
}
