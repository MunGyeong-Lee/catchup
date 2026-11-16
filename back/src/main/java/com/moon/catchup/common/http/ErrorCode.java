package com.moon.catchup.common.http;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 프론트엔드가 사용할 고정 오류 코드와 HTTP 상태를 정의한다. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 4xx 요청 오류
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식을 확인해 주세요."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력한 내용을 확인해 주세요."),
    CAREER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "자료를 찾을 수 없어요."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부 파일을 찾을 수 없어요."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일당 20MiB, 첨부 합계 100MiB까지 가능합니다."),
    INVALID_RANGE(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "요청한 파일 범위를 읽을 수 없어요."),

    // 5xx 서버 오류
    ATTACHMENT_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "첨부 파일을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String userMessage;
}
