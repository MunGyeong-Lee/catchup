package com.moon.catchup.domain.source;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 소스 수정에 필요한 저장된 입력값과 첨부 목록이다. */
@Getter
@AllArgsConstructor
public final class SourceEditVo {
    private final long sourceId;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final SourceDetailsVo details;
    private final List<SourceAttachmentSummaryVo> attachments;

    /**
     * 소스 엔티티와 첨부파일 목록을 수정 화면용 VO로 변환한다.
     *
     * @param source DB에서 조회한 소스 엔티티
     * @param attachmentList 소스에 연결된 첨부파일 요약 목록
     * @return 소스 수정 화면용 VO
     */
    public static SourceEditVo from(SourceEntity source,
                                        List<SourceAttachmentSummaryVo> attachmentList) {
        return new SourceEditVo(source.getId(), source.getCreatedAt(), source.getUpdatedAt(), SourceDetailsVo.from(source), attachmentList);
    }
}
