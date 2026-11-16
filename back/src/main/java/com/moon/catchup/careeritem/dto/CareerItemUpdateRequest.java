package com.moon.catchup.careeritem.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 자료 수정에 필요한 유형, 상세 입력, 제거할 첨부를 받는다. */
@Getter
@Schema(description = "이력, 경험, 자격증 수정 요청")
public final class CareerItemUpdateRequest {
    @Schema(description = "저장할 자료 유형")
    private final String careerItemType;
    @Schema(description = "저장할 유형별 입력 전체")
    private final Map<String, Object> details;
    @Schema(description = "제거할 첨부 식별자")
    private final List<UUID> removeAttachmentIds;

    /** 요청 값을 읽고 생략한 제거 대상은 빈 목록으로 처리한다. */
    @JsonCreator
    public CareerItemUpdateRequest(@JsonProperty("careerItemType") String careerItemType,
                                   @JsonProperty("details") Map<String, Object> details,
                                   @JsonProperty("removeAttachmentIds") List<UUID> removeAttachmentIds) {
        this.careerItemType = careerItemType;
        this.details = details;
        this.removeAttachmentIds = removeAttachmentIds == null ? List.of() : List.copyOf(removeAttachmentIds);
    }
}
