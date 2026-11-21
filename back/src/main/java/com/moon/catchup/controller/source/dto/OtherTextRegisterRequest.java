package com.moon.catchup.controller.source.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 기타 텍스트 등록의 유형별 입력을 받는다. */
@Getter
@Schema(description = "기타 텍스트 소스 등록 요청")
public final class OtherTextRegisterRequest extends SourceRegisterRequest {
    @JsonProperty
    @Schema(description = "참고 자료 본문", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object content;

    /**
     * 기타 텍스트 등록의 최상위 입력을 상세 저장 키에 연결한다.
     *
     * @param details 요청 검증에 사용할 상세 입력
     */
    @Override
    protected void addDetails(Map<String, Object> details) {
        details.put("content", this.content);
    }
}
