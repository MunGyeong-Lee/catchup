package com.moon.catchup.careeritem.controller;

import java.nio.charset.StandardCharsets;

import com.moon.catchup.careeritem.domain.CareerItemAttachmentEntity;
import com.moon.catchup.careeritem.service.ReadableAttachment;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 브라우저에서 다시 열거나 내려받을 첨부 원본의 HTTP 응답을 만든다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttachmentFileResponse {
    /** 첨부 스트림에 미리보기 또는 다운로드 헤더를 설정하고, 전체 응답은 200, 바이트 범위 응답은 206으로 반환한다. */
    public static ResponseEntity<InputStreamResource> buildResponse(ReadableAttachment delivery, boolean inline) {
        CareerItemAttachmentEntity file = delivery.getAttachment();
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'none'");
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getName(), StandardCharsets.UTF_8).build());

        boolean partial = delivery.getContentRange() != null;
        if (inline) headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (partial) headers.set(HttpHeaders.CONTENT_RANGE, delivery.getContentRange());
        headers.setContentLength(delivery.getContentLength());

        // Spring 응답 변환기가 전송을 마친 뒤 스트림을 닫는다.
        InputStreamResource content = new InputStreamResource(delivery.getStream());
        return new ResponseEntity<>(content, headers, partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK);
    }
}
