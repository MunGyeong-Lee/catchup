package com.moon.catchup.careeritem.storage;

import java.io.InputStream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** 첨부 파일 원본을 비공개 Amazon S3 버킷에 저장하고 읽는다. */
@Component
@RequiredArgsConstructor
class S3CareerItemAttachmentStorage implements CareerItemAttachmentStorage {
    private final S3Client client;
    private final S3AttachmentSettings settings;

    /** 원본 스트림을 지정된 버킷 경로에 업로드한다. */
    @Override
    public void put(String key, String type, long size, InputStream content) {
        this.client.putObject(request -> request.bucket(bucket()).key(this.settings.getPrefix() + key).contentType(type),
                RequestBody.fromInputStream(content, size));
    }

    /** 객체 전체 또는 단일 바이트 범위를 읽는 스트림을 연다. */
    @Override
    public InputStream read(String key, String range) {
        return this.client.getObject(request -> request.bucket(bucket()).key(this.settings.getPrefix() + key).range(range));
    }

    /** 실패 복구 대상 등 지정한 원본 객체를 삭제한다. */
    @Override
    public void delete(String key) {
        this.client.deleteObject(request -> request.bucket(bucket()).key(this.settings.getPrefix() + key));
    }

    /** 버킷이 구성되었는지 확인하고 버킷 이름을 반환한다. */
    private String bucket() {
        if (this.settings.getBucket().isBlank()) throw new IllegalStateException("Attachment bucket is not configured");
        return this.settings.getBucket();
    }
}
