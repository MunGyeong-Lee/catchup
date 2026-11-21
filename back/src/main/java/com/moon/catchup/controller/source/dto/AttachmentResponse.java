package com.moon.catchup.controller.source.dto;

import java.util.UUID;

import com.moon.catchup.domain.source.SourceAttachmentSummaryVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 첨부 목록에서 첨부 식별자, 원본 이름, 콘텐츠 유형, 크기를 전달한다. */
@Schema(description = "첨부 파일 요약")
@Getter
@AllArgsConstructor
public final class AttachmentResponse {
    @Schema(description = "첨부 파일 식별자")
    private final UUID attachmentId;
    @Schema(description = "원본 파일 이름")
    private final String name;
    @Schema(description = "검증된 콘텐츠 유형")
    private final String contentType;
    @Schema(description = "크기(바이트)")
    private final long size;

    /**
     * 첨부파일 요약 VO에서 목록 응답에 공개할 값을 복사한다.
     *
     * @param file 첨부파일 요약
     * @return 첨부파일 응답
     */
    public static AttachmentResponse from(SourceAttachmentSummaryVo file) {
        return new AttachmentResponse(file.getId(), file.getName(), file.getContentType(), file.getSize());
    }
}
