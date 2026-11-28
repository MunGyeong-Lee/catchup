package com.moon.catchup.controller.template.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 문단형 템플릿의 문항별 페이지 요청 */
@Getter
@Schema(description = "문단형 템플릿 구조")
public final class ParagraphTemplateStructureRequest {
    /** 표시 순서에 따른 자기소개서 문항별 페이지 목록 */
    @JsonProperty
    @Schema(description = "페이지 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Page> pages;

    /** 문항의 소제목 자리와 표시 순서에 따른 문단 번호 */
    @Getter
    @Schema(name = "ParagraphTemplatePageRequest")
    public static final class Page {
        /** 이 문항의 소제목 자리 식별자 */
        @JsonProperty
        @Schema(description = "문항의 소제목 자리 식별자", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer headingId;

        /** 표시 순서대로 1부터 이어지는 문단 번호 목록 */
        @JsonProperty
        @Schema(description = "표시 순서에 따른 문단 번호 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Integer> paragraphIds;
    }
}
