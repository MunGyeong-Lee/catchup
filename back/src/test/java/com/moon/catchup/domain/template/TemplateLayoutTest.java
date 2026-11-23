package com.moon.catchup.domain.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 문서 유형별 배치 방식과 자기소개서의 문항·문단 순서를 검증한다. */
public class TemplateLayoutTest {

    /** 문서 유형을 선택하면 정해진 템플릿 배치 방식을 사용한다. */
    @ParameterizedTest
    @CsvSource({"RESUME, GRID", "PORTFOLIO, GRID", "CAREER_DESCRIPTION, GRID", "COVER_LETTER, PARAGRAPH"})
    public void determinesLayoutFromDocumentType(TemplateDocumentType documentType, TemplateLayoutType layoutType) {
        assertThat(documentType.getLayoutType()).isEqualTo(layoutType);
    }

    /** 각 문항의 페이지와 소제목 하나 뒤에 이어지는 문단 자리의 순서를 보존한다. */
    @Test
    public void preservesQuestionPagesAndParagraphOrder() {
        List<Integer> paragraphIdList = new ArrayList<>(List.of(4, 2, 3));
        ParagraphTemplatePageVo firstQuestion = ParagraphTemplatePageVo.of(1, paragraphIdList);
        ParagraphTemplatePageVo secondQuestion = ParagraphTemplatePageVo.of(5, List.of(6, 7));
        List<ParagraphTemplatePageVo> pageList = new ArrayList<>(List.of(firstQuestion, secondQuestion));

        ParagraphTemplateStructureVo structure = ParagraphTemplateStructureVo.of(pageList);
        paragraphIdList.clear();
        pageList.clear();

        assertThat(structure.getPages()).containsExactly(firstQuestion, secondQuestion);
        assertThat(firstQuestion.getHeadingId()).isEqualTo(1);
        assertThat(firstQuestion.getParagraphIds()).containsExactly(4, 2, 3);
        assertThat(secondQuestion.getHeadingId()).isEqualTo(5);
        assertThat(secondQuestion.getParagraphIds()).containsExactly(6, 7);
    }
}
