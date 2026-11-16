package com.moon.catchup.careeritem.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 등록한 자료의 식별자를 응답으로 전달한다. */
@Schema(description = "자료 등록 결과")
@Getter
@AllArgsConstructor
public final class RegistrationResponse {
    @Schema(description = "생성한 자료 식별자")
    private final long careerItemId;
}
