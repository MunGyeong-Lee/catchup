package com.moon.catchup.careeritem.controller;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.careeritem.docs.CareerItemAttachmentDocs;
import com.moon.catchup.careeritem.dto.AttachmentResponse;
import com.moon.catchup.careeritem.service.CareerItemAttachmentService;
import com.moon.catchup.common.http.ResponseVo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 첨부 파일 목록, 브라우저 열기, 원본 내려받기의 HTTP 요청을 처리한다. */
@RestController
@RequestMapping("/api/career-materials/{materialId}/attachments")
@RequiredArgsConstructor
public class CareerItemAttachmentController implements CareerItemAttachmentDocs {
    private final CareerItemAttachmentService service;

    /** 요청한 자료에 속한 첨부 파일의 목록을 반환한다. */
    @GetMapping
    @Override
    public ResponseVo list(@PathVariable long materialId) {
        List<AttachmentResponse> attachmentList = this.service.list(materialId).stream().map(AttachmentResponse::from).toList();
        return ResponseVo.buildResponse(attachmentList);
    }

    /** 첨부 파일을 브라우저에서 열 수 있도록 전체 또는 요청 바이트 범위를 반환한다. */
    @GetMapping("/{attachmentId}/view")
    @Override
    public ResponseEntity<InputStreamResource> view(@PathVariable long materialId, @PathVariable UUID attachmentId,
                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return AttachmentFileResponse.buildResponse(this.service.deliver(materialId, attachmentId, range), true);
    }

    /** 원본 파일 이름을 포함한 내려받기 응답을 반환한다. */
    @GetMapping("/{attachmentId}/download")
    @Override
    public ResponseEntity<InputStreamResource> download(@PathVariable long materialId, @PathVariable UUID attachmentId) {
        return AttachmentFileResponse.buildResponse(this.service.deliver(materialId, attachmentId, null), false);
    }
}
