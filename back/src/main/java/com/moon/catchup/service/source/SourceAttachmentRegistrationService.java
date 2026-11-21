package com.moon.catchup.service.source;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/** SourceAttachmentRegistrationService의 공개 업무 계약이다. */
public interface SourceAttachmentRegistrationService {
    /**
     * 첨부가 있는 소스를 등록하고 소스 식별자를 반환한다.
     *
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     * @throws com.moon.catchup.common.http.ApiException 입력이나 첨부파일이 유효하지 않거나 원본 저장소 장애가 발생한 경우
     */
    public long registerUpload(UUID requestId, String type, Object name, Map<String, Object> details, List<MultipartFile> files);
}
