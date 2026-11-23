package com.moon.catchup.service.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.domain.template.GridTemplateBlockPlacementVo;
import com.moon.catchup.domain.template.GridTemplateBlockVo;
import com.moon.catchup.domain.template.GridTemplatePageVo;
import com.moon.catchup.domain.template.GridTemplateStructureVo;
import com.moon.catchup.domain.template.ParagraphTemplatePageVo;
import com.moon.catchup.domain.template.ParagraphTemplateStructureVo;
import com.moon.catchup.domain.template.TemplateBlockType;
import com.moon.catchup.domain.template.TemplateDocumentType;
import com.moon.catchup.domain.template.TemplateEntity;
import com.moon.catchup.domain.template.TemplatePageOrientation;
import com.moon.catchup.repository.template.TemplateRepository;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 템플릿 등록의 입력 검증과 PostgreSQL JSONB 저장을 확인한다. */
@SpringBootTest
@Transactional
public class TemplateCreateTest {
    @Autowired private TemplateService templateService;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JsonMapper jsonMapper;

    /** 그리드형 문서 유형과 제목, 페이지 방향과 블록 배치를 저장한다. */
    @Test
    public void createsGridTemplate() {
        GridTemplatePageVo page = GridTemplatePageVo.of(TemplatePageOrientation.LANDSCAPE,
                List.of(block(1, 1, 1, 4), block(2, 1, 5, 8), block(3, 2, 1, 12)));
        long templateId = this.templateService.createGrid(TemplateDocumentType.RESUME, "  기본 이력서  ",
                GridTemplateStructureVo.of(List.of(page)));

        TemplateEntity saved = this.reload(templateId);
        JsonNode savedPage = this.jsonMapper.readTree(saved.getStructureJson()).path("pages").get(0);
        assertThat(saved.getDocumentType()).isEqualTo(TemplateDocumentType.RESUME);
        assertThat(saved.getTitle()).isEqualTo("기본 이력서");
        assertThat(savedPage.path("orientation").asText()).isEqualTo("LANDSCAPE");
        assertThat(savedPage.path("blocks").get(1).path("placement").path("column").intValue()).isEqualTo(5);
        assertThat(savedPage.path("blocks").get(2).path("placement").path("row").intValue()).isEqualTo(2);
        assertThat(savedPage.has("layout")).isFalse();
    }

    /** 문항마다 문단 번호를 1부터 시작하고 페이지와 문단 순서를 보존한다. */
    @Test
    public void createsParagraphTemplateWithLocalSequences() {
        ParagraphTemplateStructureVo structure = ParagraphTemplateStructureVo.of(List.of(
                ParagraphTemplatePageVo.of(1, List.of(1, 2, 3)),
                ParagraphTemplatePageVo.of(2, List.of(1))));
        long templateId = this.templateService.createParagraph(TemplateDocumentType.COVER_LETTER, "자기소개서", structure);

        TemplateEntity saved = this.reload(templateId);
        JsonNode savedStructure = this.jsonMapper.readTree(saved.getStructureJson());
        assertThat(saved.getDocumentType()).isEqualTo(TemplateDocumentType.COVER_LETTER);
        assertThat(savedStructure).isEqualTo(this.jsonMapper.readTree("""
                {"pages":[{"headingId":1,"paragraphIds":[1,2,3]},{"headingId":2,"paragraphIds":[1]}]}
                """));
    }

    /** 제목 누락과 문서 유형에 맞지 않는 구조를 저장 전에 거부한다. */
    @Test
    public void rejectsInvalidRegistrationInput() {
        GridTemplateStructureVo grid = grid(block(1, 1, 1, 12));
        ParagraphTemplateStructureVo paragraph = ParagraphTemplateStructureVo.of(
                List.of(ParagraphTemplatePageVo.of(1, List.of(1))));
        long count = this.templateRepository.count();

        assertValidationFailure("title", () -> this.templateService.createGrid(TemplateDocumentType.RESUME, "  ", grid));
        assertValidationFailure("documentType", () -> this.templateService.createGrid(null, "이력서", grid));
        assertValidationFailure("structure",
                () -> this.templateService.createGrid(TemplateDocumentType.COVER_LETTER, "자기소개서", grid));
        assertValidationFailure("structure",
                () -> this.templateService.createParagraph(TemplateDocumentType.RESUME, "이력서", paragraph));
        assertThat(this.templateRepository.count()).isEqualTo(count);
    }

