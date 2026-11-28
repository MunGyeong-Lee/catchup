package com.moon.catchup.common.http;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 요청 처리 중 발생한 예외를 일관된 JSON 응답으로 변환한다. */
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** 업무 오류의 응답 상태와 항목 검증 정보를 반영한다. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ResponseVo> handleApiException(ApiException exception) {
        ErrorCode code = exception.getCode();
        if (code.getStatus().is5xxServerError() && exception.getCause() != null) {
            log.warn("Request failed: code={}", code, exception.getCause());
        }
        return ResponseEntity.status(code.getStatus()).headers(exception.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ResponseVo.failure(code, exception.getFieldErrors()));
    }

    /** 요청 DTO의 필드 검증 오류를 공통 입력 오류 형식으로 변환한다. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage();
            fieldErrors.putIfAbsent(fieldError.getField(),
                    message == null ? ErrorCode.VALIDATION_FAILED.getUserMessage() : message);
        }
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(exception, ResponseVo.failure(ErrorCode.VALIDATION_FAILED, fieldErrors),
                responseHeaders, status, request);
    }

    /**
     * JSON 바인딩이 감싼 필드 검증 오류를 기존 응답으로 변환한다.
     *
     * @param exception 요청 본문 바인딩 예외
     * @param headers 기본 응답 헤더
     * @param status 기본 응답 상태
     * @param request 현재 HTTP 요청
     * @return 필드 검증 오류 또는 일반 요청 형식 오류 응답
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.getMostSpecificCause() instanceof ApiException apiException
                && apiException.getCode() == ErrorCode.VALIDATION_FAILED) {
            ResponseEntity<ResponseVo> response = this.handleApiException(apiException);
            return super.handleExceptionInternal(exception, response.getBody(), response.getHeaders(),
                    response.getStatusCode(), request);
        }
        return super.handleHttpMessageNotReadable(exception, headers, status, request);
    }

    /** 요청 파싱과 크기 제한 등 Spring이 처리한 HTTP 오류를 공통 형식으로 변환한다. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
                                                              HttpHeaders headers, HttpStatusCode status,
                                                              WebRequest request) {
        ErrorCode code = status.value() == 413 ? ErrorCode.PAYLOAD_TOO_LARGE
                : status.is4xxClientError() ? ErrorCode.INVALID_REQUEST : ErrorCode.INTERNAL_ERROR;
        if (status.is5xxServerError()) log.error("Framework request processing failed: status={}", status.value(), exception);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(exception, ResponseVo.failure(code, Map.of()),
                responseHeaders, status, request);
    }

    /** 예상하지 못한 오류를 서버에 기록하고 사용자에게 일반 오류만 전달한다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseVo> handleUnexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON)
                .body(ResponseVo.failure(ErrorCode.INTERNAL_ERROR, Map.of()));
    }
}
