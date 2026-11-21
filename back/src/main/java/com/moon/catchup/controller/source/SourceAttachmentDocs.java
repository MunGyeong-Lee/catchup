package com.moon.catchup.controller.source;

import java.util.UUID;

import com.moon.catchup.common.http.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

/** 첨부 파일 목록, 다시 열기, 원본 내려받기의 OpenAPI 문서 계약이다. */
@Tag(name = "Source attachments")
public interface SourceAttachmentDocs {
    /**
     * 소스에서 첨부 파일 목록을 조회하는 작업을 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @return 요청 결과를 담은 공통 응답
     */
    @Operation(summary = "첨부 파일 목록 조회")
    @ApiResponse(responseCode = "200", description = "등록 순서의 첨부 목록. 등록된 첨부가 없으면 빈 목록")
    public ResponseVo list(long sourceId);

    /**
     * 전체 또는 바이트 범위의 첨부 원본을 여는 작업을 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @param range 단일 HTTP Range 헤더 값. 전체 요청이면 null
     * @return HTTP 헤더와 원본 스트림을 포함한 첨부 응답
     */
    @Operation(summary = "첨부 파일 다시 열기")
    @ApiResponse(responseCode = "200", description = "첨부 원본 전체")
    @ApiResponse(responseCode = "206", description = "요청한 단일 바이트 범위의 원본")
    @ApiResponse(responseCode = "404", description = "해당 소스의 첨부 파일이 존재하지 않음")
    @ApiResponse(responseCode = "416", description = "요청한 바이트 범위를 제공할 수 없음")
    @ApiResponse(responseCode = "503", description = "첨부 원본을 읽을 수 없음")
    public ResponseEntity<InputStreamResource> view(long sourceId, UUID attachmentId, String range);

    /**
     * 첨부 원본을 내려받는 작업을 문서화한다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @return HTTP 헤더와 원본 스트림을 포함한 첨부 응답
     */
    @Operation(summary = "첨부 원본 내려받기")
    @ApiResponse(responseCode = "200", description = "다운로드할 첨부 원본")
    @ApiResponse(responseCode = "404", description = "해당 소스의 첨부 파일이 존재하지 않음")
    @ApiResponse(responseCode = "503", description = "첨부 원본을 읽을 수 없음")
    public ResponseEntity<InputStreamResource> download(long sourceId, UUID attachmentId);
}
