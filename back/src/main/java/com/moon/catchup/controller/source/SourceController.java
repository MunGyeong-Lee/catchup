package com.moon.catchup.controller.source;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.controller.source.dto.SourceResponse;
import com.moon.catchup.controller.source.dto.SourceUpdateRequest;
import com.moon.catchup.controller.source.dto.SourceListResponse;
import com.moon.catchup.controller.source.dto.SourceRegisterRequest;
import com.moon.catchup.controller.source.dto.SourceRegisterResponse;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.service.source.SourceAttachmentRegistrationService;
import com.moon.catchup.service.source.SourceService;
import com.moon.catchup.service.source.SourceManagementService;
import com.moon.catchup.common.http.PageResponse;
import com.moon.catchup.common.http.ResponseVo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 소스 조회·등록·수정·삭제 API를 제공한다. */
@RestController
@RequiredArgsConstructor
public class SourceController implements SourceDocs {

    private final SourceService sourceService;
    private final SourceAttachmentRegistrationService attachmentRegistrationService;
    private final SourceManagementService sourceManagementService;


    /**
     * 소스 목록 조회
     *
     * <p>유형을 생략하거나 빈 값 또는 공백만 보내면 전체 목록을 조회한다. 유형이 지정되면 필터링한 결과로
     * 페이지와 전체 건수를 계산한다. Spring MVC는 반복된 유형의 첫 값을 사용하고 코드의 앞뒤 공백을 제거한다.
     *
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize 페이지당 항목 수
     * @param orderBy 선택 정렬 조건
     * @param sourceType 선택 소스 유형. 전체 목록 조회 시 null
     * @return 요청 결과를 담은 공통 응답
     * @throws ApiException 페이지 또는 정렬 조건이 유효하지 않은 경우
     */
    @GetMapping("/api/sources")
    @Override
    public ResponseVo list(@RequestParam(name = "page_number", defaultValue = "1") int pageNumber,
                           @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
                           @RequestParam(name = "order_by", required = false) String orderBy,
                           @RequestParam(name = "sourceType", required = false) SourceType sourceType) {
        return ResponseVo.buildResponse(PageResponse.from(
                this.sourceService.list(pageNumber, pageSize, orderBy, sourceType).map(SourceListResponse::from)));
    }

    /**
     * 텍스트 소스 등록
     *
     * @param req 역직렬화한 요청 본문
     * @return 요청 결과를 담은 공통 응답
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(path = "/api/sources", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseVo register(@RequestBody SourceRegisterRequest req) {
        return registerSource(() -> this.sourceService.register(req.getSourceType(), req.getName(), req.toDetails()));
    }

    /**
     * 이미지·PDF 첨부 소스 등록
     *
     * @param req 역직렬화한 요청 본문
     * @param requestId 재시도 시 재사용하는 업로드 요청 식별자
     * @param files 요청에 포함된 첨부파일 목록
     * @return 요청 결과를 담은 공통 응답
     */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(path = "/api/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public ResponseVo registerAttachments(@RequestPart("source") SourceRegisterRequest req,
                                          @RequestParam("requestId") UUID requestId,
                                          @RequestPart("files") List<MultipartFile> files) {

        return registerSource(() -> this.attachmentRegistrationService.registerUpload(
                requestId, req.getSourceType(), req.getName(), req.toDetails(), files));
    }

    /**
     * 등록 결과를 응답으로 변환하고 자격증 이름의 검증 오류를 최상위 요청 필드에 연결한다.
     *
     * @param registration 등록을 실행해 생성한 소스 식별자를 반환하는 작업
     * @return 생성한 소스 식별자를 담은 공통 응답
     * @throws ApiException 입력 검증이나 소스 저장에 실패한 경우
     */
    private static ResponseVo registerSource(LongSupplier registration) {
        try {
            return ResponseVo.buildResponse(new SourceRegisterResponse(registration.getAsLong()));
        } catch (ApiException exception) {
            String certificateError = exception.getFieldErrors().get("details.name");
            if (certificateError != null) {
                Map<String, String> fieldErrors = Map.of("certificateName", certificateError);
                throw new ApiException(ErrorCode.VALIDATION_FAILED, fieldErrors, HttpHeaders.EMPTY, null);
            }
            throw exception;
        }
    }

    /** 소스 상세 조회 */
    @GetMapping("/api/sources/{sourceId}")
    @Override
    public ResponseVo get(@PathVariable long sourceId) {
        return ResponseVo.buildResponse(SourceResponse.from(this.sourceManagementService.get(sourceId)));
    }

    /** 소스 수정 (JSON 요청) */
    @PutMapping(path = "/api/sources/{sourceId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseVo update(@PathVariable long sourceId, @RequestBody SourceUpdateRequest req) {
        long id = this.sourceManagementService.update(sourceId, req.getSourceType(), req.getName(), req.getDetails(), copyRemovalIds(req));
        return ResponseVo.buildResponse(new SourceRegisterResponse(id));
    }

    /** 소스 수정 (새 첨부 파일 포함) */
    @PutMapping(path = "/api/sources/{sourceId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public ResponseVo updateAttachments(@PathVariable long sourceId, @RequestPart("source") SourceUpdateRequest req,
                                        @RequestParam("requestId") UUID requestId, @RequestPart("files") List<MultipartFile> files) {
        long id = this.sourceManagementService.updateWithFiles(sourceId, req.getSourceType(), req.getName(), req.getDetails(),
                copyRemovalIds(req), requestId, files);
        return ResponseVo.buildResponse(new SourceRegisterResponse(id));
    }

    /** 소스 및 첨부 파일 삭제 */
    @DeleteMapping("/api/sources/{sourceId}")
    @Override
    public ResponseVo delete(@PathVariable long sourceId) {
        this.sourceManagementService.delete(sourceId);
        return ResponseVo.buildResponse();
    }

    /**
     * 생략하거나 null인 제거 대상은 빈 목록으로 처리하고 요청 목록은 복사한다.
     *
     * @param request 소스 수정 요청
     * @return 서비스에 전달할 불변 목록
     */
    private static List<UUID> copyRemovalIds(SourceUpdateRequest request) {
        List<UUID> attachmentIdList = request.getRemoveAttachmentIds();
        return attachmentIdList == null ? List.of() : List.copyOf(attachmentIdList);
    }
}
