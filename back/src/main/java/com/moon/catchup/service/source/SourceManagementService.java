package com.moon.catchup.service.source;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moon.catchup.domain.source.SourceEditVo;
import org.springframework.web.multipart.MultipartFile;

/** 소스의 수정용 조회, 수정, 삭제 업무 계약이다. */
public interface SourceManagementService {
    /**
     * 입력값과 첨부 목록을 조회한다.
     *
     * @param id 소스 식별자
     * @return 수정용 소스 상세와 첨부파일 목록
     * @throws com.moon.catchup.common.http.ApiException 소스가 존재하지 않는 경우
     */
    public SourceEditVo get(long id);

    /**
     * 소스 입력과 기존 첨부 삭제를 저장한다.
     *
     * @param id 소스 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @return 저장된 소스 식별자
     * @throws com.moon.catchup.common.http.ApiException 소스가 없거나 입력 또는 첨부 삭제 대상이 유효하지 않은 경우
     */
    public long update(long id, String type, Object name, Map<String, Object> details, List<UUID> removedIds);

    /**
     * 새 첨부가 포함된 수정을 저장한다.
     *
     * @param id 소스 식별자
     * @param type 소스 유형 또는 콘텐츠 유형
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param files 요청에 포함된 첨부파일 목록
     * @return 저장된 소스 식별자
     * @throws com.moon.catchup.common.http.ApiException 저장할 소스 입력이나 첨부파일이 유효하지 않거나 원본 저장소 장애가 발생한 경우
     */
    public long updateWithFiles(long id, String type, Object name, Map<String, Object> details, List<UUID> removedIds,
                         UUID requestId, List<MultipartFile> files);

    /**
     * 소스와 첨부 기록을 삭제한다.
     *
     * @param id 소스 식별자
     * @throws com.moon.catchup.common.http.ApiException 소스가 존재하지 않는 경우
     */
    public void delete(long id);
}
