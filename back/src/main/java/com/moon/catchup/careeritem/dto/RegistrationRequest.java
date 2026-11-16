package com.moon.catchup.careeritem.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/** 자료 등록 요청에서 자료 유형과 유형별 입력을 읽는다. */
@Schema(description = "이력, 경험, 자격증 등록 요청")
@Getter
public final class RegistrationRequest {
    @Schema(description = "자료 유형")
    private final String careerItemType;
    @Schema(description = "자료 유형별 등록 항목")
    private final Map<String, Object> details;

    /** 요청 본문의 자료 유형과 입력 객체를 등록 요청에 담는다. */
    @JsonCreator
    public RegistrationRequest(@JsonProperty("careerItemType") String careerItemType,
                               @JsonProperty("details") Map<String, Object> details) {
        this.careerItemType = careerItemType;
        this.details = details;
    }
}
