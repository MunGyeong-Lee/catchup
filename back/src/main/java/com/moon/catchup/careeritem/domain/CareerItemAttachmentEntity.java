package com.moon.catchup.careeritem.domain;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 자료에 연결된 첨부 파일의 식별자, 이름, 콘텐츠 유형, 크기를 담는다. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class CareerItemAttachmentEntity {
    private final UUID id;
    private final String name;
    private final String contentType;
    private final long size;
}
