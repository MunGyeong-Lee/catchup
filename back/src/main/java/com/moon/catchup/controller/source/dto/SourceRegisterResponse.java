package com.moon.catchup.controller.source.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 등록한 소스의 식별자를 응답으로 전달한다. */
@Schema(description = "소스 등록 결과")
@Getter
@AllArgsConstructor
public final class SourceRegisterResponse {
    @Schema(description = "생성한 소스 식별자")
    private final long sourceId;
}
