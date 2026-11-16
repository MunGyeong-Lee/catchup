package com.moon.catchup.careeritem.service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.careeritem.storage.CareerItemAttachmentStorage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

/** DB에서 삭제가 확정된 첨부의 S3 원본을 삭제하고, 매일 S3 객체 키를 DB 기록과 대조해 남은 원본을 정리한다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareerItemAttachmentCleanup {
    private final CareerItemAttachmentRepository careerItemAttachmentRepository;
    private final CareerItemAttachmentStorage careerItemAttachmentStorage;
    private final ExecutorService attachmentCleanupExecutor;
    private final AtomicBoolean reconciling = new AtomicBoolean();

    /** DB에서 삭제하는 첨부파일에 대응하는 S3 객체 키를 커밋 후 이벤트에 담는다. */
    @Getter
    public static final class Removed {
        private final List<String> keys;

        /** 커밋 후 삭제할 S3 객체 키 목록을 불변 복사본으로 보관한다. */
        public Removed(List<String> keys) {
            this.keys = List.copyOf(keys);
        }
    }

    /** DB 트랜잭션이 커밋되면 S3 원본 삭제를 전용 실행기에 제출한다. */
    @TransactionalEventListener
    public void afterCommit(Removed removed) {
        try {
            this.attachmentCleanupExecutor.execute(() -> deleteKeys(removed.getKeys()));
        } catch (RuntimeException exception) {
            log.warn("Attachment cleanup submission failed: count={}", removed.getKeys().size(), exception);
        }
    }

    /**
     * 기본값으로 매일 한국 시간 오전 4시에 S3의 Catchup 첨부 객체 키를 먼저 조회한다.
     * 이후 DB의 첨부 및 미완료 업로드 기록에 없는 키만 S3에서 삭제한다.
     * S3 목록 또는 DB 조회가 실패하면 해당 실행의 삭제를 건너뛴다.
     */
    @Scheduled(cron = "${catchup.attachments.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void reconcile() {
        if (!this.reconciling.compareAndSet(false, true)) return;
        try {
            List<String> listedKeys = this.careerItemAttachmentStorage.listKeys();
            Set<String> protectedKeys = this.careerItemAttachmentRepository.protectedKeys();
            deleteKeys(listedKeys.stream().filter(key -> !protectedKeys.contains(key)).toList());
        } catch (RuntimeException exception) {
            log.warn("Attachment reconciliation skipped", exception);
        } finally {
            this.reconciling.set(false);
        }
    }

    /** 키마다 S3 객체를 삭제하고, 실패한 키는 경고를 남긴 뒤 나머지 키를 처리한다. */
    private void deleteKeys(List<String> keys) {
        for (String key : keys) {
            try {
                this.careerItemAttachmentStorage.delete(key);
            } catch (RuntimeException exception) {
                log.warn("Attachment deletion failed: objectKey={}", key, exception);
            }
        }
    }
}
