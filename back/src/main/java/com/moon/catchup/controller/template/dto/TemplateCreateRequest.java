package com.moon.catchup.controller.template.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.moon.catchup.domain.template.TemplateDocumentType;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 문서 유형과 제목을 받고 문서 유형에 맞는 하위 요청으로 바인딩한다.
 *
 * <p>기본 그리드 요청에서도 문서 유형은 열거형 바인딩과 필수값 검증을 거친다.
 * 자기소개서의 숫자 별칭은 기존 열거형 바인딩에서 허용한 값이다.
 */
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "documentType", visible = true, defaultImpl = GridTemplateCreateRequest.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = GridTemplateCreateRequest.class, name = "RESUME"),
        @JsonSubTypes.Type(value = GridTemplateCreateRequest.class, name = "PORTFOLIO"),
        @JsonSubTypes.Type(value = GridTemplateCreateRequest.class, name = "CAREER_DESCRIPTION"),
        @JsonSubTypes.Type(value = ParagraphTemplateCreateRequest.class, name = "COVER_LETTER", names = {"3"})
})
@Schema(description = "문서 유형별 템플릿 등록 요청", discriminatorProperty = "documentType",
        oneOf = {GridTemplateCreateRequest.class, ParagraphTemplateCreateRequest.class},
        discriminatorMapping = {
                @DiscriminatorMapping(value = "RESUME", schema = GridTemplateCreateRequest.class),
                @DiscriminatorMapping(value = "PORTFOLIO", schema = GridTemplateCreateRequest.class),
                @DiscriminatorMapping(value = "CAREER_DESCRIPTION", schema = GridTemplateCreateRequest.class),
                @DiscriminatorMapping(value = "COVER_LETTER", schema = ParagraphTemplateCreateRequest.class)
        })
public abstract sealed class TemplateCreateRequest permits GridTemplateCreateRequest, ParagraphTemplateCreateRequest {
    @JsonProperty
    @Schema(description = "문서 유형", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "문서 유형을 선택해 주세요.")
    private TemplateDocumentType documentType;

    @JsonProperty
    @Schema(description = "템플릿 제목", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "템플릿 제목을 입력해 주세요.")
    private String title;
}
