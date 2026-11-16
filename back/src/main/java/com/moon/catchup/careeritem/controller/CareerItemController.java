package com.moon.catchup.careeritem.controller;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.careeritem.docs.CareerItemDocs;
import com.moon.catchup.careeritem.dto.CareerItemEditResponse;
import com.moon.catchup.careeritem.dto.CareerItemUpdateRequest;
import com.moon.catchup.careeritem.dto.ItemSummaryResponse;
import com.moon.catchup.careeritem.dto.RegistrationRequest;
import com.moon.catchup.careeritem.dto.RegistrationResponse;
import com.moon.catchup.careeritem.service.CareerItemAttachmentRegistrationService;
import com.moon.catchup.careeritem.service.CareerItemService;
import com.moon.catchup.careeritem.service.CareerItemManagementService;
import com.moon.catchup.common.http.ResponseVo;
import lombok.RequiredArgsConstructor;
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

/** 자료 조회·등록·수정·삭제 API를 제공한다. */
@RestController
@RequiredArgsConstructor
public class CareerItemController implements CareerItemDocs {

    private final CareerItemService careerItemService;

    private final CareerItemAttachmentRegistrationService attachmentRegistrationService;
    private final CareerItemManagementService careerItemManagementService;


    /** 자료 목록 조회 */
    @GetMapping("/api/career-items")
    @Override
    public ResponseVo list() {
        List<ItemSummaryResponse> summaryList = this.careerItemService.list().stream().map(ItemSummaryResponse::from).toList();
        return ResponseVo.buildResponse(summaryList);
    }

    /** 텍스트 자료 등록 */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(path = "/api/career-items", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseVo register(@RequestBody RegistrationRequest req) {
        long careerItemId = this.careerItemService.register(req.getCareerItemType(), req.getDetails());
        return ResponseVo.buildResponse(new RegistrationResponse(careerItemId));
    }

    /** 이미지·PDF 첨부 자료 등록 */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(path = "/api/career-items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public ResponseVo registerAttachments(@RequestPart("careerItem") RegistrationRequest req,
                                          @RequestParam("requestId") UUID requestId,
                                          @RequestPart("files") List<MultipartFile> files) {

        long careerItemId = this.attachmentRegistrationService.registerUpload(
                requestId, req.getCareerItemType(), req.getDetails(), files);
        return ResponseVo.buildResponse(new RegistrationResponse(careerItemId));
    }

    /** 자료 수정 화면용 상세 조회 */
    @GetMapping("/api/career-items/{careerItemId}")
    @Override
    public ResponseVo get(@PathVariable long careerItemId) {
        return ResponseVo.buildResponse(CareerItemEditResponse.from(this.careerItemManagementService.get(careerItemId)));
    }

    /** 자료 수정 (JSON 요청) */
    @PutMapping(path = "/api/career-items/{careerItemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseVo update(@PathVariable long careerItemId, @RequestBody CareerItemUpdateRequest req) {
        long id = this.careerItemManagementService.update(careerItemId, req.getCareerItemType(), req.getDetails(), req.getRemoveAttachmentIds());
        return ResponseVo.buildResponse(new RegistrationResponse(id));
    }

    /** 자료 수정 (새 첨부 파일 포함) */
    @PutMapping(path = "/api/career-items/{careerItemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public ResponseVo updateAttachments(@PathVariable long careerItemId, @RequestPart("careerItem") CareerItemUpdateRequest req,
                                        @RequestParam("requestId") UUID requestId, @RequestPart("files") List<MultipartFile> files) {
        long id = this.careerItemManagementService.updateWithFiles(careerItemId, req.getCareerItemType(), req.getDetails(),
                req.getRemoveAttachmentIds(), requestId, files);
        return ResponseVo.buildResponse(new RegistrationResponse(id));
    }

    /** 자료 및 첨부 파일 삭제 */
    @DeleteMapping("/api/career-items/{careerItemId}")
    @Override
    public ResponseVo delete(@PathVariable long careerItemId) {
        this.careerItemManagementService.delete(careerItemId);
        return ResponseVo.buildResponse();
    }
}
