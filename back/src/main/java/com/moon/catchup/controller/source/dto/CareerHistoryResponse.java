package com.moon.catchup.controller.source.dto;

import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moon.catchup.domain.source.SourceEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 이력 소스의 상세 정보를 전달한다. */
@Getter
@Schema(description = "이력 소스 상세 응답")
public final class CareerHistoryResponse extends SourceResponse {
    @Schema(description = "소속명")
    private final String organization;
    @Schema(description = "이력 구분")
    private final String historyType;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "시작 연월 (YYYY-MM)", type = "string")
    private final YearMonth startMonth;
    @Schema(description = "직무·전공·과정명")
    private final String role;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "종료 연월 (YYYY-MM)", type = "string")
    private final YearMonth endMonth;
    @Schema(description = "진행 중 여부")
    private final Boolean ongoing;
    @Schema(description = "상세 설명")
    private final String description;

    /**
     * 저장된 이력 입력값을 응답 필드에 담는다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     */
    public CareerHistoryResponse(SourceEditVo edit) {
        super(edit);
        Map<String, Object> fields = edit.getDetails().getFields();
        this.organization = Objects.toString(fields.get("organization"), null);
        this.historyType = Objects.toString(fields.get("historyType"), null);
        this.startMonth = (YearMonth) fields.get("startMonth");
        this.role = Objects.toString(fields.get("role"), null);
        this.endMonth = (YearMonth) fields.get("endMonth");
        this.ongoing = (Boolean) fields.get("ongoing");
        this.description = Objects.toString(fields.get("description"), null);
    }
}
