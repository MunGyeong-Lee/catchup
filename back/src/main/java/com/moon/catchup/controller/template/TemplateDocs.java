package com.moon.catchup.controller.template;

import com.moon.catchup.common.http.ResponseVo;
import com.moon.catchup.controller.template.dto.TemplateCreateRequest;
import com.moon.catchup.controller.template.dto.TemplateCreateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;

/** 템플릿 등록 API의 요청과 응답을 문서화한다. */
@Tag(name = "Templates")
public interface TemplateDocs {
    /**
     * 문서 유형에 맞는 템플릿을 등록하는 요청을 문서화한다.
     *
     * @param req 문서 유형, 제목과 편집 구조
     * @return 등록 식별자를 담은 공통 응답
     */
    @Operation(summary = "템플릿 등록",
            description = "이력서, 포트폴리오, 경력기술서는 그리드형이고 자기소개서는 문단형입니다. "
                    + "그리드형은 12열이며 행과 열은 1부터 시작합니다. 방향을 생략하면 세로이며 rowSpan을 생략하면 한 행을 차지합니다. "
                    + "편집 구조는 Jackson 기본 타입 변환을 적용합니다. 예를 들어 정수 필드의 소수 부분을 제거하고, "
                    + "숫자로 보낸 블록 제목은 문자열로 저장합니다. 방향과 블록 유형은 문자열 코드를 사용합니다. "
                    + "문단형은 한 페이지에 한 문항을 두고 문단 번호를 각 문항에서 1부터 연속으로 보냅니다.",
            requestBody = @RequestBody(required = true, content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TemplateCreateRequest.class),
                    examples = {
                            @ExampleObject(name = "GRID", value = """
                                    {
                                      "documentType": "RESUME",
                                      "title": "기본 이력서",
                                      "structure": {"pages": [
                                        {"orientation": "PORTRAIT", "blocks": [
                                          {"id": 1, "type": "TEXT", "title": "경력",
                                           "placement": {"row": 1, "column": 1, "columnSpan": 12, "rowSpan": 2}}
                                        ]}
                                      ]}
                                    }
                                    """),
                            @ExampleObject(name = "PARAGRAPH", value = """
                                    {
                                      "documentType": "COVER_LETTER",
                                      "title": "기본 자기소개서",
                                      "structure": {"pages": [
                                        {"headingId": 1, "paragraphIds": [1, 2]},
                                        {"headingId": 2, "paragraphIds": [1]}
                                      ]}
                                    }
                                    """)
                    })))
    @ApiResponse(responseCode = "201", description = "등록한 템플릿 식별자",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = TemplateCreateEnvelope.class)))
    @ApiResponse(responseCode = "400", description = "JSON을 읽을 수 없거나 요청 값 또는 구조를 해석할 수 없으면 INVALID_REQUEST, "
            + "필수값 및 구조 검증에 실패하면 VALIDATION_FAILED와 field_errors를 반환합니다.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseVo.class)))
    public ResponseVo create(TemplateCreateRequest req);

    /** OpenAPI에 표시할 정상 등록 응답의 구조다. */
    @Getter
    public static final class TemplateCreateEnvelope {
        @Schema(types = "null", requiredMode = Schema.RequiredMode.REQUIRED)
        private String error;
        @Schema(name = "error_message", types = "null", requiredMode = Schema.RequiredMode.REQUIRED)
        private String errorMessage;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private TemplateCreateResponse response;
    }
}