    /** 빈 페이지, 누락된 블록 유형·배치, 중복 ID 및 겹치는 블록을 거부한다. */
    @Test
    public void rejectsInvalidGridStructure() {
        assertValidationFailure("pages", () -> this.templateService.createGrid(TemplateDocumentType.RESUME, "이력서", null));
        assertValidationFailure("blocks", () -> this.templateService.createGrid(TemplateDocumentType.RESUME, "이력서", grid()));
        assertValidationFailure("blocks", () -> this.templateService.createGrid(TemplateDocumentType.RESUME, "이력서",
                grid(GridTemplateBlockVo.of(1, null, null, GridTemplateBlockPlacementVo.of(1, 1, 1)))));
        assertValidationFailure("placement", () -> this.templateService.createGrid(TemplateDocumentType.RESUME, "이력서",
                grid(GridTemplateBlockVo.of(1, TemplateBlockType.TEXT, null, null))));
        assertValidationFailure("blocks", () -> this.templateService.createGrid(TemplateDocumentType.RESUME, "이력서",
                GridTemplateStructureVo.of(List.of(
                        GridTemplatePageVo.of(null, List.of(block(1, 1, 1, 12))),
                        GridTemplatePageVo.of(null, List.of(block(1, 1, 1, 12)))))));
        assertThatThrownBy(() -> this.templateService.createGrid(TemplateDocumentType.RESUME, "이력서",
                grid(block(1, 1, 1, 6), block(2, 1, 6, 7))))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getFieldErrors()).containsEntry("blocks", "블록이 겹칩니다.");
                });
    }

    /** 행·열·너비가 허용 범위를 벗어나는 그리드 배치를 거부한다. */
    @ParameterizedTest
    @CsvSource({"0,1,1", "1,0,1", "1,13,1", "1,12,2", "1,1,0"})
    public void rejectsOutOfBoundsPlacement(int row, int column, int columnSpan) {
        assertValidationFailure("placement", () -> this.templateService.createGrid(TemplateDocumentType.RESUME, "이력서",
                grid(block(1, row, column, columnSpan))));
    }

    /** 문단이 없거나 번호가 건너뛰거나 순서가 바뀐 문항을 거부한다. */
    @Test
    public void rejectsInvalidParagraphSequences() {
        for (List<Integer> paragraphIdList : List.of(List.<Integer>of(), List.of(2), List.of(1, 3), List.of(2, 1),
                List.of(1, 1))) {
            ParagraphTemplateStructureVo structure = ParagraphTemplateStructureVo.of(
                    List.of(ParagraphTemplatePageVo.of(1, paragraphIdList)));
            assertValidationFailure("paragraphIds",
                    () -> this.templateService.createParagraph(TemplateDocumentType.COVER_LETTER, "자기소개서", structure));
        }
    }

    /** 문항 페이지 누락과 같거나 유효하지 않은 소제목 식별자를 거부한다. */
    @Test
    public void rejectsMissingQuestionPagesAndInvalidHeadings() {
        assertValidationFailure("pages", () -> this.templateService.createParagraph(
                TemplateDocumentType.COVER_LETTER, "자기소개서", ParagraphTemplateStructureVo.of(List.of())));
        ParagraphTemplatePageVo first = ParagraphTemplatePageVo.of(1, List.of(1));
        ParagraphTemplatePageVo second = ParagraphTemplatePageVo.of(1, List.of(1, 2));
        ParagraphTemplatePageVo invalid = ParagraphTemplatePageVo.of(0, List.of(1));
        assertValidationFailure("headingId", () -> this.templateService.createParagraph(
                TemplateDocumentType.COVER_LETTER, "자기소개서", ParagraphTemplateStructureVo.of(List.of(first, second))));
        assertValidationFailure("headingId", () -> this.templateService.createParagraph(
                TemplateDocumentType.COVER_LETTER, "자기소개서", ParagraphTemplateStructureVo.of(List.of(invalid))));
    }

    private TemplateEntity reload(long templateId) {
        this.entityManager.flush();
        this.entityManager.clear();
        return this.templateRepository.findById(templateId).orElseThrow();
    }

    private static GridTemplateStructureVo grid(GridTemplateBlockVo... blocks) {
        return GridTemplateStructureVo.of(List.of(GridTemplatePageVo.of(null, List.of(blocks))));
    }

    private static GridTemplateBlockVo block(int id, int row, int column, int columnSpan) {
        return GridTemplateBlockVo.of(id, TemplateBlockType.TEXT, null,
                GridTemplateBlockPlacementVo.of(row, column, columnSpan));
    }

    private static void assertValidationFailure(String field, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(exception.getFieldErrors()).containsOnlyKeys(field);
        });
    }
}
