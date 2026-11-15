package com.moon.catchup.careeritem.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.moon.catchup.careeritem.domain.CareerItemAttachment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 첨부 메타데이터와 파일 업로드 시도 기록을 PostgreSQL에서 관리한다. */
@Repository
public class CareerItemAttachmentRepository {
    @Getter
    @AllArgsConstructor
    public static final class StoredAttachment {
        private final CareerItemAttachment attachment;
        private final String key;
    }
    @Getter
    @AllArgsConstructor
    public static final class UploadAttempt {
        private final List<String> objectKeys;
        private final Long materialId;
    }

    private static final String LOCK_ATTEMPT = """
            SELECT object_keys, career_item_id FROM upload_attempts
            WHERE request_id = ? FOR UPDATE
            """;

    private final JdbcTemplate jdbc;

    /** 첨부 및 업로드 시도 쿼리를 실행할 데이터베이스 접근 객체를 연결한다. */
    CareerItemAttachmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 객체 업로드 전에 요청 식별자와 저장할 객체 키를 기록한다. */
    public void createAttempt(UUID requestId, List<String> keys) {
        this.jdbc.update("""
                INSERT INTO upload_attempts (request_id, object_keys)
                VALUES (?, ?) ON CONFLICT DO NOTHING
                """, requestId, String.join("\n", keys));
    }

    /** 등록 시도 행을 잠그고, 필요하면 다른 작업이 잠근 행을 건너뛴다. */
    public Optional<UploadAttempt> lockAttempt(UUID requestId, boolean skipActive) {
        return this.jdbc.query(skipActive ? LOCK_ATTEMPT + " SKIP LOCKED" : LOCK_ATTEMPT,
                (row, index) -> new UploadAttempt(row.getString(1).lines().toList(), row.getObject(2, Long.class)),
                requestId).stream().findFirst();
    }

    /** 정리 대상으로 검토할 오래된 미완료 등록 시도 식별자를 반환한다. */
    public List<UUID> expiredAttempts() {
        return this.jdbc.queryForList("""
                SELECT request_id FROM upload_attempts
                WHERE career_item_id IS NULL AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                ORDER BY created_at LIMIT 100
                """, UUID.class);
    }

    /** 등록 시도에 생성된 자료 식별자를 기록해 완료 상태로 표시한다. */
    public void completeAttempt(UUID requestId, long materialId) {
        this.jdbc.update("UPDATE upload_attempts SET career_item_id = ? WHERE request_id = ?", materialId, requestId);
    }

    /** 객체 정리가 끝난 미완료 등록 시도 기록을 삭제한다. */
    public void deleteAttempt(UUID requestId) {
        this.jdbc.update("DELETE FROM upload_attempts WHERE request_id = ?", requestId);
    }

    /** 객체 키와 첨부 메타데이터를 자료 식별자에 연결해 저장한다. */
    public void insert(long materialId, CareerItemAttachment attachment, String key) {
        this.jdbc.update("""
                INSERT INTO attachments
                (attachment_id, career_item_id, original_name, content_type, size_bytes, object_key)
                VALUES (?, ?, ?, ?, ?, ?)
                """, attachment.getId(), materialId, attachment.getName(), attachment.getContentType(), attachment.getSize(), key);
    }

    /** 자료의 첨부 메타데이터를 등록 순서로 조회한다. */
    public List<CareerItemAttachment> list(long materialId) {
        return this.jdbc.query("""
                SELECT attachment_id, original_name, content_type, size_bytes FROM attachments
                WHERE career_item_id = ? ORDER BY created_at, attachment_id
                """, (row, index) -> new CareerItemAttachment(row.getObject(1, UUID.class), row.getString(2),
                row.getString(3), row.getLong(4)), materialId);
    }

    /** 자료와 첨부 식별자가 모두 일치하는 첨부 메타데이터와 저장 키를 조회한다. */
    public Optional<StoredAttachment> find(long materialId, UUID attachmentId) {
        return this.jdbc.query("""
                SELECT attachment_id, original_name, content_type, size_bytes, object_key
                FROM attachments WHERE career_item_id = ? AND attachment_id = ?
                """, (row, index) -> new StoredAttachment(new CareerItemAttachment(row.getObject(1, UUID.class),
                row.getString(2), row.getString(3), row.getLong(4)), row.getString(5)), materialId, attachmentId)
                .stream().findFirst();
    }
}
