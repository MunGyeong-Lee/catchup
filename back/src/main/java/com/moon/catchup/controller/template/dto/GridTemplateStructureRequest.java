package com.moon.catchup.controller.template.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.domain.template.TemplateBlockType;
import com.moon.catchup.domain.template.TemplatePageOrientation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 그리드형 템플릿의 페이지 및 블록 배치 요청 */
@Getter
@Schema(description = "그리드형 템플릿 구조")
public final class GridTemplateStructureRequest {
    /** 문서에 배치할 그리드 페이지 목록 */
    @JsonProperty
    @Schema(description = "페이지 목록", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Page> pages;

    /** 그리드 페이지의 용지 방향과 블록 목록 */
    @Getter
    @Schema(name = "GridTemplatePageRequest")
    public static final class Page {
        /** A4 용지의 방향 (기본값 세로) */
        @JsonProperty
        @Schema(description = "A4 용지 방향", defaultValue = "PORTRAIT")
        private TemplatePageOrientation orientation;

        /** 이 페이지에 배치할 블록 목록 */
        @JsonProperty
        @Schema(description = "페이지에 배치할 블록 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Block> blocks;

        /**
         * 다른 구조 유형의 전용 필드 거부
         *
         * @param field 선언되지 않은 JSON 필드명
         * @param value 요청에 포함된 값
         * @throws ApiException 다른 구조 유형에만 있는 필드를 입력한 경우
         */
        @JsonAnySetter
        private void rejectIncompatibleField(String field, Object value) {
            if ("headingId".equals(field) || "paragraphIds".equals(field)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("structure", "그리드형 페이지를 사용해 주세요."));
            }
        }
    }

    /** 그리드 블록의 식별자, 유형, 선택 제목 및 배치 */
    @Getter
    @Schema(name = "GridTemplateBlockRequest")
    public static final class Block {
        /** 템플릿 내 고유 블록 식별자 */
        @JsonProperty
        @Schema(description = "블록 식별자", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer id;

        /** 블록 유형 (텍스트 또는 이미지 자리) */
        @JsonProperty
        @Schema(description = "블록 유형", requiredMode = Schema.RequiredMode.REQUIRED)
        private TemplateBlockType type;

        /** 블록의 선택 제목 */
        @JsonProperty
        @Schema(description = "블록의 선택 제목")
        private String title;

        /** 그리드에서 블록의 시작 위치와 크기 */
        @JsonProperty
        @Schema(description = "블록의 시작 위치와 크기", requiredMode = Schema.RequiredMode.REQUIRED)
        private Placement placement;
    }

    /** 그리드 블록의 시작 위치와 행·열 범위 */
    @Getter
    @Schema(name = "GridTemplateBlockPlacementRequest")
    public static final class Placement {
        /** 블록의 시작 행 번호 (1부터) */
        @JsonProperty
        @Schema(description = "블록의 시작 행", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer row;

        /** 블록의 시작 열 번호 (1부터) */
        @JsonProperty
        @Schema(description = "블록의 시작 열", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer column;

        /** 블록이 가로로 차지하는 열 수 */
        @JsonProperty
        @Schema(description = "블록이 차지하는 열 수", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer columnSpan;

        /** 블록이 세로로 차지하는 행 수 (기본값 1) */
        @JsonProperty
        @Schema(description = "블록이 차지하는 행 수", requiredMode = Schema.RequiredMode.NOT_REQUIRED, defaultValue = "1")
        private Integer rowSpan = 1;
    }
}
