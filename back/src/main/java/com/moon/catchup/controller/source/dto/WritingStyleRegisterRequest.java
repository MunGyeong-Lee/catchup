package com.moon.catchup.controller.source.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 문체 및 어투 등록의 유형별 입력을 받는다. */
@Getter
@Schema(description = "문체 및 어투 소스 등록 요청")
public final class WritingStyleRegisterRequest extends SourceRegisterRequest {
    @JsonProperty
    @Schema(description = "문체 설명 또는 예문", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private Object content;

    /**
     * 문체 및 어투 등록의 최상위 입력을 상세 저장 키에 연결한다.
     *
     * @param details 요청 검증에 사용할 상세 입력
     */
    @Override
    protected void addDetails(Map<String, Object> details) {
        details.put("content", this.content);
    }
}
