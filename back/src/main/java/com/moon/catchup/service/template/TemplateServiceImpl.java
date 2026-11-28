package com.moon.catchup.service.template;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.controller.template.dto.GridTemplateCreateRequest;
import com.moon.catchup.controller.template.dto.GridTemplateStructureRequest;
import com.moon.catchup.controller.template.dto.ParagraphTemplateCreateRequest;
import com.moon.catchup.controller.template.dto.ParagraphTemplateStructureRequest;
import com.moon.catchup.controller.template.dto.TemplateCreateRequest;
import com.moon.catchup.domain.template.GridTemplateBlockPlacementVo;
import com.moon.catchup.domain.template.GridTemplateBlockVo;
import com.moon.catchup.domain.template.GridTemplatePageVo;
import com.moon.catchup.domain.template.GridTemplateStructureVo;
import com.moon.catchup.domain.template.ParagraphTemplatePageVo;
import com.moon.catchup.domain.template.ParagraphTemplateStructureVo;
import com.moon.catchup.domain.template.TemplateDocumentType;
import com.moon.catchup.domain.template.TemplateLayoutType;
import com.moon.catchup.domain.template.TemplateVo;
import com.moon.catchup.repository.template.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
        if (!(req instanceof GridTemplateCreateRequest request)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("structure", "그리드형 페이지를 사용해 주세요."));
        }
        GridTemplateStructureVo gridTemplateStructureVo = toGridStructure(request.getStructure());
        return this.createGrid(documentType, req.getTitle(), gridTemplateStructureVo);
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
        if (!(req instanceof ParagraphTemplateCreateRequest request)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("structure", "문단형 페이지를 사용해 주세요."));
        }
        ParagraphTemplateStructureVo paragraphTemplateStructureVo = toParagraphStructure(request.getStructure());
        return this.createParagraph(documentType, req.getTitle(), paragraphTemplateStructureVo);
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
     * 그리드형 요청의 페이지와 블록을 요청 순서대로 VO로 변환한다.
     *
     * @param structure 그리드형 편집 구조
     * @return 그리드형 템플릿 구조
     */
    private static GridTemplateStructureVo toGridStructure(GridTemplateStructureRequest structure) {
        List<GridTemplatePageVo> pageList = new ArrayList<>();
        for (GridTemplateStructureRequest.Page page : requireValue(structure.getPages(), "pages")) {
            if (page == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
            List<GridTemplateBlockVo> blockList = requireValue(page.getBlocks(), "blocks").stream()
                    .map(TemplateServiceImpl::toGridBlock).toList();
            pageList.add(GridTemplatePageVo.of(page.getOrientation(), blockList));
        }
        return GridTemplateStructureVo.of(pageList);
    }

    /**
     * 그리드 블록의 입력값과 배치를 VO로 변환한다.
     *
     * @param block 그리드 블록 요청
     * @return 그리드 블록 VO
     */
    private static GridTemplateBlockVo toGridBlock(GridTemplateStructureRequest.Block block) {
        if (block == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        int id = requireValue(block.getId(), "blocks");
        GridTemplateStructureRequest.Placement placement = requireValue(block.getPlacement(), "placement");
        GridTemplateBlockPlacementVo blockPlacement = GridTemplateBlockPlacementVo.of(
                requireValue(placement.getRow(), "placement"), requireValue(placement.getColumn(), "placement"),
                requireValue(placement.getColumnSpan(), "placement"), requireValue(placement.getRowSpan(), "placement"));
        return GridTemplateBlockVo.of(id, block.getType(), block.getTitle(), blockPlacement);
    }

    /**
     * 문단형 요청의 소제목과 문단 식별자를 요청 순서대로 VO로 변환한다.
     *
     * @param structure 문단형 편집 구조
     * @return 문단형 템플릿 구조
     */
    private static ParagraphTemplateStructureVo toParagraphStructure(ParagraphTemplateStructureRequest structure) {
        List<ParagraphTemplatePageVo> pageList = new ArrayList<>();
        for (ParagraphTemplateStructureRequest.Page page : requireValue(structure.getPages(), "pages")) {
            if (page == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
            int headingId = requireValue(page.getHeadingId(), "headingId");
            List<Integer> paragraphIdList = requireValue(page.getParagraphIds(), "paragraphIds");
            for (Integer paragraphId : paragraphIdList) {
                requireValue(paragraphId, "paragraphIds");
            }
            pageList.add(ParagraphTemplatePageVo.of(headingId, paragraphIdList));
        }
        return ParagraphTemplateStructureVo.of(pageList);
    }

    /**
     * 등록 요청의 필수 구조값을 확인한다.
     *
     * @param <T> 구조값 타입
     * @param value 확인할 값
     * @param field 오류 응답에 사용할 필드명
     * @return 입력된 값
     * @throws ApiException 입력이 누락된 경우
     */
    private static <T> T requireValue(T value, String field) {
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of(field, "필수 입력 항목입니다."));
        }
        return value;
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
