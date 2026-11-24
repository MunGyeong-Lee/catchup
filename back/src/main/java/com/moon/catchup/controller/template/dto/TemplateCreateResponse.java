package com.moon.catchup.controller.template.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 등록된 템플릿의 식별자를 전달한다. */
@Getter
@AllArgsConstructor
@Schema(description = "템플릿 등록 결과")
public final class TemplateCreateResponse {
    @Schema(description = "등록한 템플릿 식별자", requiredMode = Schema.RequiredMode.REQUIRED)
    private final long templateId;
}
