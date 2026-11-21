package com.moon.catchup.controller.source.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 소스 수정에 필요한 유형, 상세 입력, 제거할 첨부를 받는다. */
@Getter
@Schema(description = "이력, 경험, 자격증 수정 요청")
public final class SourceUpdateRequest {
    @Schema(description = "저장할 소스 유형")
    private final String sourceType;
    @Schema(description = "소스 이름", type = "string", requiredMode = Schema.RequiredMode.REQUIRED)
    private final Object name;
    @Schema(description = "저장할 유형별 입력 전체. 연월은 YYYY-MM, 달력 날짜는 YYYY-MM-DD 문자열을 사용합니다.")
    private final Map<String, Object> details;
    @Schema(description = "제거할 첨부 식별자")
    private final List<UUID> removeAttachmentIds;

    /**
     * 요청 값을 읽고 생략한 제거 대상은 빈 목록으로 처리한다.
     *
     * @param sourceType 요청 소스 유형 코드
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param removeAttachmentIds 제거할 첨부파일 식별자 목록. 생략하면 빈 목록
     */
    @JsonCreator
    public SourceUpdateRequest(@JsonProperty("sourceType") String sourceType,
                                   @JsonProperty("name") Object name,
                                 @JsonProperty("details") Map<String, Object> details,
                                   @JsonProperty("removeAttachmentIds") List<UUID> removeAttachmentIds) {
        this.sourceType = sourceType;
        this.name = name;
        this.details = details;
        this.removeAttachmentIds = removeAttachmentIds == null ? List.of() : List.copyOf(removeAttachmentIds);
    }
}
