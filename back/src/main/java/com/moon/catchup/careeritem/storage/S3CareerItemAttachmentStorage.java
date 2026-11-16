package com.moon.catchup.careeritem.storage;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/** 첨부 파일 원본을 비공개 Amazon S3 버킷에 저장하고 읽는다. */
@Component
@RequiredArgsConstructor
public class S3CareerItemAttachmentStorage implements CareerItemAttachmentStorage {
    private static final Pattern MANAGED_KEY = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private final S3Client s3Client;
    private final S3AttachmentSettings s3AttachmentSettings;

    /** 원본 스트림을 지정된 버킷 경로에 업로드한다. */
    @Override
    public void put(String key, String type, long size, InputStream content) {
        this.s3Client.putObject(request -> request.bucket(bucket()).key(this.s3AttachmentSettings.getPrefix() + key).contentType(type),
                RequestBody.fromInputStream(content, size));
    }

    /** 객체 전체 또는 단일 바이트 범위를 읽는 스트림을 연다. */
    @Override
    public InputStream read(String key, String range) {
        return this.s3Client.getObject(request -> request.bucket(bucket()).key(this.s3AttachmentSettings.getPrefix() + key).range(range));
    }

    /** 실패 복구 대상 등 지정한 원본 객체를 삭제한다. */
    @Override
    public void delete(String key) {
        this.s3Client.deleteObject(request -> request.bucket(bucket()).key(this.s3AttachmentSettings.getPrefix() + key));
    }

    /** 설정된 S3 버킷의 첨부 경로를 페이지 단위로 조회해 Catchup 첨부 객체 키를 반환한다. */
    @Override
    public List<String> listKeys() {
        if (this.s3AttachmentSettings.getBucket().isBlank()) return List.of();
        String prefix = this.s3AttachmentSettings.getPrefix();
        return this.s3Client.listObjectsV2Paginator(request -> request.bucket(bucket()).prefix(prefix))
                .contents().stream().map(S3Object::key)
                .filter(key -> key.startsWith(prefix)).map(key -> key.substring(prefix.length()))
                .filter(key -> MANAGED_KEY.matcher(key).matches()).toList();
    }

    /** 버킷이 구성되었는지 확인하고 버킷 이름을 반환한다. */
    private String bucket() {
        if (this.s3AttachmentSettings.getBucket().isBlank()) throw new IllegalStateException("Attachment bucket is not configured");
        return this.s3AttachmentSettings.getBucket();
    }
}
