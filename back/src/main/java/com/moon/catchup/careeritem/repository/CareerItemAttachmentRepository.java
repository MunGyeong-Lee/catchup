package com.moon.catchup.careeritem.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import com.moon.catchup.careeritem.domain.CareerItemAttachmentEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 첨부 메타데이터와 파일 업로드 시도 기록을 DB에서 관리한다. */
@Repository
@RequiredArgsConstructor
public class CareerItemAttachmentRepository {
    @Getter
    @AllArgsConstructor
    public static final class StoredAttachment {
        private final CareerItemAttachmentEntity attachment;
        private final String key;
    }
    @Getter
    @AllArgsConstructor
    public static final class UploadAttempt {
        private final List<String> objectKeys;
        private final Long careerItemId;
        private final Long targetCareerItemId;
        private final boolean completed;

        /** 기존 등록 흐름과 같은 방식으로 등록 시도 상태를 생성한다. */
        public UploadAttempt(List<String> objectKeys, Long careerItemId) {
            this(objectKeys, careerItemId, null, careerItemId != null);
        }
    }

    private static final String LOCK_ATTEMPT = """
            SELECT object_keys, career_item_id, target_career_item_id, completed_at FROM upload_attempts
            WHERE request_id = ? FOR UPDATE
            """;

    private final JdbcTemplate jdbc;

    /** 객체 업로드 전에 요청 식별자와 저장할 객체 키를 기록한다. */
    public void createAttempt(UUID requestId, List<String> keys) {
        createAttempt(requestId, keys, null);
    }

    /** 수정 대상 자료를 함께 기록하고 이미 예약한 요청 식별자는 유지한다. */
    public void createAttempt(UUID requestId, List<String> keys, Long targetCareerItemId) {
        this.jdbc.update("""
                INSERT INTO upload_attempts (request_id, object_keys, target_career_item_id)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, requestId, String.join("\n", keys), targetCareerItemId);
    }

    /** 등록 시도 행을 잠그고, 필요하면 다른 작업이 잠근 행을 건너뛴다. */
    public Optional<UploadAttempt> lockAttempt(UUID requestId, boolean skipActive) {
        return this.jdbc.query(skipActive ? LOCK_ATTEMPT + " SKIP LOCKED" : LOCK_ATTEMPT,
                (row, index) -> new UploadAttempt(row.getString(1).lines().toList(), row.getObject(2, Long.class),
                        row.getObject(3, Long.class), row.getObject(4) != null || row.getObject(2) != null),
                requestId).stream().findFirst();
    }

    /** DB에서 생성 후 5분이 지난 미완료 업로드 요청 ID를 오래된 순서대로 최대 100개 조회한다. */
    public List<UUID> expiredAttempts() {
        return this.jdbc.queryForList("""
                SELECT request_id FROM upload_attempts
                WHERE completed_at IS NULL AND career_item_id IS NULL AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                ORDER BY created_at LIMIT 100
                """, UUID.class);
    }

    /** 등록 시도에 생성된 자료 식별자를 기록해 완료 상태로 표시한다. */
    public void completeAttempt(UUID requestId, long careerItemId) {
        this.jdbc.update("UPDATE upload_attempts SET career_item_id = ?, completed_at = CURRENT_TIMESTAMP WHERE request_id = ?", careerItemId, requestId);
    }

    /** 예약된 S3 원본을 정리한 뒤 DB의 upload_attempts에서 요청 기록을 삭제한다. */
    public void deleteAttempt(UUID requestId) {
        this.jdbc.update("DELETE FROM upload_attempts WHERE request_id = ?", requestId);
    }

    /** 객체 키와 첨부 메타데이터를 자료 식별자에 연결해 저장한다. */
    public void insert(long careerItemId, CareerItemAttachmentEntity attachment, String key) {
        this.jdbc.update("""
                INSERT INTO attachments
                (attachment_id, career_item_id, original_name, content_type, size_bytes, object_key)
                VALUES (?, ?, ?, ?, ?, ?)
                """, attachment.getId(), careerItemId, attachment.getName(), attachment.getContentType(), attachment.getSize(), key);
    }

    /** 완료 요청의 재실행을 막는 완료 시점은 보존하고 삭제할 자료와의 외래 키만 제거한다. */
    public void detachCompletedAttempts(long careerItemId) {
        this.jdbc.update("""
                UPDATE upload_attempts SET career_item_id = NULL, completed_at = COALESCE(completed_at, created_at)
                WHERE career_item_id = ?
                """, careerItemId);
    }

    /** 이 자료에 속하는 첨부 하나를 제거하고 삭제할 원본 키를 반환한다. */
    public Optional<String> remove(long careerItemId, UUID attachmentId) {
        return this.jdbc.queryForList("""
                DELETE FROM attachments WHERE career_item_id = ? AND attachment_id = ? RETURNING object_key
                """, String.class, careerItemId, attachmentId).stream().findFirst();
    }

    /** DB에서 자료에 속한 attachments 행을 모두 삭제하고 이후 S3에서 삭제할 객체 키를 반환한다. */
    public List<String> removeAll(long careerItemId) {
        return this.jdbc.queryForList("""
                DELETE FROM attachments WHERE career_item_id = ? RETURNING object_key
                """, String.class, careerItemId);
    }

    /** S3 일일 정리에서 보호할 키를 DB의 attachments와 미완료 upload_attempts에서 한 쿼리로 조회한다. */
    public Set<String> protectedKeys() {
        return Set.copyOf(this.jdbc.queryForList("""
                SELECT object_key FROM attachments
                UNION
                SELECT unnest(string_to_array(object_keys, chr(10))) FROM upload_attempts
                WHERE completed_at IS NULL AND career_item_id IS NULL
                """, String.class));
    }

    /** 자료의 첨부 메타데이터를 등록 순서로 조회한다. */
    public List<CareerItemAttachmentEntity> list(long careerItemId) {
        return this.jdbc.query("""
                SELECT attachment_id, original_name, content_type, size_bytes FROM attachments
                WHERE career_item_id = ? ORDER BY created_at, attachment_id
                """, (row, index) -> new CareerItemAttachmentEntity(row.getObject(1, UUID.class), row.getString(2),
                row.getString(3), row.getLong(4)), careerItemId);
    }

    /** 자료와 첨부 식별자가 모두 일치하는 첨부 메타데이터와 저장 키를 조회한다. */
    public Optional<StoredAttachment> find(long careerItemId, UUID attachmentId) {
        return this.jdbc.query("""
                SELECT attachment_id, original_name, content_type, size_bytes, object_key
                FROM attachments WHERE career_item_id = ? AND attachment_id = ?
                """, (row, index) -> new StoredAttachment(new CareerItemAttachmentEntity(row.getObject(1, UUID.class),
                row.getString(2), row.getString(3), row.getLong(4)), row.getString(5)), careerItemId, attachmentId)
                .stream().findFirst();
    }
}
