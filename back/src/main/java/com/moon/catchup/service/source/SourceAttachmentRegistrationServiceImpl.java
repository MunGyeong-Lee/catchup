package com.moon.catchup.service.source;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
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
public class SourceAttachmentRegistrationServiceImpl implements SourceAttachmentRegistrationService {
    private final SourceAttachmentRepository sourceAttachmentRepository;
    private final SourceAttachmentTransactionService attachmentTransactionService;
    private final SourceServiceImpl sourceService;
    private final SourceAttachmentValidator attachmentValidator;

    /**
     * 업로드 파일을 검증하고 등록 실패를 공통 업무 오류로 전달한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     */
    @Override
    public long registerUpload(UUID requestId, String type, Object name, Map<String, Object> details,
                                                         List<MultipartFile> files) {
        try {
            return register(requestId, type, name, details, validateFiles(files));
        } catch (IllegalStateException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }

    /**
     * 새 첨부 파일이 있는 수정에서 등록과 같은 검증 및 실패 복구를 사용한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param sourceId 소스 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     */
    public long updateUpload(UUID requestId, long sourceId, String type, Object name, Map<String, Object> details,
                      List<UUID> removedIds, List<MultipartFile> files) {
        List<SourceAttachmentValidator.File> checkedFiles = validateFiles(files);
        SourceDetailsVo source = this.sourceService.validate(type, name, details);
        try {
            this.attachmentTransactionService.createEditAttempt(requestId, newKeys(checkedFiles.size()), sourceId);
            return this.attachmentTransactionService.storeEdit(requestId, sourceId, source, removedIds, checkedFiles);
        } catch (RuntimeException exception) {
            try {
                Long completedId = this.attachmentTransactionService.cleanupEdit(requestId, sourceId);
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

    /**
     * 등록과 수정에서 추가할 파일의 형식과 크기를 동일하게 검증한다.
     *
     * @param files 요청에 포함된 첨부파일 목록
     * @return 요청 순서대로 검증한 첨부파일 목록
     * @throws ApiException 파일이 없거나 파일 형식·크기가 유효하지 않거나 스트림을 읽을 수 없는 경우
     */
    private List<SourceAttachmentValidator.File> validateFiles(List<MultipartFile> files) {
        try {
            if (files.isEmpty()) throw new IllegalArgumentException("첨부 파일을 선택해 주세요.");
            return this.attachmentValidator.validate(files);
        } catch (SourceAttachmentValidator.SizeLimitExceededException exception) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation(Map.of("attachments", exception.getMessage()));
        } catch (UncheckedIOException exception) {
            throw new ApiException(ErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE, exception);
        }
    }

    /**
     * 저장 키를 새로 발급해 이전 원본의 키를 재사용하지 않는다.
     *
     * @param size 발급할 객체 키의 수
     * @return 조회 또는 삭제한 S3 객체 키 목록
     */
    private static List<String> newKeys(int size) {
        return IntStream.range(0, size).mapToObj(index -> UUID.randomUUID().toString()).toList();
    }

    /**
     * 소스를 검증하고 업로드 시도를 기록한 뒤 등록 트랜잭션을 실행한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     */
    public long register(UUID requestId, String type, Object name, Map<String, Object> details,
                                                 List<SourceAttachmentValidator.File> files) {
        SourceDetailsVo source = this.sourceService.validate(type, name, details);
        List<String> keys = newKeys(files.size());
        try {
            this.attachmentTransactionService.createAttempt(requestId, keys);
            return this.attachmentTransactionService.store(requestId, source, files);
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
        for (UUID request : this.sourceAttachmentRepository.expiredAttempts()) {
            try {
                this.attachmentTransactionService.cleanup(request, true);
            } catch (RuntimeException exception) {
                log.warn("Attachment cleanup will retry: requestId={}", request, exception);
            }
        }
    }
}
