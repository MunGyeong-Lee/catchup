package com.moon.catchup.controller.source.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moon.catchup.domain.source.SourceEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 소스 상세 조회의 공통 필드와 첨부 정보를 전달한다. */
@Getter
@Schema(description = "소스 상세 조회 응답")
public abstract sealed class SourceResponse
        permits CareerHistoryResponse, ExperienceResponse, CertificationResponse {
    @Schema(description = "소스 식별자")
    private final long sourceId;
    @Schema(description = "현재 소스 유형")
    private final String sourceType;
    @Schema(description = "소스 이름")
    private final String name;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "DB 등록 시각 (UTC ISO 8601)", type = "string", format = "date-time")
    private final Instant createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "DB 최종 수정 시각 (UTC ISO 8601). 생성 직후에는 등록 시각과 동일합니다.",
            type = "string", format = "date-time")
    private final Instant updatedAt;
    @Schema(description = "현재 첨부 정보")
    private final List<AttachmentResponse> attachments;

    /**
     * 소스 유형과 이름 및 첨부 목록을 공통 응답 필드에 담는다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     */
    protected SourceResponse(SourceEditVo edit) {
        this.sourceId = edit.getSourceId();
        this.sourceType = edit.getDetails().getType().name();
        this.name = edit.getDetails().getName();
        this.createdAt = edit.getCreatedAt();
        this.updatedAt = edit.getUpdatedAt();
        this.attachments = edit.getAttachments().stream().map(AttachmentResponse::from).toList();
    }

    /**
     * 저장된 소스를 유형별 최상위 필드를 가진 응답으로 변환한다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     * @return 해당 유형의 소스 상세 응답
     * @throws IllegalStateException 소스 유형의 전용 상세 응답이 아직 정의되지 않은 경우
     */
    public static SourceResponse from(SourceEditVo edit) {
        return switch (edit.getDetails().getType()) {
            case CAREER_HISTORY -> new CareerHistoryResponse(edit);
            case EXPERIENCE -> new ExperienceResponse(edit);
            case CERTIFICATION -> new CertificationResponse(edit);
            case TARGET_COMPANY, WRITING_STYLE, OTHER_TEXT ->
                    throw new IllegalStateException("신규 소스 유형의 전용 상세 응답이 아직 정의되지 않았습니다.");
        };
    }
}
