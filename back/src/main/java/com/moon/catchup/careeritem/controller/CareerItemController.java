package com.moon.catchup.careeritem.controller;

import java.util.List;
import java.util.UUID;

import com.moon.catchup.careeritem.docs.CareerItemDocs;
import com.moon.catchup.careeritem.dto.ItemSummaryResponse;
import com.moon.catchup.careeritem.dto.RegistrationRequest;
import com.moon.catchup.careeritem.dto.RegistrationResponse;
import com.moon.catchup.careeritem.service.CareerItemAttachmentRegistrationService;
import com.moon.catchup.careeritem.service.CareerItemService;
import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ResponseVo;
import com.moon.catchup.common.http.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 자료 등록과 자료 목록 조회의 HTTP 요청을 처리한다. */
@RestController
@RequiredArgsConstructor
public class CareerItemController implements CareerItemDocs {

    private final CareerItemService service;

    private final CareerItemAttachmentRegistrationService attachments;


    /** 최근 등록 순으로 조회한 자료를 목록 응답으로 반환한다. */
    @GetMapping("/api/career-materials")
    @Override
    public ResponseVo list() {
        List<ItemSummaryResponse> summaryList = this.service.list().stream().map(ItemSummaryResponse::from).toList();
        return ResponseVo.buildResponse(summaryList);
    }

    /** 첨부 파일 없이 자료를 등록한다. */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(path = "/api/career-materials", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseVo register(@RequestBody RegistrationRequest req) {
        long materialId = this.service.register(req.getMaterialType(), req.getDetails());
        return ResponseVo.buildResponse(new RegistrationResponse(materialId));
    }

    /** 파일과 자료 입력을 받아 첨부 등록을 요청하고 실패를 HTTP 응답으로 변환한다. */
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(path = "/api/career-materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public ResponseVo registerAttachments(@RequestPart("material") RegistrationRequest req,
                                          @RequestParam("requestId") UUID requestId,
                                          @RequestPart("files") List<MultipartFile> files,
                                          @RequestHeader("X-Catchup-Upload") String uploadHeader) {
        if (!"1".equals(uploadHeader)) throw new ApiException(ErrorCode.INVALID_REQUEST);

        long materialId = this.attachments.registerUpload(
                requestId, req.getMaterialType(), req.getDetails(), files);
        return ResponseVo.buildResponse(new RegistrationResponse(materialId));
    }
}
