package com.moon.catchup.careeritem.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import com.moon.catchup.careeritem.domain.CareerItemAttachmentEntity;
import com.moon.catchup.careeritem.domain.CareerItemAttachmentSummaryVo;
import com.moon.catchup.careeritem.domain.StoredAttachmentVo;
import com.moon.catchup.careeritem.domain.UploadAttemptVo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 첨부 메타데이터와 파일 업로드 시도 기록을 DB에서 관리한다. */
@Repository
@RequiredArgsConstructor
public class CareerItemAttachmentRepository {
    private static final String LOCK_ATTEMPT = """
            SELECT object_keys, career_item_id, target_career_item_id, completed_at FROM upload_attempts
            WHERE request_id = ? FOR UPDATE
            """;

    private final JdbcTemplate jdbcTemplate;

    /** 객체 업로드 전에 요청 식별자와 저장할 객체 키를 기록한다. */
    public void createAttempt(UUID requestId, List<String> keys) {
        createAttempt(requestId, keys, null);
    }

    /** 수정 대상 자료를 함께 기록하고 이미 예약한 요청 식별자는 유지한다. */
    public void createAttempt(UUID requestId, List<String> keys, Long targetCareerItemId) {
        this.jdbcTemplate.update("""
                INSERT INTO upload_attempts (request_id, object_keys, target_career_item_id)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, requestId, String.join("\n", keys), targetCareerItemId);
    }

    /** 등록 시도 행을 잠그고, 필요하면 다른 작업이 잠근 행을 건너뛴다. */
    public Optional<UploadAttemptVo> lockAttempt(UUID requestId, boolean skipActive) {
        return this.jdbcTemplate.query(skipActive ? LOCK_ATTEMPT + " SKIP LOCKED" : LOCK_ATTEMPT,
                this::convertToUploadAttemptVo, requestId).stream().findFirst();
    }

    /** DB에서 생성 후 5분이 지난 미완료 업로드 요청 ID를 오래된 순서대로 최대 100개 조회한다. */
    public List<UUID> expiredAttempts() {
        return this.jdbcTemplate.queryForList("""
                SELECT request_id FROM upload_attempts
                WHERE completed_at IS NULL AND career_item_id IS NULL AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
                ORDER BY created_at LIMIT 100
                """, UUID.class);
    }

    /** 등록 시도에 생성된 자료 식별자를 기록해 완료 상태로 표시한다. */
    public void completeAttempt(UUID requestId, long careerItemId) {
        this.jdbcTemplate.update("UPDATE upload_attempts SET career_item_id = ?, completed_at = CURRENT_TIMESTAMP WHERE request_id = ?", careerItemId, requestId);
    }

    /** 예약된 S3 원본을 정리한 뒤 DB의 upload_attempts에서 요청 기록을 삭제한다. */
    public void deleteAttempt(UUID requestId) {
        this.jdbcTemplate.update("DELETE FROM upload_attempts WHERE request_id = ?", requestId);
    }

    /** 객체 키와 첨부 메타데이터를 자료 식별자에 연결해 저장한다. */
    public void insert(long careerItemId, CareerItemAttachmentEntity attachment, String key) {
        this.jdbcTemplate.update("""
                INSERT INTO attachments
                (attachment_id, career_item_id, original_name, content_type, size_bytes, object_key)
                VALUES (?, ?, ?, ?, ?, ?)
                """, attachment.getId(), careerItemId, attachment.getName(), attachment.getContentType(), attachment.getSize(), key);
    }

    /** 완료 요청의 재실행을 막는 완료 시점은 보존하고 삭제할 자료와의 외래 키만 제거한다. */
    public void detachCompletedAttempts(long careerItemId) {
        this.jdbcTemplate.update("""
                UPDATE upload_attempts SET career_item_id = NULL, completed_at = COALESCE(completed_at, created_at)
                WHERE career_item_id = ?
                """, careerItemId);
    }

    /** 이 자료에 속하는 첨부 하나를 제거하고 삭제할 원본 키를 반환한다. */
    public Optional<String> remove(long careerItemId, UUID attachmentId) {
        return this.jdbcTemplate.queryForList("""
                DELETE FROM attachments WHERE career_item_id = ? AND attachment_id = ? RETURNING object_key
                """, String.class, careerItemId, attachmentId).stream().findFirst();
    }

    /** DB에서 자료에 속한 attachments 행을 모두 삭제하고 이후 S3에서 삭제할 객체 키를 반환한다. */
    public List<String> removeAll(long careerItemId) {
        return this.jdbcTemplate.queryForList("""
                DELETE FROM attachments WHERE career_item_id = ? RETURNING object_key
                """, String.class, careerItemId);
    }

    /** S3 일일 정리에서 보호할 키를 DB의 attachments와 미완료 upload_attempts에서 한 쿼리로 조회한다. */
    public Set<String> protectedKeys() {
        return Set.copyOf(this.jdbcTemplate.queryForList("""
                SELECT object_key FROM attachments
                UNION
                SELECT unnest(string_to_array(object_keys, chr(10))) FROM upload_attempts
                WHERE completed_at IS NULL AND career_item_id IS NULL
                """, String.class));
    }

    /** 자료의 첨부 메타데이터를 등록 순서로 조회한다. */
    public List<CareerItemAttachmentSummaryVo> list(long careerItemId) {
        return this.jdbcTemplate.query("""
                SELECT attachment_id, original_name, content_type, size_bytes FROM attachments
                WHERE career_item_id = ? ORDER BY created_at, attachment_id
                """, (row, index) -> {
            CareerItemAttachmentEntity attachment = new CareerItemAttachmentEntity(
                    row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getLong(4));
            return CareerItemAttachmentSummaryVo.from(attachment);
        }, careerItemId);
    }

    /** 자료와 첨부 식별자가 모두 일치하는 첨부 메타데이터와 저장 키를 조회한다. */
    public Optional<StoredAttachmentVo> find(long careerItemId, UUID attachmentId) {
        return this.jdbcTemplate.query("""
                SELECT attachment_id, original_name, content_type, size_bytes, object_key
                FROM attachments WHERE career_item_id = ? AND attachment_id = ?
                """, (row, index) -> {
            CareerItemAttachmentEntity attachment = new CareerItemAttachmentEntity(
                    row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getLong(4));
            return StoredAttachmentVo.from(attachment, row.getString(5));
        }, careerItemId, attachmentId)
                .stream().findFirst();
    }

    /** 잠근 업로드 행에서 예약한 S3 키와 자료 ID, 완료 상태를 읽는다. */
    private UploadAttemptVo convertToUploadAttemptVo(ResultSet row, int rowNumber) throws SQLException {
        List<String> objectKeyList = row.getString("object_keys").lines().toList();
        Long careerItemId = row.getObject("career_item_id", Long.class);
        Long targetCareerItemId = row.getObject("target_career_item_id", Long.class);
        boolean completed = row.getObject("completed_at") != null || careerItemId != null;
        return new UploadAttemptVo(objectKeyList, careerItemId, targetCareerItemId, completed);
    }
}
