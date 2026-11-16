package com.moon.catchup.careeritem.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 자료 수정에 필요한 저장된 입력값과 첨부 목록이다. */
@Getter
@AllArgsConstructor
public final class CareerItemEditVo {
    private final CareerItemEntity careerItem;
    private final List<CareerItemAttachmentEntity> attachments;
}
