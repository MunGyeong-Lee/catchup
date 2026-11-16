package com.moon.catchup.careeritem.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 자료 수정에 필요한 저장된 입력값과 첨부 목록이다. */
@Getter
@AllArgsConstructor
public final class CareerItemEditVo {
    private final long careerItemId;
    private final CareerItemDetailsVo details;
    private final List<CareerItemAttachmentSummaryVo> attachments;

    /**
     * 자료 엔티티와 첨부파일 목록을 수정 화면용 VO로 변환한다.
     *
     * @param careerItem DB에서 조회한 자료 엔티티
     * @param attachmentList 자료에 연결된 첨부파일 요약 목록
     * @return 자료 수정 화면용 VO
     */
    public static CareerItemEditVo from(CareerItemEntity careerItem,
                                        List<CareerItemAttachmentSummaryVo> attachmentList) {
        return new CareerItemEditVo(careerItem.getId(), careerItem.getDetails(), attachmentList);
    }
}
