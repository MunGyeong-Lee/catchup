package com.moon.catchup.controller.source;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.controller.source.SourceAttachmentDocs;
import com.moon.catchup.controller.source.dto.AttachmentResponse;
import com.moon.catchup.service.source.SourceAttachmentService;
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
@RequestMapping("/api/sources/{sourceId}/attachments")
@RequiredArgsConstructor
public class SourceAttachmentController implements SourceAttachmentDocs {
    private final SourceAttachmentService attachmentService;

    /**
     * 소스의 첨부 파일 목록 조회
     *
     * @param sourceId 소스 식별자
     * @return 요청 결과를 담은 공통 응답
     */
    @GetMapping
    @Override
    public ResponseVo list(@PathVariable long sourceId) {
        List<AttachmentResponse> attachmentList = this.attachmentService.list(sourceId).stream().map(AttachmentResponse::from).toList();
        return ResponseVo.buildResponse(attachmentList);
    }

    /** 첨부 파일 미리보기 */
    @GetMapping("/{attachmentId}/view")
    @Override
    public ResponseEntity<InputStreamResource> view(@PathVariable long sourceId, @PathVariable UUID attachmentId,
                           @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        return AttachmentFileResponse.buildResponse(this.attachmentService.deliver(sourceId, attachmentId, range), true);
    }

    /** 첨부 원본 내려받기 */
    @GetMapping("/{attachmentId}/download")
    @Override
    public ResponseEntity<InputStreamResource> download(@PathVariable long sourceId, @PathVariable UUID attachmentId) {
        return AttachmentFileResponse.buildResponse(this.attachmentService.deliver(sourceId, attachmentId, null), false);
    }
}
