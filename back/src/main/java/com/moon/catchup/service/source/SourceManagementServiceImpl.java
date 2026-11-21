package com.moon.catchup.service.source;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moon.catchup.domain.source.SourceEntity;
import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceEditVo;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.repository.source.SourceRepository;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** DB에서 소스와 첨부 메타데이터 변경을 관리하고, 삭제한 첨부의 S3 원본 정리를 커밋 후 작업으로 요청한다. */
@Service
@RequiredArgsConstructor
public class SourceManagementServiceImpl implements SourceManagementService {
    private final SourceServiceImpl sourceService;
    private final SourceRepository sourceRepository;
    private final SourceAttachmentRepository sourceAttachmentRepository;
    private final SourceAttachmentRegistrationServiceImpl attachmentRegistrationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 수정 화면에 필요한 소스 입력과 첨부 메타데이터를 같은 DB 스냅샷에서 읽는다.
     *
     * @param id 소스 식별자
     * @return 수정용 소스 상세와 첨부파일 목록
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SourceEditVo get(long id) {
        SourceEntity item = this.sourceRepository.find(id, false)
                .orElseThrow(() -> new ApiException(ErrorCode.SOURCE_NOT_FOUND));
        return SourceEditVo.from(item, this.sourceAttachmentRepository.list(id));
    }

    /**
     * 소스를 잠근 뒤 검증한 입력과 첨부 제거를 함께 저장한다.
     *
     * @param id 소스 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @return 저장된 소스 식별자
     */
    @Override
    @Transactional
    public long update(long id, String type, Object name, Map<String, Object> details, List<UUID> removedIds) {
        SourceDetailsVo validated = this.sourceService.validate(type, name, details);
        this.sourceService.lock(id);
        this.sourceService.modify(id, validated, removedIds, false);
        return id;
    }

    /**
     * 새 파일을 예약하는 업로드 흐름에서 소스 수정을 처리한다.
     *
     * @param id 소스 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     */
    @Override
    public long updateWithFiles(long id, String type, Object name, Map<String, Object> details, List<UUID> removedIds,
                                UUID requestId, List<MultipartFile> files) {
        return this.attachmentRegistrationService.updateUpload(requestId, id, type, name, details, removedIds, files);
    }

    /**
     * 소스를 잠그고 첨부와 소스를 함께 삭제한 뒤 커밋 후 원본 정리를 예약한다.
     *
     * @param id 소스 식별자
     */
    @Override
    @Transactional
    public void delete(long id) {
        this.sourceService.lock(id);
        List<String> removedKeys = this.sourceAttachmentRepository.removeAll(id);
        this.sourceAttachmentRepository.detachCompletedAttempts(id);
        this.sourceRepository.delete(id);
        if (!removedKeys.isEmpty()) this.applicationEventPublisher.publishEvent(new SourceAttachmentCleanup.Removed(removedKeys));
    }
}
