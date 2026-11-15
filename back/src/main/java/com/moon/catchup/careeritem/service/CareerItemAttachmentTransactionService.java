package com.moon.catchup.careeritem.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.moon.catchup.careeritem.domain.CareerItemAttachment;
import com.moon.catchup.careeritem.domain.CareerItemDetails;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository.UploadAttempt;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.careeritem.storage.CareerItemAttachmentStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 첨부 등록의 시도 기록, 저장, 실패 정리를 각각 독립된 트랜잭션으로 실행한다. */
@Service
@RequiredArgsConstructor
public class CareerItemAttachmentTransactionService {
    private final CareerItemAttachmentRepository repository;
    private final CareerItemServiceImpl materials;
    private final CareerItemAttachmentStorage storage;

    /** 업로드 전에 요청 식별자와 객체 키를 별도 트랜잭션에 기록한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAttempt(UUID requestId, List<String> keys) {
        this.repository.createAttempt(requestId, keys);
    }

    /** 등록 시도를 잠그고 파일을 업로드한 뒤 자료와 첨부의 저장을 완료한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long store(UUID requestId, CareerItemDetails material,
                       List<CareerItemAttachmentValidator.File> files) {
        UploadAttempt attempt = this.repository.lockAttempt(requestId, false)
                .orElseThrow(() -> new IllegalStateException("Registration attempt was cleared"));
        if (attempt.getMaterialId() != null) return attempt.getMaterialId();

        List<String> keys = attempt.getObjectKeys();
        if (keys.size() != files.size()) throw new IllegalStateException("Registration payload changed");
        for (int index = 0; index < keys.size(); index++) {
            CareerItemAttachmentValidator.File file = files.get(index);
            try (InputStream input = file.getContent().getInputStream()) {
                this.storage.put(keys.get(index), file.getType(), file.getContent().getSize(), input);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        long materialId = this.materials.insert(material);
        for (int index = 0; index < keys.size(); index++) {
            CareerItemAttachmentValidator.File file = files.get(index);
            this.repository.insert(materialId, new CareerItemAttachment(UUID.randomUUID(), file.getName(), file.getType(),
                    file.getContent().getSize()), keys.get(index));
        }
        this.repository.completeAttempt(requestId, materialId);
        return materialId;
    }

    /** 완료 여부를 잠금 상태에서 확인하고 미완료 시도의 객체와 기록을 정리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long cleanup(UUID requestId, boolean skipActive) {
        Optional<UploadAttempt> attempts = this.repository.lockAttempt(requestId, skipActive);
        if (attempts.isEmpty()) return null;

        UploadAttempt attempt = attempts.get();
        if (attempt.getMaterialId() != null) return attempt.getMaterialId();

        for (String key : attempt.getObjectKeys()) this.storage.delete(key);
        this.repository.deleteAttempt(requestId);
        return null;
    }
}
