package com.moon.catchup.common.http;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 정상 데이터 또는 사용자에게 표시할 오류를 공통 형식으로 전달한다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class ResponseVo {
    private final ErrorCode error;
    @JsonProperty("error_message")
    private final String errorMessage;
    private final Object response;
    @JsonProperty("field_errors")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Map<String, String> fieldErrors;

    /** 정상 응답에 기능별 데이터를 담는다. */
    public static ResponseVo buildResponse(Object data) {
        return new ResponseVo(null, null, data, null);
    }

    /** 반환할 데이터가 없는 정상 응답을 생성한다. */
    public static ResponseVo buildResponse() {
        return buildResponse(null);
    }

    /** 입력 검증 오류에만 항목별 오류를 포함한다. */
    static ResponseVo failure(ErrorCode code, Map<String, String> fieldErrors) {
        return new ResponseVo(code, code.getUserMessage(), null,
                code == ErrorCode.VALIDATION_FAILED ? Map.copyOf(fieldErrors) : null);
    }
}
