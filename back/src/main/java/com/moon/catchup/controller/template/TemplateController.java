package com.moon.catchup.controller.template;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ResponseVo;
import com.moon.catchup.controller.template.dto.TemplateCreateRequest;
import com.moon.catchup.controller.template.dto.TemplateCreateResponse;
import com.moon.catchup.domain.template.TemplateDocumentType;
import com.moon.catchup.domain.template.TemplateLayoutType;
import com.moon.catchup.service.template.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 템플릿 등록 HTTP 요청을 문서 유형에 맞는 서비스 호출로 변환한다. */
@RestController
@RequiredArgsConstructor
public class TemplateController implements TemplateDocs {
    private final TemplateService templateService;

    /**
     * 템플릿 등록
     *
     * @param req 문서 유형, 제목과 편집 구조
     * @return 등록 식별자를 담은 공통 응답
     * @throws ApiException 요청 형식이나 템플릿 입력이 유효하지 않은 경우
     */
    @Override
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(path = "/api/templates", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseVo create(@Valid @RequestBody TemplateCreateRequest req) {
        TemplateDocumentType documentType = req.getDocumentType();
        long templateId = documentType.getLayoutType() == TemplateLayoutType.GRID
                ? this.templateService.createGrid(documentType, req)
                : this.templateService.createParagraph(documentType, req);
        return ResponseVo.buildResponse(new TemplateCreateResponse(templateId));
    }
}
