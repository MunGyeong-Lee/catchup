package com.moon.catchup.careeritem.docs;

import java.util.UUID;

import com.moon.catchup.common.http.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

/** 첨부 파일 목록, 다시 열기, 원본 내려받기의 OpenAPI 문서 계약이다. */
@Tag(name = "Career item attachments")
public interface CareerItemAttachmentDocs {
    /** 자료에서 첨부 파일 목록을 조회하는 작업을 문서화한다. */
    @Operation(summary = "첨부 파일 목록 조회")
    ResponseVo list(long materialId);

    /** 전체 또는 바이트 범위의 첨부 원본을 여는 작업을 문서화한다. */
    @Operation(summary = "첨부 파일 다시 열기")
    ResponseEntity<InputStreamResource> view(long materialId, UUID attachmentId, String range);

    /** 첨부 원본을 내려받는 작업을 문서화한다. */
    @Operation(summary = "첨부 원본 내려받기")
    ResponseEntity<InputStreamResource> download(long materialId, UUID attachmentId);
}
