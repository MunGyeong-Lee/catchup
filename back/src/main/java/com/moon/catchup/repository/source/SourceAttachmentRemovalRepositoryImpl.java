package com.moon.catchup.repository.source;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;

/**
 * 첨부 행을 삭제하고 현재 JPA 트랜잭션에서 원본 키를 읽는다.
 *
 * <p>PostgreSQL의 {@code DELETE RETURNING}으로 두 동작을 하나의 SQL 문에서 수행한다.
 * Spring Data의 수정 쿼리는 영향 행 수를 반환하므로 결과 행을 읽는 커스텀 저장소를 사용한다.
 */
@RequiredArgsConstructor
public class SourceAttachmentRemovalRepositoryImpl implements SourceAttachmentRemovalRepository {
    private final EntityManager entityManager;

    /**
     * 소스와 첨부 ID가 일치하는 행의 삭제 결과에서 객체 키를 읽는다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @return 삭제된 첨부 원본 키 또는 일치하는 행이 없으면 빈 값
     */
    @Override
    public Optional<String> remove(long sourceId, UUID attachmentId) {
        return this.entityManager.unwrap(Session.class).createNativeQuery("""
                DELETE FROM attachments WHERE source_id = :sourceId AND attachment_id = :attachmentId
                RETURNING object_key
                """, String.class)
                .setParameter("sourceId", sourceId)
                .setParameter("attachmentId", attachmentId)
                .getResultList().stream().findFirst();
    }

    /**
     * 소스 첨부 전체를 삭제한 결과에서 객체 키 목록을 읽는다.
     *
     * @param sourceId 소스 식별자
     * @return 조회 또는 삭제한 S3 객체 키 목록
     */
    @Override
    public List<String> removeAll(long sourceId) {
        return this.entityManager.unwrap(Session.class).createNativeQuery("""
                DELETE FROM attachments WHERE source_id = :sourceId RETURNING object_key
                """, String.class)
                .setParameter("sourceId", sourceId)
                .getResultList();
    }
}
