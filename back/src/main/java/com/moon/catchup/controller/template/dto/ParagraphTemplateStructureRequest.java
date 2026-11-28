package com.moon.catchup.controller.template.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 문단형 템플릿의 문항별 페이지 요청 */
@Getter
@Schema(description = "문단형 템플릿 구조")
public final class ParagraphTemplateStructureRequest {
    /** 표시 순서에 따른 자기소개서 문항별 페이지 목록 */
    @JsonProperty
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Page> pages;

    /** 문항의 소제목 자리와 표시 순서에 따른 문단 번호 */
    @Getter
    @Schema(name = "ParagraphTemplatePageRequest")
    public static final class Page {
        /** 이 문항의 소제목 자리 식별자 */
        @JsonProperty
        private Integer headingId;

        /** 표시 순서대로 1부터 이어지는 문단 번호 목록 */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonProperty
        private List<Integer> paragraphIds;

        /**
         * 다른 구조 유형의 전용 필드 거부
         *
         * @param field 선언되지 않은 JSON 필드명
         * @param value 요청에 포함된 값
         * @throws ApiException 다른 구조 유형에만 있는 필드를 입력한 경우
         */
        @JsonAnySetter
        private void rejectIncompatibleField(String field, Object value) {
            if ("blocks".equals(field) || "orientation".equals(field)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("structure", "문단형 페이지를 사용해 주세요."));
            }
        }
    }
}
