package com.moon.catchup.service.template;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
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

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
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

    private static void validateGridPage(GridTemplatePageVo page, Set<Integer> blockIds) {
        if (page.getBlocks().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("blocks", "페이지에 블록을 한 개 이상 추가해 주세요."));
        }

        Map<Integer, boolean[]> occupiedColumnsByRow = new HashMap<>();
        for (GridTemplateBlockVo block : page.getBlocks()) {
            if (block.getId() < 1 || !blockIds.add(block.getId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("blocks", "블록 식별자는 중복되지 않는 양의 정수여야 합니다."));
            }
            if (block.getType() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("blocks", "블록 유형을 선택해 주세요."));
            }
            validateGridPlacement(block, occupiedColumnsByRow);
        }
    }

    private static void validateGridPlacement(GridTemplateBlockVo block, Map<Integer, boolean[]> occupiedColumnsByRow) {
        GridTemplateBlockPlacementVo placement = block.getPlacement();
        if (placement == null || placement.getRow() < 1 || placement.getColumn() < 1
                || placement.getColumn() > GRID_COLUMN_COUNT || placement.getColumnSpan() < 1
                || placement.getColumnSpan() > GRID_COLUMN_COUNT - placement.getColumn() + 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    Map.of("placement", "블록이 배치 범위를 벗어났습니다."));
        }

        boolean[] occupiedColumns = occupiedColumnsByRow.computeIfAbsent(placement.getRow(),
                row -> new boolean[GRID_COLUMN_COUNT]);
        int start = placement.getColumn() - 1;
        int end = start + placement.getColumnSpan();
        for (int column = start; column < end; column++) {
            if (occupiedColumns[column]) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        Map.of("blocks", "블록이 겹칩니다."));
            }
            occupiedColumns[column] = true;
        }
    }

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

    private long save(TemplateDocumentType documentType, String title, String structureJson) {
        TemplateVo template = new TemplateVo(null, documentType, title, structureJson, null);
        return this.templateRepository.save(template.convertToTemplateEntity()).getId();
    }
}
