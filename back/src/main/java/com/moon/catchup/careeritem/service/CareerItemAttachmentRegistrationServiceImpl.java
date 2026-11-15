package com.moon.catchup.careeritem.service;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.moon.catchup.careeritem.domain.CareerItemDetails;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 자료 등록과 첨부 원본 전송을 조율하고 실패한 업로드를 정리한다. */
@Service
@RequiredArgsConstructor
public class CareerItemAttachmentRegistrationServiceImpl implements CareerItemAttachmentRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(CareerItemAttachmentRegistrationServiceImpl.class);
    private final CareerItemAttachmentRepository repository;
    private final CareerItemAttachmentTransactionService transactions;
    private final CareerItemServiceImpl materials;
    private final CareerItemAttachmentValidator validator;

    /** 업로드 파일을 검증하고 등록 실패를 공통 업무 오류로 전달한다. */
    @Override
    public long registerUpload(UUID requestId, String type, Map<String, Object> details,
                                                         List<MultipartFile> files) {
        List<CareerItemAttachmentValidator.File> checkedFiles;
        try {
            if (files.isEmpty()) throw new IllegalArgumentException("첨부 파일을 선택해 주세요.");
            checkedFiles = this.validator.validate(files);
        } catch (CareerItemAttachmentValidator.SizeLimitExceededException exception) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation(Map.of("attachments", exception.getMessage()));
        } catch (UncheckedIOException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }

        try {
            return register(requestId, type, details, checkedFiles);
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }

    /** 자료를 검증하고 업로드 시도를 기록한 뒤 등록 트랜잭션을 실행한다. */
    long register(UUID requestId, String type, Map<String, Object> details,
                                                 List<CareerItemAttachmentValidator.File> files) {
        CareerItemDetails material = this.materials.validate(type, details);
        List<String> keys = IntStream.range(0, files.size()).mapToObj(index -> UUID.randomUUID().toString()).toList();
        try {
            this.transactions.createAttempt(requestId, keys);
            return this.transactions.store(requestId, material, files);
        } catch (RuntimeException exception) {
            try {
                Long completedId = this.transactions.cleanup(requestId, false);
                if (completedId != null) return completedId;
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            log.warn("Attachment registration failed: requestId={}", requestId, exception);
            throw new IllegalStateException("Attachment registration failed", exception);
        }
    }

    /** 오래된 미완료 등록 시도의 객체 정리를 주기적으로 재시도한다. */
    @Scheduled(fixedDelay = 60000)
    void recoverAbandonedAttempts() {
        for (UUID request : this.repository.expiredAttempts()) {
            try {
                this.transactions.cleanup(request, true);
            } catch (RuntimeException exception) {
                log.warn("Attachment cleanup will retry: requestId={}", request, exception);
            }
        }
    }
}
