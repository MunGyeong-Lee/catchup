package com.moon.catchup.careeritem.dto;

import java.util.List;
import java.util.Map;

import com.moon.catchup.careeritem.domain.CareerItemDetailsVo;
import com.moon.catchup.careeritem.domain.CareerItemEditVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 수정 화면에서 사용하는 자료 저장값과 첨부 목록을 전달한다. */
@Getter
@AllArgsConstructor
@Schema(description = "자료 수정용 현재값")
public final class CareerItemEditResponse {
    @Schema(description = "자료 식별자")
    private final long careerItemId;
    @Schema(description = "현재 자료 유형")
    private final String careerItemType;
    @Schema(description = "저장된 자료 입력값")
    private final Map<String, Object> details;
    @Schema(description = "현재 첨부 정보")
    private final List<AttachmentResponse> attachments;

    /** 현재 자료와 첨부 정보에서 응답 값을 만든다. */
    public static CareerItemEditResponse from(CareerItemEditVo edit) {
        CareerItemDetailsVo details = edit.getCareerItem().getDetails();
        return new CareerItemEditResponse(edit.getCareerItem().getId(), details.getType().name(), details.getFields(),
                edit.getAttachments().stream().map(AttachmentResponse::from).toList());
    }
}
