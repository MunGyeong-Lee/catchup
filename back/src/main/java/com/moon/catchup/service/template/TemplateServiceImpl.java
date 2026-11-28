package com.moon.catchup.service.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.controller.template.dto.TemplateCreateRequest;
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
import com.moon.catchup.domain.template.TemplateVo;
import com.moon.catchup.repository.template.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 템플릿 등록 입력을 검증하고 편집 구조를 JSONB 컬럼에 저장한다. */
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {
    private static final int GRID_COLUMN_COUNT = 12;

    private final TemplateRepository templateRepository;
    private final JsonMapper jsonMapper;

    /**
     * 그리드형 HTTP 요청의 페이지와 블록을 VO로 변환하고 등록한다.
     *
     * <p>요청 구조를 읽은 뒤 그리드 등록 규칙을 검증하고 같은 트랜잭션에서 저장한다.
     *
     * @param documentType 그리드형 문서 유형
     * @param req 제목과 그리드형 편집 구조를 담은 등록 요청
     * @return 저장된 템플릿 식별자
     * @throws ApiException 요청 구조를 읽을 수 없거나 그리드 등록 규칙을 위반한 경우
     */
    @Override
    @Transactional
    public long createGrid(TemplateDocumentType documentType, TemplateCreateRequest req) {
        JsonNode pageList = readPages(req.getStructure());
        return this.createGrid(documentType, req.getTitle(), readGrid(pageList));
    }

    /**
     * 문단형 HTTP 요청의 소제목과 문단 자리를 VO로 변환하고 등록한다.
     *
     * <p>요청 구조를 읽은 뒤 문단형 등록 규칙을 검증하고 같은 트랜잭션에서 저장한다.
     *
     * @param documentType 문단형 문서 유형
     * @param req 제목과 문단형 편집 구조를 담은 등록 요청
     * @return 저장된 템플릿 식별자
     * @throws ApiException 요청 구조를 읽을 수 없거나 문단형 등록 규칙을 위반한 경우
     */
    @Override
    @Transactional
    public long createParagraph(TemplateDocumentType documentType, TemplateCreateRequest req) {
        JsonNode pageList = readPages(req.getStructure());
        return this.createParagraph(documentType, req.getTitle(), readParagraph(pageList));
    }

    /**
     * 그리드형 템플릿의 구조를 검증하고 JSONB로 저장한다.
     *
     * <p>문서 유형과 제목을 확인한 뒤 페이지별 블록의 12열 배치 범위와 겹침을 검사한다.
     * 블록 식별자는 템플릿 전체에서 중복될 수 없다.
     *
     * @param documentType 그리드형 문서 유형
     * @param title 템플릿 제목. 앞뒤 공백을 제거해 저장한다.
     * @param structure 페이지와 블록의 그리드 구조
     * @return 저장된 템플릿 식별자
     * @throws ApiException 문서 유형, 제목 또는 그리드 구조가 등록 규칙을 위반한 경우
     */
    @Override
    @Transactional
    public long createGrid(TemplateDocumentType documentType, String title, GridTemplateStructureVo structure) {
        String normalizedTitle = validateRegistration(documentType, title, TemplateLayoutType.GRID);
        if (structure == null || structure.getPages().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("pages", "페이지를 한 개 이상 추가해 주세요."));
        }

        Set<Integer> blockIds = new HashSet<>();
        for (GridTemplatePageVo page : structure.getPages()) {
            validateGridPage(page, blockIds);
        }
        return this.save(documentType, normalizedTitle, this.jsonMapper.writeValueAsString(structure));
    }

    /**
     * 문단형 템플릿의 구조를 검증하고 JSONB로 저장한다.
     *
     * <p>문서 유형과 제목을 확인한 뒤 소제목 식별자의 중복과 문항별 문단 순서를 검사한다.
     * 각 문항에는 문단이 한 개 이상 있어야 하고 번호는 1부터 연속해야 한다.
     *
     * @param documentType 문단형 문서 유형
     * @param title 템플릿 제목. 앞뒤 공백을 제거해 저장한다.
     * @param structure 문항별 페이지와 문단 자리 구조
     * @return 저장된 템플릿 식별자
     * @throws ApiException 문서 유형, 제목 또는 문단형 구조가 등록 규칙을 위반한 경우
     */
    @Override
    @Transactional
    public long createParagraph(TemplateDocumentType documentType, String title, ParagraphTemplateStructureVo structure) {
        String normalizedTitle = validateRegistration(documentType, title, TemplateLayoutType.PARAGRAPH);
        if (structure == null || structure.getPages().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("pages", "문항 페이지를 한 개 이상 추가해 주세요."));
        }

        Set<Integer> headingIds = new HashSet<>();
        for (ParagraphTemplatePageVo page : structure.getPages()) {
            if (page.getHeadingId() < 1 || !headingIds.add(page.getHeadingId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("headingId", "소제목 식별자는 중복되지 않는 양의 정수여야 합니다."));
            }
            validateParagraphOrder(page.getParagraphIds());
        }
        return this.save(documentType, normalizedTitle, this.jsonMapper.writeValueAsString(structure));
    }

    /**
     * 편집 구조에서 필수 페이지 배열을 읽는다.
     *
     * @param structure 요청에 포함된 편집 구조 객체
     * @return 요청 순서대로 페이지를 담은 JSON 배열
     * @throws ApiException 구조 또는 페이지가 누락됐거나 pages가 배열이 아닌 경우
     */
    private static JsonNode readPages(JsonNode structure) {
        return readArray(requireValue(structure, "structure").path("pages"), "pages");
    }

    /**
     * 요청 순서를 보존하며 그리드형 페이지와 블록을 VO로 변환한다.
     *
     * @param pageList 요청에서 읽은 페이지 배열
     * @return 그리드형 템플릿 구조
     * @throws ApiException 페이지 형식이 올바르지 않거나 문단형 필드가 있거나 블록 입력을 읽을 수 없는 경우
     */
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

    /**
     * 블록 객체에서 식별자, 유형, 제목과 배치를 읽는다.
     *
     * <p>{@code rowSpan}을 생략하면 한 행을 차지한다.
     *
     * @param block 블록을 담은 JSON 객체
     * @return 그리드 블록 VO
     * @throws ApiException 필수 배치 정보나 식별자가 누락됐거나 값의 형식이 올바르지 않은 경우
     */
    private static GridTemplateBlockVo readGridBlock(JsonNode block) {
        int id = readInteger(block.path("id"), "blocks");
        TemplateBlockType type = readEnum(block.path("type"), TemplateBlockType.class);
        String title = readText(block.path("title"));
        JsonNode placement = readObject(requireValue(block.path("placement"), "placement"));
        GridTemplateBlockPlacementVo blockPlacement = GridTemplateBlockPlacementVo.of(
                readInteger(placement.path("row"), "placement"), readInteger(placement.path("column"), "placement"),
                readInteger(placement.path("columnSpan"), "placement"),
                placement.has("rowSpan") ? readInteger(placement.path("rowSpan"), "placement") : 1);
        return GridTemplateBlockVo.of(id, type, title, blockPlacement);
    }

    /**
     * 요청 순서를 보존하며 소제목과 문단 식별자를 문단형 페이지로 변환한다.
     *
     * @param pageList 요청에서 읽은 페이지 배열
     * @return 문단형 템플릿 구조
     * @throws ApiException 그리드형 필드가 있거나 소제목 및 문단 입력이 누락됐거나 형식이 올바르지 않은 경우
     */
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

    /**
     * 입력값이 JSON 객체인지 확인한다.
     *
     * @param value 확인할 JSON 값
     * @return 확인한 JSON 객체
     * @throws ApiException 값이 JSON 객체가 아닌 경우
     */
    private static JsonNode readObject(JsonNode value) {
        if (!value.isObject()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    /**
     * 필수 입력값이 JSON 배열인지 확인한다.
     *
     * @param value 확인할 JSON 값
     * @param field 누락 시 오류 응답에 표시할 필드명
     * @return 확인한 JSON 배열
     * @throws ApiException 값이 누락됐거나 JSON 배열이 아닌 경우
     */
    private static JsonNode readArray(JsonNode value, String field) {
        if (!requireValue(value, field).isArray()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value;
    }

    /**
     * 필수 JSON 숫자를 정수로 읽는다.
     *
     * @param value 읽을 JSON 값
     * @param field 누락 시 오류 응답에 표시할 필드명
     * @return int 범위의 정수
     * @throws ApiException 값이 누락됐거나 JSON 정수가 아니거나 int 범위를 벗어난 경우
     */
    private static int readInteger(JsonNode value, String field) {
        if (!requireValue(value, field).isIntegralNumber() || !value.canConvertToInt()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value.intValue();
    }

    /**
     * 선택 입력을 이름이 일치하는 열거형 값으로 변환한다.
     *
     * @param <E> 변환할 열거형 타입
     * @param value 읽을 JSON 문자열
     * @param enumClass 변환에 사용할 열거형 클래스
     * @return 이름이 일치하는 열거형 값 또는 입력이 누락되면 null
     * @throws ApiException JSON 문자열 형식이 아니거나 정의되지 않은 코드인 경우
     */
    private static <E extends Enum<E>> E readEnum(JsonNode value, Class<E> enumClass) {
        String code = readText(value);
        try {
            return code == null ? null : Enum.valueOf(enumClass, code);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 선택 입력에서 JSON 문자열을 읽는다.
     *
     * @param value 읽을 JSON 값
     * @return 문자열 값 또는 입력이 누락되면 null
     * @throws ApiException 입력값이 JSON 문자열이 아닌 경우
     */
    private static String readText(JsonNode value) {
        if (isMissing(value)) {
            return null;
        }
        if (!value.isString()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return value.asString();
    }

    /**
     * 필수 JSON 입력이 있는지 확인한다.
     *
     * @param value 확인할 JSON 값
     * @param field 누락 시 오류 응답에 표시할 필드명
     * @return 입력된 JSON 값
     * @throws ApiException 입력이 누락됐거나 JSON null인 경우
     */
    private static JsonNode requireValue(JsonNode value, String field) {
        if (isMissing(value)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of(field, "필수 입력 항목입니다."));
        }
        return value;
    }

    /**
     * 입력이 누락됐거나 JSON null인지 확인한다.
     *
     * @param value 확인할 JSON 값
     * @return 입력이 누락됐거나 null이면 true
     */
    private static boolean isMissing(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode();
    }

    /**
     * 문서 유형, 배치 방식, 제목을 순서대로 검증하고 제목을 정리한다.
     *
     * @param documentType 등록할 문서 유형
     * @param title 등록할 템플릿 제목
     * @param layoutType 호출한 등록 메서드가 지원하는 배치 방식
     * @return 앞뒤 공백을 제거한 제목
     * @throws ApiException 문서 유형이나 제목이 누락됐거나 배치 방식이 문서 유형과 맞지 않는 경우
     */
    private static String validateRegistration(TemplateDocumentType documentType, String title,
                                               TemplateLayoutType layoutType) {
        if (documentType == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("documentType", "문서 유형을 선택해 주세요."));
        }
        if (documentType.getLayoutType() != layoutType) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("structure", "문서 유형에 맞는 템플릿 구조를 사용해 주세요."));
        }
        if (title == null || title.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("title", "템플릿 제목을 입력해 주세요."));
        }
        return title.strip();
    }

    /**
     * 페이지의 블록 수, 식별자, 유형과 배치를 검증한다.
     *
     * @param page 검증할 그리드 페이지
     * @param blockIds 템플릿 전체에 걸쳐 확인한 블록 식별자 집합. 현재 페이지의 식별자도 추가한다.
     * @throws ApiException 블록이 없거나 식별자, 유형 또는 배치가 등록 규칙을 위반한 경우
     */
    private static void validateGridPage(GridTemplatePageVo page, Set<Integer> blockIds) {
        if (page.getBlocks().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("blocks", "페이지에 블록을 한 개 이상 추가해 주세요."));
        }

        List<GridTemplateBlockPlacementVo> placementList = new ArrayList<>();
        for (GridTemplateBlockVo block : page.getBlocks()) {
            if (block.getId() < 1 || !blockIds.add(block.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("blocks", "블록 식별자는 중복되지 않는 양의 정수여야 합니다."));
            }
            if (block.getType() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("blocks", "블록 유형을 선택해 주세요."));
            }
            GridTemplateBlockPlacementVo placement = block.getPlacement();
            validateGridPlacement(placement, placementList);
            placementList.add(placement);
        }
    }

    /**
     * 블록의 행·열 범위와 같은 페이지에서의 겹침을 검증한다.
     *
     * @param placement 확인할 블록 배치
     * @param placementList 같은 페이지에서 이미 확인한 블록 배치 목록
     * @throws ApiException 배치가 허용 범위를 벗어나거나 다른 블록과 겹치는 경우
     */
    private static void validateGridPlacement(GridTemplateBlockPlacementVo placement,
                                              List<GridTemplateBlockPlacementVo> placementList) {
        if (placement == null || placement.getRow() < 1 || placement.getColumn() < 1
                || placement.getColumn() > GRID_COLUMN_COUNT || placement.getColumnSpan() < 1
                || placement.getColumnSpan() > GRID_COLUMN_COUNT - placement.getColumn() + 1
                || placement.getRowSpan() < 1 || placement.getRowSpan() > Integer.MAX_VALUE - placement.getRow() + 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("placement", "블록이 배치 범위를 벗어났습니다."));
        }

        for (GridTemplateBlockPlacementVo existing : placementList) {
            if (rangesOverlap(placement.getRow(), placement.getRowSpan(), existing.getRow(), existing.getRowSpan())
                    && rangesOverlap(placement.getColumn(), placement.getColumnSpan(),
                            existing.getColumn(), existing.getColumnSpan())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("blocks", "블록이 겹칩니다."));
            }
        }
    }

    /**
     * 시작 위치를 포함하고 끝 위치를 제외하는 두 구간이 겹치는지 확인한다.
     *
     * @param start 첫 구간의 시작 위치
     * @param span 첫 구간의 길이
     * @param otherStart 두 번째 구간의 시작 위치
     * @param otherSpan 두 번째 구간의 길이
     * @return 두 구간이 한 칸 이상 겹치면 true
     */
    private static boolean rangesOverlap(int start, int span, int otherStart, int otherSpan) {
        return start < (long) otherStart + otherSpan && otherStart < (long) start + span;
    }

    /**
     * 문항당 문단이 한 개 이상이며 번호가 목록 순서대로 1부터 이어지는지 검증한다.
     *
     * @param paragraphIdList 한 문항의 문단 식별자 목록
     * @throws ApiException 문단이 없거나 번호가 1부터 연속하지 않는 경우
     */
    private static void validateParagraphOrder(List<Integer> paragraphIdList) {
        if (paragraphIdList.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("paragraphIds", "문항마다 문단을 한 개 이상 추가해 주세요."));
        }
        for (int index = 0; index < paragraphIdList.size(); index++) {
            if (paragraphIdList.get(index) != index + 1) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("paragraphIds", "문단 번호는 문항마다 1부터 순서대로 매겨 주세요."));
            }
        }
    }

    /**
     * 검증한 템플릿을 현재 트랜잭션에서 저장한다.
     *
     * @param documentType 문서 유형
     * @param title 앞뒤 공백을 제거한 템플릿 제목
     * @param structureJson JSONB 컬럼에 저장할 편집 구조 JSON
     * @return 저장된 템플릿 식별자
     */
    private long save(TemplateDocumentType documentType, String title, String structureJson) {
        TemplateVo template = TemplateVo.of(documentType, title, structureJson);
        return this.templateRepository.save(template.convertToTemplateEntity()).getId();
    }
}
