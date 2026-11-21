package com.moon.catchup.service.source;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.UploadAttemptVo;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.storage.source.SourceAttachmentStorage;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** DB에 업로드 예약을 기록하고, S3 원본 저장 및 첨부 메타데이터 확정과 실패 복구를 각각 별도 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
public class SourceAttachmentTransactionService {
    private final SourceAttachmentRepository sourceAttachmentRepository;
    private final SourceServiceImpl sourceService;
    private final SourceAttachmentStorage sourceAttachmentStorage;

    /**
     * 업로드 전에 요청 식별자와 객체 키를 별도 트랜잭션에 기록한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param keys 첨부 원본을 가리키는 S3 객체 키 목록
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAttempt(UUID requestId, List<String> keys) {
        this.sourceAttachmentRepository.createAttempt(requestId, keys);
    }

    /**
     * 수정 대상 소스를 포함해 업로드 요청을 예약한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param keys 첨부 원본을 가리키는 S3 객체 키 목록
     * @param sourceId 소스 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createEditAttempt(UUID requestId, List<String> keys, long sourceId) {
        this.sourceAttachmentRepository.createAttempt(requestId, keys, sourceId);
    }

    /**
     * 등록 시도를 잠그고 파일을 업로드한 뒤 소스와 첨부의 저장을 완료한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param source 변환하거나 저장할 소스
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long store(UUID requestId, SourceDetailsVo source, List<SourceAttachmentValidator.File> files) {
        return storeFiles(requestId, null, source, List.of(), files);
    }

    /**
     * 수정용 업로드와 소스 및 첨부 변경을 함께 확정한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param id 소스 식별자
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long storeEdit(UUID requestId, long id, SourceDetailsVo details, List<UUID> removedIds,
                          List<SourceAttachmentValidator.File> files) {
        return storeFiles(requestId, id, details, removedIds, files);
    }

    /**
     * 같은 요청의 재실행을 막고 원본을 먼저 저장한 뒤 첨부 정보를 확정한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param target 수정 대상 소스 식별자. 등록이면 null
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     */
    private long storeFiles(UUID requestId, Long target, SourceDetailsVo details, List<UUID> removedIds,
                            List<SourceAttachmentValidator.File> files) {
        UploadAttemptVo attempt = this.sourceAttachmentRepository.lockAttempt(requestId, false)
                .orElseThrow(() -> new IllegalStateException("Upload attempt was cleared"));
        requireTarget(attempt, target);
        if (attempt.isCompleted()) return requireCompletedItem(attempt);

        List<String> keys = attempt.getObjectKeys();
        if (keys.size() != files.size()) throw new ApiException(ErrorCode.INVALID_REQUEST);
        if (target != null) this.sourceService.lock(target);
        uploadFiles(keys, files);

        long sourceId;
        if (target == null) {
            sourceId = this.sourceService.insert(details);
        } else {
            this.sourceService.modify(target, details, removedIds, !files.isEmpty());
            sourceId = target;
        }
        for (int index = 0; index < keys.size(); index++) {
            SourceAttachmentValidator.File file = files.get(index);
            this.sourceAttachmentRepository.insert(sourceId, UUID.randomUUID(), file.getName(),
                    file.getType(), file.getContent().getSize(), keys.get(index));
        }
        this.sourceAttachmentRepository.completeAttempt(requestId, sourceId);
        return sourceId;
    }

    /**
     * DB에 예약한 키로 검증된 첨부 파일을 S3에 업로드한다.
     *
     * @param keys 첨부 원본을 가리키는 S3 객체 키 목록
     * @param files 요청에 포함된 첨부파일 목록
     */
    private void uploadFiles(List<String> keys, List<SourceAttachmentValidator.File> files) {
        for (int index = 0; index < keys.size(); index++) {
            SourceAttachmentValidator.File file = files.get(index);
            try (InputStream input = file.getContent().getInputStream()) {
                this.sourceAttachmentStorage.put(keys.get(index), file.getType(), file.getContent().getSize(), input);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    /**
     * 등록 및 수정 요청 간 요청 식별자 재사용을 거부한다.
     *
     * @param attempt 현재 트랜잭션에서 잠근 업로드 예약
     * @param target 수정 대상 소스 식별자. 등록이면 null
     * @throws ApiException 등록·수정 구분이나 수정 대상이 예약과 다른 경우
     */
    private static void requireTarget(UploadAttemptVo attempt, Long target) {
        if (!Objects.equals(attempt.getTargetSourceId(), target)) throw new ApiException(ErrorCode.INVALID_REQUEST);
    }

    /**
     * 이미 삭제된 완료 소스를 재등록하지 않도록 소스 식별자의 존재를 확인한다.
     *
     * @param attempt 현재 트랜잭션에서 잠근 업로드 예약
     * @return 저장된 소스 식별자
     * @throws ApiException 완료된 요청의 소스가 삭제된 경우
     */
    private static long requireCompletedItem(UploadAttemptVo attempt) {
        if (attempt.getSourceId() == null) throw new ApiException(ErrorCode.SOURCE_NOT_FOUND);
        return attempt.getSourceId();
    }

    /**
     * DB에서 업로드 요청 행을 잠그고, 미완료 요청에 예약된 S3 원본과 DB 기록을 정리한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param skipActive 다른 트랜잭션이 잠근 행을 건너뛸지 여부
     * @return 이미 완료된 소스 식별자 또는 정리하지 않았거나 정리를 마치면 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long cleanup(UUID requestId, boolean skipActive) {
        return cleanupAttempt(requestId, skipActive, null);
    }

    /**
     * 수정 요청이 확정되었는지 확인하거나 이 수정에서 예약한 새 객체만 정리한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param sourceId 소스 식별자
     * @return 이미 완료된 수정 대상 소스 식별자 또는 정리 후 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long cleanupEdit(UUID requestId, long sourceId) {
        return cleanupAttempt(requestId, false, sourceId);
    }

    /**
     * 요청 소유권을 확인한 뒤 트랜잭션에서 업로드 예약을 정리한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param skipActive 다른 트랜잭션이 잠근 행을 건너뛸지 여부
     * @param target 수정 대상 소스 식별자. 등록이면 null
     * @return 이미 완료된 소스 식별자 또는 정리 후 null
     */
    private Long cleanupAttempt(UUID requestId, boolean skipActive, Long target) {
        Optional<UploadAttemptVo> attempts = this.sourceAttachmentRepository.lockAttempt(requestId, skipActive);
        if (attempts.isEmpty()) return null;

        UploadAttemptVo attempt = attempts.get();
        if (!skipActive) requireTarget(attempt, target);
        if (attempt.isCompleted()) return requireCompletedItem(attempt);

        for (String key : attempt.getObjectKeys()) this.sourceAttachmentStorage.delete(key);
        this.sourceAttachmentRepository.deleteAttempt(requestId);
        return null;
    }
}
