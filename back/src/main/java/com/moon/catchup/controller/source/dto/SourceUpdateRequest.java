package com.moon.catchup.controller.source.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 소스 수정에 필요한 유형, 상세 입력, 제거할 첨부를 받는다. */
@Getter
@Schema(description = "이력, 경험, 자격증 수정 요청")
public final class SourceUpdateRequest {
    /** 저장할 소스 유형 */
    @JsonProperty
    @Schema(description = "저장할 소스 유형", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceType;

    /** 소스 이름 */
    @JsonProperty
    @Schema(description = "소스 이름", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object name;

    /** 저장할 유형별 입력 */
    @JsonProperty
    @Schema(description = "저장할 유형별 입력 전체. 연월은 YYYY-MM, 달력 날짜는 YYYY-MM-DD 문자열을 사용합니다.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Map<String, Object> details;

    /** 제거할 첨부 식별자 목록 */
    @JsonProperty
    @JsonSetter(contentNulls = Nulls.FAIL)
    @Schema(description = "제거할 첨부 식별자")
    private List<UUID> removeAttachmentIds;
}
