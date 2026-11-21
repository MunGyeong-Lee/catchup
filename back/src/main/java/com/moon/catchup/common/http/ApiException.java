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

    /**
     * 응답 변환에 사용할 업무 오류와 부가 정보를 보관한다.
     *
     * @param code 응답 상태와 오류 메시지를 결정하는 업무 오류 코드
     * @param fieldErrors 입력 항목별 오류 메시지. 항목 오류가 없으면 빈 맵
     * @param headers 오류 응답에 추가할 HTTP 헤더. 추가 헤더가 없으면 빈 헤더
     * @param cause 서버 로그에서 확인할 원인 예외. 원인이 없으면 null
     */
    public ApiException(ErrorCode code, Map<String, String> fieldErrors, HttpHeaders headers, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.fieldErrors = Map.copyOf(fieldErrors);
        this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
    }
}
