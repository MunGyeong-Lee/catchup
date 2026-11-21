package com.moon.catchup.domain.source;

import java.util.UUID;

import lombok.Getter;

/** DB에서 조회한 첨부파일 정보와 S3 객체 키를 담는다. */
@Getter
public final class StoredAttachmentVo {
    private final UUID id;
    private final String name;
    private final String contentType;
    private final long size;
    private final String key;

    /**
     * 첨부파일의 메타데이터와 S3 객체 키를 초기화한다.
     *
     * @param attachment DB에서 조회한 첨부파일 엔티티
     */
    private StoredAttachmentVo(SourceAttachmentEntity attachment) {
        this.id = attachment.getId();
        this.name = attachment.getName();
        this.contentType = attachment.getContentType();
        this.size = attachment.getSize();
        this.key = attachment.getObjectKey();
    }

    /**
     * 첨부파일 엔티티의 메타데이터와 S3 객체 키를 조회용 VO로 변환한다.
     *
     * @param attachment DB에서 조회한 첨부파일 엔티티
     * @return 첨부파일 조회용 VO
     */
    public static StoredAttachmentVo from(SourceAttachmentEntity attachment) {
        return new StoredAttachmentVo(attachment);
    }
}
