package com.moon.catchup.controller.source.dto;

import java.util.Objects;

import com.moon.catchup.domain.source.SourceEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 문체 및 어투 소스의 상세 정보를 전달한다. */
@Getter
@Schema(description = "문체 및 어투 소스 상세 응답")
public final class WritingStyleResponse extends SourceResponse {
    @Schema(description = "문체 설명 또는 예문")
    private final String content;

    /**
     * 저장된 문체 및 어투 입력값을 응답 필드에 담는다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     */
    public WritingStyleResponse(SourceEditVo edit) {
        super(edit);
        this.content = Objects.toString(edit.getDetails().getFields().get("content"), null);
    }
}
