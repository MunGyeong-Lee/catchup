package com.moon.catchup.repository.source;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 첨부 행을 삭제하는 SQL 문에서 후속 S3 정리에 필요한 키를 읽는 저장소 계약이다. */
public interface SourceAttachmentRemovalRepository {
    /**
     * 소스에 속하는 첨부를 삭제하고 원본 키를 반환한다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @return 삭제된 첨부 원본 키 또는 일치하는 행이 없으면 빈 값
     */
    public Optional<String> remove(long sourceId, UUID attachmentId);

    /**
     * 소스에 속한 첨부를 모두 삭제하고 삭제된 원본 키를 반환한다.
     *
     * @param sourceId 소스 식별자
     * @return 조회 또는 삭제한 S3 객체 키 목록
     */
    public List<String> removeAll(long sourceId);
}
