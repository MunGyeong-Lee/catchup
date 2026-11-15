package com.moon.catchup.common.http;

import java.util.Map;

import lombok.Getter;
import org.springframework.http.HttpHeaders;

/** 업무 오류를 공통 응답으로 변환하는 데 필요한 코드와 부가 정보를 전달한다. */
@Getter
public final class ApiException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, String> fieldErrors;
    private final HttpHeaders headers;

    /** 검증 항목이나 추가 헤더가 없는 업무 오류를 만든다. */
    public ApiException(ErrorCode code) {
        this(code, Map.of(), HttpHeaders.EMPTY, null);
    }

    /** 서버 로그에서 확인할 원인을 보존해 업무 오류를 만든다. */
    public ApiException(ErrorCode code, Throwable cause) {
        this(code, Map.of(), HttpHeaders.EMPTY, cause);
    }

    /** 응답 변환에 사용할 항목 오류 또는 헤더를 보관한다. */
    private ApiException(ErrorCode code, Map<String, String> fieldErrors, HttpHeaders headers, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
        this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
    }

    /** 검증에 실패한 입력 항목과 안전한 안내 메시지를 전달한다. */
    public static ApiException validation(Map<String, String> fieldErrors) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, fieldErrors, HttpHeaders.EMPTY, null);
    }

    /** 유효하지 않은 바이트 범위 오류에 원본 크기를 포함한다. */
    public static ApiException invalidRange(long size) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
        return new ApiException(ErrorCode.INVALID_RANGE, Map.of(), headers, null);
    }
}
