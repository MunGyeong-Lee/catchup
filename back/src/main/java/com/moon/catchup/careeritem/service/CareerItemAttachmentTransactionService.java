package com.moon.catchup.careeritem.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

import com.moon.catchup.careeritem.domain.CareerItemAttachmentEntity;
import com.moon.catchup.careeritem.domain.CareerItemDetailsVo;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository.UploadAttempt;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.careeritem.storage.CareerItemAttachmentStorage;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** DB에 업로드 예약을 기록하고, S3 원본 저장 및 첨부 메타데이터 확정과 실패 복구를 각각 별도 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
public class CareerItemAttachmentTransactionService {
    private final CareerItemAttachmentRepository repository;
    private final CareerItemServiceImpl careerItems;
    private final CareerItemAttachmentStorage storage;

    /** 업로드 전에 요청 식별자와 객체 키를 별도 트랜잭션에 기록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAttempt(UUID requestId, List<String> keys) {
        this.repository.createAttempt(requestId, keys);
    }

    /** 수정 대상 자료를 포함해 업로드 요청을 예약한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createEditAttempt(UUID requestId, List<String> keys, long careerItemId) {
        this.repository.createAttempt(requestId, keys, careerItemId);
    }

    /** 등록 시도를 잠그고 파일을 업로드한 뒤 자료와 첨부의 저장을 완료한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long store(UUID requestId, CareerItemDetailsVo careerItem, List<CareerItemAttachmentValidator.File> files) {
        return storeFiles(requestId, null, careerItem, List.of(), files);
    }

    /** 수정용 업로드와 자료 및 첨부 변경을 함께 확정한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long storeEdit(UUID requestId, long id, CareerItemDetailsVo details, List<UUID> removedIds,
                          List<CareerItemAttachmentValidator.File> files) {
        return storeFiles(requestId, id, details, removedIds, files);
    }

    /** 같은 요청의 재실행을 막고 원본을 먼저 저장한 뒤 첨부 정보를 확정한다. */
    private long storeFiles(UUID requestId, Long target, CareerItemDetailsVo details, List<UUID> removedIds,
                            List<CareerItemAttachmentValidator.File> files) {
        UploadAttempt attempt = this.repository.lockAttempt(requestId, false)
                .orElseThrow(() -> new IllegalStateException("Upload attempt was cleared"));
        requireTarget(attempt, target);
        if (attempt.isCompleted()) return requireCompletedItem(attempt);

        List<String> keys = attempt.getObjectKeys();
        if (keys.size() != files.size()) throw new ApiException(ErrorCode.INVALID_REQUEST);
        if (target != null) this.careerItems.lock(target);
        uploadFiles(keys, files);

        long careerItemId;
        if (target == null) {
            careerItemId = this.careerItems.insert(details);
        } else {
            this.careerItems.modify(target, details, removedIds);
            careerItemId = target;
        }
        for (int index = 0; index < keys.size(); index++) {
            CareerItemAttachmentValidator.File file = files.get(index);
            this.repository.insert(careerItemId, new CareerItemAttachmentEntity(UUID.randomUUID(), file.getName(),
                    file.getType(), file.getContent().getSize()), keys.get(index));
        }
        this.repository.completeAttempt(requestId, careerItemId);
        return careerItemId;
    }

    /** DB에 예약한 키로 검증된 첨부 파일을 S3에 업로드한다. */
    private void uploadFiles(List<String> keys, List<CareerItemAttachmentValidator.File> files) {
        for (int index = 0; index < keys.size(); index++) {
            CareerItemAttachmentValidator.File file = files.get(index);
            try (InputStream input = file.getContent().getInputStream()) {
                this.storage.put(keys.get(index), file.getType(), file.getContent().getSize(), input);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    /** 등록 및 수정 요청 간 요청 식별자 재사용을 거부한다. */
    private static void requireTarget(UploadAttempt attempt, Long target) {
        if (!Objects.equals(attempt.getTargetCareerItemId(), target)) throw new ApiException(ErrorCode.INVALID_REQUEST);
    }

    /** 이미 삭제된 완료 자료를 재등록하지 않도록 자료 식별자의 존재를 확인한다. */
    private static long requireCompletedItem(UploadAttempt attempt) {
        if (attempt.getCareerItemId() == null) throw new ApiException(ErrorCode.CAREER_ITEM_NOT_FOUND);
        return attempt.getCareerItemId();
    }

    /** DB에서 업로드 요청 행을 잠그고, 미완료 요청에 예약된 S3 원본과 DB 기록을 정리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long cleanup(UUID requestId, boolean skipActive) {
        return cleanupAttempt(requestId, skipActive, null);
    }

    /** 수정 요청이 확정되었는지 확인하거나 이 수정에서 예약한 새 객체만 정리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long cleanupEdit(UUID requestId, long careerItemId) {
        return cleanupAttempt(requestId, false, careerItemId);
    }

    /** 요청 소유권을 확인한 뒤 트랜잭션에서 업로드 예약을 정리한다. */
    private Long cleanupAttempt(UUID requestId, boolean skipActive, Long target) {
        Optional<UploadAttempt> attempts = this.repository.lockAttempt(requestId, skipActive);
        if (attempts.isEmpty()) return null;

        UploadAttempt attempt = attempts.get();
        if (!skipActive) requireTarget(attempt, target);
        if (attempt.isCompleted()) return requireCompletedItem(attempt);

        for (String key : attempt.getObjectKeys()) this.storage.delete(key);
        this.repository.deleteAttempt(requestId);
        return null;
    }
}
