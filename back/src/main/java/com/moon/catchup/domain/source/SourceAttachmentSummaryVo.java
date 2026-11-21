package com.moon.catchup.domain.source;

import java.util.UUID;

import lombok.Getter;

/** 첨부파일 목록에 표시할 식별자, 원본 이름, 콘텐츠 유형, 바이트 크기를 담는다. */
@Getter
public final class SourceAttachmentSummaryVo {
    private final UUID id;
    private final String name;
    private final String contentType;
    private final long size;

    /**
     * 첨부파일 목록에 필요한 메타데이터를 초기화한다.
     *
     * @param attachment DB에서 조회한 첨부파일 엔티티
     */
    private SourceAttachmentSummaryVo(SourceAttachmentEntity attachment) {
        this.id = attachment.getId();
        this.name = attachment.getName();
        this.contentType = attachment.getContentType();
        this.size = attachment.getSize();
    }

    /**
     * 첨부파일 엔티티에서 목록에 표시할 정보를 복사한다.
     *
     * @param attachment DB에서 조회한 첨부파일 엔티티
     * @return 첨부파일 목록용 VO
     */
    public static SourceAttachmentSummaryVo from(SourceAttachmentEntity attachment) {
        return new SourceAttachmentSummaryVo(attachment);
    }
}
