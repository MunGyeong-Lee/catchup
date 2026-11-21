package com.moon.catchup.service.source;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.domain.source.SourceAttachmentSummaryVo;

/** SourceAttachmentService의 공개 업무 계약이다. */
public interface SourceAttachmentService {
    /**
     * 요청한 소스의 첨부 파일 목록을 반환한다.
     *
     * @param sourceId 소스 식별자
     * @return 등록 순서로 정렬한 첨부파일 요약 목록
     */
    public List<SourceAttachmentSummaryVo> list(long sourceId);

    /**
     * 첨부 원본 전체 또는 요청 범위를 열어 반환한다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @param range 단일 HTTP Range 헤더 값. 전체 요청이면 null
     * @return 첨부 메타데이터와 열어 둔 원본 스트림
     * @throws com.moon.catchup.common.http.ApiException 첨부파일이 없거나 범위가 유효하지 않거나 원본 저장소에서 읽을 수 없는 경우
     */
    public ReadableAttachment deliver(long sourceId, UUID attachmentId, String range);
}
