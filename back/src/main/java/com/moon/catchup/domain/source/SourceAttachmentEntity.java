package com.moon.catchup.domain.source;

import java.util.UUID;

import com.moon.catchup.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 소스에 연결된 첨부 메타데이터와 S3 객체 키를 매핑한다. */
@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceAttachmentEntity extends BaseEntity {
    @Id
    @Column(name = "attachment_id")
    private UUID id;

    @Column(name = "source_id")
    private long sourceId;

    @Column(name = "original_name")
    private String name;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private long size;

    @Column(name = "object_key")
    private String objectKey;
}
