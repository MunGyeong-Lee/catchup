package com.moon.catchup.careeritem.docs;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.careeritem.dto.RegistrationRequest;
import com.moon.catchup.common.http.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

/** 자료 목록 조회와 자료 등록의 OpenAPI 문서 계약이다. */
@Tag(name = "Career items")
public interface CareerItemDocs {
    /** 자료 요약 목록을 반환하는 작업을 문서화한다. */
    @Operation(summary = "자료 목록 조회")
    ResponseVo list();

    /** 파일 없이 자료를 등록하는 작업을 문서화한다. */
    @Operation(summary = "자료 등록")
    ResponseVo register(RegistrationRequest req);

    /** 자료와 선택한 파일을 함께 등록하는 작업을 문서화한다. */
    @Operation(summary = "첨부 파일이 있는 자료 등록")
    ResponseVo registerAttachments(
            RegistrationRequest req, UUID requestId, List<MultipartFile> files, String uploadHeader);
}
