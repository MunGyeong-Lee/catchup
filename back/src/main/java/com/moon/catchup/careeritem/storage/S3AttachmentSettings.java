package com.moon.catchup.careeritem.storage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 첨부 원본을 저장할 S3 버킷 이름과 정규화된 객체 키 접두어를 담는다. */
@Getter
@RequiredArgsConstructor
public final class S3AttachmentSettings {
    private final String bucket;
    private final String prefix;
}
