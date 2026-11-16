package com.moon.catchup.careeritem.service;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.careeritem.domain.CareerItemAttachmentEntity;

/** CareerItemAttachmentService의 공개 업무 계약이다. */
public interface CareerItemAttachmentService {
    /** 요청한 자료의 첨부 파일 목록을 반환한다. */
    public List<CareerItemAttachmentEntity> list(long careerItemId);

    /** 첨부 원본 전체 또는 요청 범위를 열어 반환한다. */
    public ReadableAttachment deliver(long careerItemId, UUID attachmentId, String range);
}
