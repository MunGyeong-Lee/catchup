package com.moon.catchup.common.storage.s3;

import java.io.InputStream;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

/** 호출자가 지정한 버킷과 객체 키로 공통 S3 연산을 수행한다. */
@Component
@RequiredArgsConstructor
public class S3ObjectStorage {
    private final S3Client s3Client;

    /** 지정된 버킷과 키에 콘텐츠 유형과 크기를 포함해 원본을 저장한다. */
    public void put(String bucket, String key, String contentType, long size, InputStream content) {
        this.s3Client.putObject(request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromInputStream(content, size));
    }

    /** 전체 객체 또는 지정한 바이트 범위를 읽는 스트림을 연다. */
    public InputStream read(String bucket, String key, String range) {
        return this.s3Client.getObject(request -> request.bucket(bucket).key(key).range(range));
    }

    /** 지정한 버킷의 객체를 삭제한다. */
    public void delete(String bucket, String key) {
        this.s3Client.deleteObject(request -> request.bucket(bucket).key(key));
    }

    /** 접두어로 조회한 S3 객체의 전체 키를 페이지 순서대로 제공한다. */
    public Stream<String> listKeys(String bucket, String prefix) {
        return this.s3Client.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(prefix))
                .contents().stream().map(S3Object::key);
    }
}
