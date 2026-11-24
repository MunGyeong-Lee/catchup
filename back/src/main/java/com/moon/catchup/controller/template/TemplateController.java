package com.moon.catchup.controller.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.common.http.ResponseVo;
import com.moon.catchup.controller.template.dto.TemplateCreateRequest;
import com.moon.catchup.controller.template.dto.TemplateCreateResponse;
import com.moon.catchup.domain.template.GridTemplateBlockPlacementVo;
import com.moon.catchup.domain.template.GridTemplateBlockVo;
import com.moon.catchup.domain.template.GridTemplatePageVo;
import com.moon.catchup.domain.template.GridTemplateStructureVo;
import com.moon.catchup.domain.template.ParagraphTemplatePageVo;
import com.moon.catchup.domain.template.ParagraphTemplateStructureVo;
import com.moon.catchup.domain.template.TemplateBlockType;
import com.moon.catchup.domain.template.TemplateDocumentType;
import com.moon.catchup.domain.template.TemplateLayoutType;
import com.moon.catchup.domain.template.TemplatePageOrientation;
import com.moon.catchup.service.template.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

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
    public ResponseVo create(@RequestBody TemplateCreateRequest req) {
        TemplateDocumentType documentType = readEnum(req.getDocumentType(), TemplateDocumentType.class);
        if (documentType == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("documentType", "문서 유형을 선택해 주세요."));
        }
        String title = readText(req.getTitle());
        JsonNode structure = req.getStructure();
        if (isMissing(structure)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("structure", "템플릿 구조를 입력해 주세요."));
        }

        JsonNode pageList = readArray(readObject(structure).path("pages"), "pages");
        long templateId = documentType.getLayoutType() == TemplateLayoutType.GRID
                ? this.templateService.createGrid(documentType, title, readGrid(pageList))
                : this.templateService.createParagraph(documentType, title, readParagraph(pageList));
        return ResponseVo.buildResponse(new TemplateCreateResponse(templateId));
    }

    private static GridTemplateStructureVo readGrid(JsonNode pageList) {
        List<GridTemplatePageVo> gridPageList = new ArrayList<>();
        for (JsonNode value : pageList) {
            JsonNode page = readObject(value);
            if (page.has("headingId") || page.has("paragraphIds")) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("structure", "그리드형 페이지를 사용해 주세요."));
            }
            TemplatePageOrientation orientation = readEnum(page.path("orientation"), TemplatePageOrientation.class);
            List<GridTemplateBlockVo> blockList = new ArrayList<>();
            for (JsonNode block : readArray(page.path("blocks"), "blocks")) {
                blockList.add(readGridBlock(readObject(block)));
            }
            gridPageList.add(GridTemplatePageVo.of(orientation, blockList));
        }
        return GridTemplateStructureVo.of(gridPageList);
    }

    private static GridTemplateBlockVo readGridBlock(JsonNode block) {
        int id = readInteger(block.path("id"), "blocks");
        TemplateBlockType type = readEnum(block.path("type"), TemplateBlockType.class);
        String title = readText(block.path("title"));
        JsonNode placement = readObject(requireValue(block.path("placement"), "placement"));
        GridTemplateBlockPlacementVo blockPlacement = GridTemplateBlockPlacementVo.of(
                readInteger(placement.path("row"), "placement"), readInteger(placement.path("column"), "placement"),
                readInteger(placement.path("columnSpan"), "placement"));
        return GridTemplateBlockVo.of(id, type, title, blockPlacement);
    }

    private static ParagraphTemplateStructureVo readParagraph(JsonNode pageList) {
        List<ParagraphTemplatePageVo> paragraphPageList = new ArrayList<>();
        for (JsonNode value : pageList) {
            JsonNode page = readObject(value);
            if (page.has("blocks") || page.has("orientation")) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("structure", "문단형 페이지를 사용해 주세요."));
            }
            int headingId = readInteger(page.path("headingId"), "headingId");
            List<Integer> paragraphIdList = new ArrayList<>();
            for (JsonNode paragraphId : readArray(page.path("paragraphIds"), "paragraphIds")) {
                paragraphIdList.add(readInteger(paragraphId, "paragraphIds"));
            }
            paragraphPageList.add(ParagraphTemplatePageVo.of(headingId, paragraphIdList));
        }
        return ParagraphTemplateStructureVo.of(paragraphPageList);
    }

    private static JsonNode readObject(JsonNode value) {
        if (!value.isObject()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private static JsonNode readArray(JsonNode value, String field) {
        if (!requireValue(value, field).isArray()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    private static int readInteger(JsonNode value, String field) {
        if (!requireValue(value, field).isIntegralNumber() || !value.canConvertToInt()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value.intValue();
    }

    private static <E extends Enum<E>> E readEnum(JsonNode value, Class<E> enumClass) {
        String code = readText(value);
        try {
            return code == null ? null : Enum.valueOf(enumClass, code);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static String readText(JsonNode value) {
        if (isMissing(value)) {
            return null;
        }
        if (!value.isString()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value.asString();
    }

    private static JsonNode requireValue(JsonNode value, String field) {
        if (isMissing(value)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of(field, "필수 입력 항목입니다."));
        }
        return value;
    }

    private static boolean isMissing(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode();
    }
}
