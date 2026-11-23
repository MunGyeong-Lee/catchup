package com.moon.catchup.domain.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 여러 A4 페이지의 용지 방향, 블록 식별자와 그리드 배치가 보존되는지 확인한다. */
public class TemplateStructureVoTest {
    /** 같은 행의 가로 배치와 다른 행의 세로 배치, 페이지별 방향을 함께 담는다. */
    @Test
    public void keepsPageAndBlockOrderWithMixedOrientations() {
        TemplateBlockVo photo = TemplateBlockVo.of(1, TemplateBlockType.IMAGE_PLACEHOLDER, null,
                TemplateBlockPlacementVo.of(1, 1, 4));
        TemplateBlockVo motivation = textBlock(2, 1, 5, 8);
        TemplateBlockVo introduction = textBlock(3, 2, 1, 12);
        List<TemplateBlockVo> blockList = new ArrayList<>(List.of(photo, motivation, introduction));
        TemplatePageVo portrait = TemplatePageVo.of(null, blockList);
        TemplatePageVo landscape = TemplatePageVo.of(TemplatePageOrientation.LANDSCAPE,
                List.of(textBlock(4, 1, 1, 12)));
        List<TemplatePageVo> pageList = new ArrayList<>(List.of(portrait, landscape));

        TemplateStructureVo structure = TemplateStructureVo.of(pageList);
        blockList.clear();
        pageList.clear();

        assertThat(structure.getPages()).containsExactly(portrait, landscape);
        assertThat(portrait.getBlocks()).containsExactly(photo, motivation, introduction);
        assertThat(portrait.getOrientation()).isEqualTo(TemplatePageOrientation.PORTRAIT);
        assertThat(portrait.getOrientation().getWidth()).isEqualTo(210);
        assertThat(portrait.getOrientation().getHeight()).isEqualTo(297);
        assertThat(landscape.getOrientation().getWidth()).isEqualTo(297);
        assertThat(landscape.getOrientation().getHeight()).isEqualTo(210);
    }

    /** 방향 변경 전후에 블록 식별자와 그리드 위치를 보존한다. */
    @Test
    public void preservesBlockIdsAndPlacementWhenChangingOrientation() {
        TemplateBlockVo block = textBlock(7, 2, 5, 8);
        TemplatePageVo portrait = TemplatePageVo.of(null, List.of(block));

        TemplatePageVo landscape = portrait.withOrientation(TemplatePageOrientation.LANDSCAPE);

        assertThat(portrait.getOrientation()).isEqualTo(TemplatePageOrientation.PORTRAIT);
        assertThat(landscape.getOrientation()).isEqualTo(TemplatePageOrientation.LANDSCAPE);
        assertThat(landscape.getBlocks()).containsExactly(block);
        assertThat(landscape.withOrientation(TemplatePageOrientation.PORTRAIT).getBlocks()).containsExactly(block);
    }

    /** 유효한 위치의 텍스트 블록을 만든다. */
    private static TemplateBlockVo textBlock(int id, int row, int column, int columnSpan) {
        return TemplateBlockVo.of(id, TemplateBlockType.TEXT, "문단",
                TemplateBlockPlacementVo.of(row, column, columnSpan));
    }
}
