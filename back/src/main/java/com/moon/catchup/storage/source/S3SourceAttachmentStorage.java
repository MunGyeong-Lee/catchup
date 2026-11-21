package com.moon.catchup.storage.source;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

import com.moon.catchup.common.storage.s3.S3ObjectStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 첨부 파일 원본을 비공개 Amazon S3 버킷에 저장하고 읽는다. */
@Component
@RequiredArgsConstructor
public class S3SourceAttachmentStorage implements SourceAttachmentStorage {
    private static final Pattern MANAGED_KEY = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private final S3ObjectStorage s3ObjectStorage;
    private final S3AttachmentSettings s3AttachmentSettings;

    /**
     * 원본 스트림을 지정된 버킷 경로에 업로드한다.
     *
     * @param key S3 객체 키
     * @param type 첨부 원본의 콘텐츠 유형
     * @param size 원본의 바이트 수
     * @param content 읽을 원본 스트림
     */
    @Override
    public void put(String key, String type, long size, InputStream content) {
        this.s3ObjectStorage.put(bucket(), this.s3AttachmentSettings.getPrefix() + key, type, size, content);
    }

    /**
     * 객체 전체 또는 단일 바이트 범위를 읽는 스트림을 연다.
     *
     * @param key S3 객체 키
     * @param range 단일 HTTP Range 헤더 값. 전체 요청이면 null
     * @return 호출자가 사용 후 닫아야 하는 원본 입력 스트림
     */
    @Override
    public InputStream read(String key, String range) {
        return this.s3ObjectStorage.read(bucket(), this.s3AttachmentSettings.getPrefix() + key, range);
    }

    /**
     * 실패 복구 대상 등 지정한 원본 객체를 삭제한다.
     *
     * @param key S3 객체 키
     */
    @Override
    public void delete(String key) {
        this.s3ObjectStorage.delete(bucket(), this.s3AttachmentSettings.getPrefix() + key);
    }

    /**
     * 설정된 S3 버킷의 첨부 경로를 페이지 단위로 조회해 Catchup 첨부 객체 키를 반환한다.
     *
     * @return 조회 또는 삭제한 S3 객체 키 목록
     */
    @Override
    public List<String> listKeys() {
        if (this.s3AttachmentSettings.getBucket().isBlank()) return List.of();
        String prefix = this.s3AttachmentSettings.getPrefix();
        return this.s3ObjectStorage.listKeys(bucket(), prefix)
                .filter(key -> key.startsWith(prefix)).map(key -> key.substring(prefix.length()))
                .filter(key -> MANAGED_KEY.matcher(key).matches()).toList();
    }

    /**
     * 버킷이 구성되었는지 확인하고 버킷 이름을 반환한다.
     *
     * @return 설정한 S3 버킷 이름
     * @throws IllegalStateException 첨부 버킷이 설정되지 않은 경우
     */
    private String bucket() {
        if (this.s3AttachmentSettings.getBucket().isBlank()) throw new IllegalStateException("Attachment bucket is not configured");
        return this.s3AttachmentSettings.getBucket();
    }
}
