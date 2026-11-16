package com.moon.catchup.careeritem.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moon.catchup.careeritem.domain.CareerItemEntity;
import com.moon.catchup.careeritem.domain.CareerItemDetailsVo;
import com.moon.catchup.careeritem.domain.CareerItemEditVo;
import com.moon.catchup.careeritem.repository.CareerItemAttachmentRepository;
import com.moon.catchup.careeritem.repository.CareerItemRepository;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** DB에서 자료와 첨부 메타데이터 변경을 관리하고, 삭제한 첨부의 S3 원본 정리를 커밋 후 작업으로 요청한다. */
@Service
@RequiredArgsConstructor
public class CareerItemManagementServiceImpl implements CareerItemManagementService {
    private final CareerItemServiceImpl careerItems;
    private final CareerItemRepository repository;
    private final CareerItemAttachmentRepository attachments;
    private final CareerItemAttachmentRegistrationServiceImpl uploads;
    private final ApplicationEventPublisher events;

    /** 수정 화면에 필요한 자료 입력과 첨부 메타데이터를 같은 DB 스냅샷에서 읽는다. */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CareerItemEditVo get(long id) {
        CareerItemEntity item = this.repository.find(id, false)
                .orElseThrow(() -> new ApiException(ErrorCode.CAREER_ITEM_NOT_FOUND));
        return new CareerItemEditVo(item, this.attachments.list(id));
    }

    /** 자료를 잠근 뒤 검증한 입력과 첨부 제거를 함께 저장한다. */
    @Override
    @Transactional
    public long update(long id, String type, Map<String, Object> details, List<UUID> removedIds) {
        CareerItemDetailsVo validated = this.careerItems.validate(type, details);
        this.careerItems.lock(id);
        this.careerItems.modify(id, validated, removedIds);
        return id;
    }

    /** 새 파일을 예약하는 업로드 흐름에서 자료 수정을 처리한다. */
    @Override
    public long updateWithFiles(long id, String type, Map<String, Object> details, List<UUID> removedIds,
                                UUID requestId, List<MultipartFile> files) {
        return this.uploads.updateUpload(requestId, id, type, details, removedIds, files);
    }

    /** 자료를 잠그고 첨부와 자료를 함께 삭제한 뒤 커밋 후 원본 정리를 예약한다. */
    @Override
    @Transactional
    public void delete(long id) {
        this.careerItems.lock(id);
        List<String> removedKeys = this.attachments.removeAll(id);
        this.attachments.detachCompletedAttempts(id);
        this.repository.delete(id);
        if (!removedKeys.isEmpty()) this.events.publishEvent(new CareerItemAttachmentCleanup.Removed(removedKeys));
    }
}
