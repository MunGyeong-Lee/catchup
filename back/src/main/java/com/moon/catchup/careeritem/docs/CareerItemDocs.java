package com.moon.catchup.careeritem.docs;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.careeritem.dto.RegistrationRequest;
import com.moon.catchup.careeritem.dto.CareerItemUpdateRequest;
import com.moon.catchup.common.http.ResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

/** 자료 목록, 등록, 수정용 조회, 수정 및 삭제 API의 OpenAPI 문서를 정의한다. */
@Tag(name = "Career items")
public interface CareerItemDocs {
    /** 자료 요약 목록을 반환하는 작업을 문서화한다. */
    @Operation(summary = "자료 목록 조회")
    public ResponseVo list();

    /** 파일 없이 자료를 등록하는 작업을 문서화한다. */
    @Operation(summary = "자료 등록")
    public ResponseVo register(RegistrationRequest req);

    /** 자료와 선택한 파일을 함께 등록하는 작업을 문서화한다. */
    @Operation(summary = "첨부 파일이 있는 자료 등록")
    public ResponseVo registerAttachments(
            RegistrationRequest req, UUID requestId, List<MultipartFile> files);

    /** 수정용 자료와 첨부 목록 조회를 문서화한다. */
    @Operation(summary = "자료 수정용 현재값 조회")
    public ResponseVo get(long careerItemId);

    /** 자료 입력 및 첨부 제거를 문서화한다. */
    @Operation(summary = "자료 수정")
    public ResponseVo update(long careerItemId, CareerItemUpdateRequest req);

    /** 새 첨부를 추가하는 자료 수정을 문서화한다. */
    @Operation(summary = "새 첨부 파일이 있는 자료 수정")
    public ResponseVo updateAttachments(long careerItemId, CareerItemUpdateRequest req, UUID requestId,
                                 List<MultipartFile> files);

    /** 자료 전체 삭제를 문서화한다. */
    @Operation(summary = "자료 삭제")
    public ResponseVo delete(long careerItemId);
}
