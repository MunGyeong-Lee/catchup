package com.moon.catchup.controller.template.dto;

import com.moon.catchup.domain.template.GridTemplateStructureVo;
import com.moon.catchup.domain.template.ParagraphTemplateStructureVo;
import com.moon.catchup.domain.template.TemplateDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

/** 문서 유형과 제목, 유형에 대응하는 템플릿 구조를 담는다. */
@Getter
@AllArgsConstructor
@Schema(description = "템플릿 등록 요청")
public final class TemplateCreateRequest {
    @Schema(implementation = TemplateDocumentType.class, requiredMode = Schema.RequiredMode.REQUIRED)
    private final JsonNode documentType;
    @Schema(type = "string", description = "템플릿 제목", requiredMode = Schema.RequiredMode.REQUIRED)
    private final JsonNode title;
    @Schema(description = "문서 유형별 편집 구조", requiredMode = Schema.RequiredMode.REQUIRED,
            anyOf = {GridTemplateStructureVo.class, ParagraphTemplateStructureVo.class})
    private final JsonNode structure;
}
