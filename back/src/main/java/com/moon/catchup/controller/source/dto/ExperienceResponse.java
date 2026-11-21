package com.moon.catchup.controller.source.dto;

import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moon.catchup.domain.source.SourceEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 경험 소스의 상세 정보를 전달한다. */
@Getter
@Schema(description = "경험 소스 상세 응답")
public final class ExperienceResponse extends SourceResponse {
    @Schema(description = "경험 제목")
    private final String title;
    @Schema(description = "기여")
    private final String contribution;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "시작 연월 (YYYY-MM)", type = "string")
    private final YearMonth startMonth;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "종료 연월 (YYYY-MM)", type = "string")
    private final YearMonth endMonth;
    @Schema(description = "소속 또는 팀")
    private final String organization;
    @Schema(description = "결과와 성과")
    private final String outcome;
    @Schema(description = "사용 기술")
    private final String technologies;
    @Schema(description = "관련 링크")
    private final String relatedLink;

    /**
     * 저장된 경험 입력값을 응답 필드에 담는다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     */
    public ExperienceResponse(SourceEditVo edit) {
        super(edit);
        Map<String, Object> fields = edit.getDetails().getFields();
        this.title = Objects.toString(fields.get("title"), null);
        this.contribution = Objects.toString(fields.get("contribution"), null);
        this.startMonth = (YearMonth) fields.get("startMonth");
        this.endMonth = (YearMonth) fields.get("endMonth");
        this.organization = Objects.toString(fields.get("organization"), null);
        this.outcome = Objects.toString(fields.get("outcome"), null);
        this.technologies = Objects.toString(fields.get("technologies"), null);
        this.relatedLink = Objects.toString(fields.get("relatedLink"), null);
    }
}
