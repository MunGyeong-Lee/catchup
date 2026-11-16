package com.moon.catchup.careeritem.service;

import java.io.InputStream;

import com.moon.catchup.careeritem.domain.CareerItemAttachmentEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 첨부 메타데이터, 열린 원본 스트림, 전송할 바이트 정보를 담는다. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ReadableAttachment {
    private final CareerItemAttachmentEntity attachment;
    private final InputStream stream;
    private final long contentLength;
    private final String contentRange;
}
