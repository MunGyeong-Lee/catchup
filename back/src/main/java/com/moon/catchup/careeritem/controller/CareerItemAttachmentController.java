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
@RequestMapping("/api/career-items/{careerItemId}/attachments")
@RequiredArgsConstructor
public class CareerItemAttachmentController implements CareerItemAttachmentDocs {
    private final CareerItemAttachmentService attachmentService;

    /** 자료의 첨부 파일 목록 조회 */
    @GetMapping
    @Override
    public ResponseVo list(@PathVariable long careerItemId) {
        List<AttachmentResponse> attachmentList = this.attachmentService.list(careerItemId).stream().map(AttachmentResponse::from).toList();
        return ResponseVo.buildResponse(attachmentList);
    }

    /** 첨부 파일 미리보기 */
    @GetMapping("/{attachmentId}/view")
    @Override
    public ResponseEntity<InputStreamResource> view(@PathVariable long careerItemId, @PathVariable UUID attachmentId,
                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return AttachmentFileResponse.buildResponse(this.attachmentService.deliver(careerItemId, attachmentId, range), true);
    }

    /** 첨부 원본 내려받기 */
    @GetMapping("/{attachmentId}/download")
    @Override
    public ResponseEntity<InputStreamResource> download(@PathVariable long careerItemId, @PathVariable UUID attachmentId) {
        return AttachmentFileResponse.buildResponse(this.attachmentService.deliver(careerItemId, attachmentId, null), false);
    }
}
