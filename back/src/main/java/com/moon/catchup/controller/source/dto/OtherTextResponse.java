package com.moon.catchup.controller.source.dto;

import java.util.Objects;

import com.moon.catchup.domain.source.SourceEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 기타 텍스트 소스의 상세 정보를 전달한다. */
@Getter
@Schema(description = "기타 텍스트 소스 상세 응답")
public final class OtherTextResponse extends SourceResponse {
    @Schema(description = "참고 자료 본문")
    private final String content;

    /**
     * 저장된 기타 텍스트 입력값을 응답 필드에 담는다.
     *
     * @param edit 저장된 소스 상세와 첨부 요약
     */
    public OtherTextResponse(SourceEditVo edit) {
        super(edit);
        this.content = Objects.toString(edit.getDetails().getFields().get("content"), null);
    }
}
