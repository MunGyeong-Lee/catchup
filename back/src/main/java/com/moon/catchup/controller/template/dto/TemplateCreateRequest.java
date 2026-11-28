package com.moon.catchup.controller.template.dto;

import com.moon.catchup.domain.template.GridTemplateStructureVo;
import com.moon.catchup.domain.template.ParagraphTemplateStructureVo;
import com.moon.catchup.domain.template.TemplateDocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.databind.node.ObjectNode;

/** 문서 유형과 제목, 유형에 대응하는 템플릿 구조를 담는다. */
@Getter
@AllArgsConstructor
@Schema(description = "템플릿 등록 요청")
public final class TemplateCreateRequest {
    @Schema(description = "문서 유형", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "문서 유형을 선택해 주세요.")
    private final TemplateDocumentType documentType;

    @Schema(description = "템플릿 제목", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "템플릿 제목을 입력해 주세요.")
    private final String title;

    @Schema(description = "문서 유형별 편집 구조", requiredMode = Schema.RequiredMode.REQUIRED,
            anyOf = {GridTemplateStructureVo.class, ParagraphTemplateStructureVo.class})
    @NotNull(message = "템플릿 구조를 입력해 주세요.")
    private final ObjectNode structure;
}
