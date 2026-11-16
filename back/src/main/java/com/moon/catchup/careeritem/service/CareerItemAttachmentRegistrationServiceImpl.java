package com.moon.catchup.careeritem.service;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.moon.catchup.careeritem.domain.CareerItemDetailsVo;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 첨부 등록·수정에서 파일을 검증하고 DB 업로드 예약과 S3 원본 저장 흐름을 조율한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareerItemAttachmentRegistrationServiceImpl implements CareerItemAttachmentRegistrationService {
    private final CareerItemAttachmentRepository careerItemAttachmentRepository;
    private final CareerItemAttachmentTransactionService attachmentTransactionService;
    private final CareerItemServiceImpl careerItemService;
    private final CareerItemAttachmentValidator attachmentValidator;

    /** 업로드 파일을 검증하고 등록 실패를 공통 업무 오류로 전달한다. */
    @Override
    public long registerUpload(UUID requestId, String type, Map<String, Object> details,
                                                         List<MultipartFile> files) {
        try {
            return register(requestId, type, details, validateFiles(files));
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** 새 첨부 파일이 있는 수정에서 등록과 같은 검증 및 실패 복구를 사용한다. */
    public long updateUpload(UUID requestId, long careerItemId, String type, Map<String, Object> details,
                      List<UUID> removedIds, List<MultipartFile> files) {
        List<CareerItemAttachmentValidator.File> checkedFiles = validateFiles(files);
        CareerItemDetailsVo careerItem = this.careerItemService.validate(type, details);
        try {
            this.attachmentTransactionService.createEditAttempt(requestId, newKeys(checkedFiles.size()), careerItemId);
            return this.attachmentTransactionService.storeEdit(requestId, careerItemId, careerItem, removedIds, checkedFiles);
        } catch (RuntimeException exception) {
            try {
                Long completedId = this.attachmentTransactionService.cleanupEdit(requestId, careerItemId);
                if (completedId != null) return completedId;
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            if (exception instanceof ApiException apiException) throw apiException;
            if (exception instanceof DataAccessException) throw exception;
            log.warn("Attachment edit failed: requestId={}", requestId, exception);
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** 등록과 수정에서 추가할 파일의 형식과 크기를 동일하게 검증한다. */
    private List<CareerItemAttachmentValidator.File> validateFiles(List<MultipartFile> files) {
        try {
            if (files.isEmpty()) throw new IllegalArgumentException("첨부 파일을 선택해 주세요.");
            return this.attachmentValidator.validate(files);
        } catch (CareerItemAttachmentValidator.SizeLimitExceededException exception) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation(Map.of("attachments", exception.getMessage()));
        } catch (UncheckedIOException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** 저장 키를 새로 발급해 이전 원본의 키를 재사용하지 않는다. */
    private static List<String> newKeys(int size) {
        return IntStream.range(0, size).mapToObj(index -> UUID.randomUUID().toString()).toList();
    }

    /** 자료를 검증하고 업로드 시도를 기록한 뒤 등록 트랜잭션을 실행한다. */
    public long register(UUID requestId, String type, Map<String, Object> details,
                                                 List<CareerItemAttachmentValidator.File> files) {
        CareerItemDetailsVo careerItem = this.careerItemService.validate(type, details);
        List<String> keys = newKeys(files.size());
        try {
            this.attachmentTransactionService.createAttempt(requestId, keys);
            return this.attachmentTransactionService.store(requestId, careerItem, files);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            try {
                Long completedId = this.attachmentTransactionService.cleanup(requestId, false);
                if (completedId != null) return completedId;
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            log.warn("Attachment registration failed: requestId={}", requestId, exception);
            throw new IllegalStateException("Attachment registration failed", exception);
        }
    }

    /**
     * 이전 실행이 끝난 뒤 60초 간격으로 DB에서 생성 후 5분이 지난 미완료 업로드를 조회한다.
     * 처리 중인 요청은 건너뛰고, 나머지 요청에 예약된 S3 원본과 DB 기록의 정리를 재시도한다.
     */
    @Scheduled(fixedDelay = 60000)
    public void recoverAbandonedAttempts() {
        for (UUID request : this.careerItemAttachmentRepository.expiredAttempts()) {
            try {
                this.attachmentTransactionService.cleanup(request, true);
            } catch (RuntimeException exception) {
                log.warn("Attachment cleanup will retry: requestId={}", request, exception);
            }
        }
    }
}
