package com.moon.catchup.careeritem.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/** CareerItemAttachmentRegistrationService의 공개 업무 계약이다. */
public interface CareerItemAttachmentRegistrationService {
    /** 첨부가 있는 자료를 등록하고 자료 식별자를 반환한다. */
    public long registerUpload(UUID requestId, String type, Map<String, Object> details, List<MultipartFile> files);
}
