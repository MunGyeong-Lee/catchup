package com.moon.catchup.repository.source;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.moon.catchup.domain.source.SourceAttachmentEntity;
import com.moon.catchup.domain.source.SourceAttachmentSummaryVo;
import com.moon.catchup.domain.source.StoredAttachmentVo;
import com.moon.catchup.domain.source.UploadAttemptVo;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 첨부 메타데이터와 업로드 예약을 DB에서 관리한다. */
public interface SourceAttachmentRepository
        extends Repository<SourceAttachmentEntity, UUID>, SourceAttachmentRemovalRepository {
    /**
     * 목록 페이지의 소스 중 첨부 메타데이터가 존재하는 식별자만 한 번에 조회한다.
     *
     * @param sourceIdList 비어 있지 않은 목록 페이지의 소스 식별자 목록
     * @return 첨부가 있는 소스의 중복 없는 식별자 집합
     */
    @Query("select distinct attachment.sourceId from SourceAttachmentEntity attachment where attachment.sourceId in :sourceIdList")
    public Set<Long> findSourceIdsWithAttachments(@Param("sourceIdList") List<Long> sourceIdList);

    /**
     * 업로드를 시작하기 전에 객체 키를 예약한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param keys 첨부 원본을 가리키는 S3 객체 키 목록
     */
    public default void createAttempt(UUID requestId, List<String> keys) {
        createAttempt(requestId, keys, null);
    }

    /**
     * 수정 대상과 예약 키를 기존 저장 형식으로 기록한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param keys 첨부 원본을 가리키는 S3 객체 키 목록
     * @param targetSourceId 수정 대상 소스 식별자. 등록이면 null
     */
    public default void createAttempt(UUID requestId, List<String> keys, Long targetSourceId) {
        insertAttempt(requestId, String.join("\n", keys), targetSourceId);
    }

    /**
     * 같은 요청을 다시 예약할 때 최초 예약을 보존한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param objectKeys 줄바꿈으로 구분한 예약 객체 키
     * @param targetSourceId 수정 대상 소스 식별자. 등록이면 null
     */
    @Modifying
    @NativeQuery("""
            INSERT INTO upload_attempts (request_id, object_keys, target_source_id)
            VALUES (:requestId, :objectKeys, :targetSourceId) ON CONFLICT DO NOTHING
            """)
    public void insertAttempt(@Param("requestId") UUID requestId, @Param("objectKeys") String objectKeys,
                              @Param("targetSourceId") Long targetSourceId);

    /**
     * 일반 처리에서는 잠금을 기다리고 복구 작업에서는 이미 잠긴 예약을 건너뛴다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param skipActive 다른 트랜잭션이 잠근 행을 건너뛸지 여부
     * @return 잠근 업로드 예약 또는 잠글 대상이 없으면 빈 값
     */
    public default Optional<UploadAttemptVo> lockAttempt(UUID requestId, boolean skipActive) {
        Optional<AttemptRow> attempt = skipActive ? findUnlockedAttempt(requestId) : findAttemptForUpdate(requestId);
        return attempt.map(row -> UploadAttemptVo.of(
                row.getObjectKeys().lines().toList(), row.getSourceId(), row.getTargetSourceId(), row.isCompleted()));
    }

    /**
     * 같은 업로드 요청을 동시에 실행하지 않도록 예약 행을 잠근다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @return 잠근 업로드 예약 행 또는 잠글 대상이 없으면 빈 값
     */
    @NativeQuery("""
            SELECT object_keys AS "objectKeys", source_id AS "sourceId", target_source_id AS "targetSourceId",
                   (completed_at IS NOT NULL OR source_id IS NOT NULL) AS completed
            FROM upload_attempts WHERE request_id = :requestId FOR UPDATE
            """)
    public Optional<AttemptRow> findAttemptForUpdate(@Param("requestId") UUID requestId);

    /**
     * 이미 잠긴 예약을 기다리지 않고 복구할 수 있는 예약만 잠근다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @return 잠근 업로드 예약 행 또는 잠글 대상이 없으면 빈 값
     */
    @NativeQuery("""
            SELECT object_keys AS "objectKeys", source_id AS "sourceId", target_source_id AS "targetSourceId",
                   (completed_at IS NOT NULL OR source_id IS NOT NULL) AS completed
            FROM upload_attempts WHERE request_id = :requestId FOR UPDATE SKIP LOCKED
            """)
    public Optional<AttemptRow> findUnlockedAttempt(@Param("requestId") UUID requestId);

    /**
     * 5분이 지난 미완료 예약을 오래된 순서대로 최대 100개 조회한다.
     *
     * @return 오래된 순서의 미완료 요청 식별자 목록
     */
    @NativeQuery("""
            SELECT request_id FROM upload_attempts
            WHERE completed_at IS NULL AND source_id IS NULL AND created_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'
            ORDER BY created_at LIMIT 100
            """)
    public List<UUID> expiredAttempts();

    /**
     * 업로드로 저장된 소스와 완료 시각을 예약에 기록한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param sourceId 소스 식별자
     */
    @Modifying
    @NativeQuery("""
            UPDATE upload_attempts SET source_id = :sourceId, completed_at = CURRENT_TIMESTAMP
            WHERE request_id = :requestId
            """)
    public void completeAttempt(@Param("requestId") UUID requestId, @Param("sourceId") long sourceId);

    /**
     * S3 객체 정리가 끝난 예약을 삭제한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     */
    @Modifying
    @NativeQuery("DELETE FROM upload_attempts WHERE request_id = :requestId")
    public void deleteAttempt(@Param("requestId") UUID requestId);

    /**
     * 소스에 첨부 메타데이터와 S3 객체 키를 연결한다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @param name 첨부파일의 원본 이름
     * @param contentType 첨부파일 콘텐츠 유형
     * @param size 원본의 바이트 수
     * @param objectKey 첨부 원본의 S3 객체 키
     */
    @Modifying
    @NativeQuery("""
            INSERT INTO attachments (attachment_id, source_id, original_name, content_type, size_bytes, object_key)
            VALUES (:attachmentId, :sourceId, :name, :contentType, :size, :objectKey)
            """)
    public void insert(@Param("sourceId") long sourceId, @Param("attachmentId") UUID attachmentId,
                       @Param("name") String name, @Param("contentType") String contentType,
                       @Param("size") long size, @Param("objectKey") String objectKey);

    /**
     * 완료 시각을 보존하며 삭제할 소스와 완료 예약의 연결을 해제한다.
     *
     * @param sourceId 소스 식별자
     */
    @Modifying
    @NativeQuery("""
            UPDATE upload_attempts SET source_id = NULL, completed_at = COALESCE(completed_at, created_at)
            WHERE source_id = :sourceId
            """)
    public void detachCompletedAttempts(@Param("sourceId") long sourceId);

    /**
     * 정리 작업에서 보호할 첨부와 미완료 예약의 키를 한 스냅샷으로 읽는다.
     *
     * @return 정리에서 보호할 저장 및 미완료 예약 객체 키
     */
    @NativeQuery("""
            SELECT object_key FROM attachments
            UNION
            SELECT unnest(string_to_array(object_keys, chr(10))) FROM upload_attempts
            WHERE completed_at IS NULL AND source_id IS NULL
            """)
    public Set<String> protectedKeys();

    /**
     * 등록 순서대로 읽은 첨부 엔티티를 목록 조회용 값으로 변환한다.
     *
     * @param sourceId 소스 식별자
     * @return 등록 순서로 정렬한 첨부파일 요약 목록
     */
    public default List<SourceAttachmentSummaryVo> list(long sourceId) {
        return getAttachments(sourceId).stream().map(SourceAttachmentSummaryVo::from).toList();
    }

    /**
     * 소스에 속한 첨부를 등록 시각과 식별자 순서로 조회한다.
     *
     * @param sourceId 소스 식별자
     * @return 등록 순서로 정렬한 첨부파일 엔티티 목록
     */
    @Query("""
            SELECT attachment FROM SourceAttachmentEntity attachment WHERE attachment.sourceId = :sourceId
            ORDER BY attachment.createdAt, attachment.id
            """)
    public List<SourceAttachmentEntity> getAttachments(@Param("sourceId") long sourceId);

    /**
     * 소스에 속한 첨부의 메타데이터와 S3 저장 키를 반환한다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @return 조회한 첨부 메타데이터와 객체 키 또는 일치하는 행이 없으면 빈 값
     */
    public default Optional<StoredAttachmentVo> find(long sourceId, UUID attachmentId) {
        return getAttachment(sourceId, attachmentId).map(StoredAttachmentVo::from);
    }

    /**
     * 소스와 첨부 식별자가 모두 일치하는 엔티티를 조회한다.
     *
     * @param sourceId 소스 식별자
     * @param attachmentId 첨부파일 식별자
     * @return 조회한 첨부 엔티티 또는 일치하는 행이 없으면 빈 값
     */
    @Query("""
            SELECT attachment FROM SourceAttachmentEntity attachment
            WHERE attachment.sourceId = :sourceId AND attachment.id = :attachmentId
            """)
    public Optional<SourceAttachmentEntity> getAttachment(
            @Param("sourceId") long sourceId, @Param("attachmentId") UUID attachmentId);

    /** 잠근 업로드 예약의 예약 키와 완료 상태를 읽는 프로젝션이다. */
    public interface AttemptRow {
        /**
         * 예약된 원본 키를 읽는다.
         *
         * @return 줄바꿈으로 구분한 원본 키
         */
        public String getObjectKeys();

        /**
         * 예약으로 확정한 소스를 읽는다.
         *
         * @return 확정한 소스의 식별자 또는 미완료이거나 소스가 삭제됐으면 null
         */
        public Long getSourceId();

        /**
         * 수정 요청이 대상으로 지정한 소스를 읽는다.
         *
         * @return 수정 대상 식별자 또는 등록 요청이면 null
         */
        public Long getTargetSourceId();

        /**
         * 이전 요청의 처리 완료 여부를 확인한다.
         *
         * @return 완료 시각이나 확정한 소스 식별자가 기록되어 있으면 true
         */
        public boolean isCompleted();
    }
}
