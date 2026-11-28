package com.moon.catchup.controller.template.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/** 그리드형 템플릿의 등록 입력을 받는다. */
@Getter
@Schema(description = "그리드형 템플릿 등록 요청")
public final class GridTemplateCreateRequest extends TemplateCreateRequest {
    /** 그리드형 편집 구조 */
    @JsonProperty
    @Schema(description = "그리드형 편집 구조", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "템플릿 구조를 입력해 주세요.")
    private GridTemplateStructureRequest structure;
}
