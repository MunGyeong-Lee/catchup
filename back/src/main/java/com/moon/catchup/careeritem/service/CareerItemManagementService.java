package com.moon.catchup.careeritem.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.moon.catchup.careeritem.domain.CareerItemEditVo;
import org.springframework.web.multipart.MultipartFile;

/** 자료의 수정용 조회, 수정, 삭제 업무 계약이다. */
public interface CareerItemManagementService {
    /** 입력값과 첨부 목록을 조회한다. */
    public CareerItemEditVo get(long id);

    /** 자료 입력과 기존 첨부 삭제를 저장한다. */
    public long update(long id, String type, Map<String, Object> details, List<UUID> removedIds);

    /** 새 첨부가 포함된 수정을 저장한다. */
    public long updateWithFiles(long id, String type, Map<String, Object> details, List<UUID> removedIds,
                         UUID requestId, List<MultipartFile> files);

    /** 자료와 첨부 기록을 삭제한다. */
    public void delete(long id);
}
