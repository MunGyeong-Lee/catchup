package com.moon.catchup.domain.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 여러 A4 페이지의 용지 방향, 블록 식별자와 그리드 배치가 보존되는지 확인한다. */
public class GridTemplateStructureVoTest {
    /** 같은 행의 가로 배치와 다른 행의 세로 배치, 페이지별 방향을 함께 담는다. */
    @Test
    public void keepsPageAndBlockOrderWithMixedOrientations() {
        GridTemplateBlockVo photo = GridTemplateBlockVo.of(1, TemplateBlockType.IMAGE_PLACEHOLDER, null,
                GridTemplateBlockPlacementVo.of(1, 1, 4));
        GridTemplateBlockVo motivation = textBlock(2, 1, 5, 8);
        GridTemplateBlockVo introduction = textBlock(3, 2, 1, 12);
        List<GridTemplateBlockVo> blockList = new ArrayList<>(List.of(photo, motivation, introduction));
        GridTemplatePageVo portrait = GridTemplatePageVo.of(null, blockList);
        GridTemplatePageVo landscape = GridTemplatePageVo.of(TemplatePageOrientation.LANDSCAPE,
                List.of(textBlock(4, 1, 1, 12)));
        List<GridTemplatePageVo> pageList = new ArrayList<>(List.of(portrait, landscape));

        GridTemplateStructureVo structure = GridTemplateStructureVo.of(pageList);
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
        GridTemplateBlockVo block = textBlock(7, 2, 5, 8);
        GridTemplatePageVo portrait = GridTemplatePageVo.of(null, List.of(block));

        GridTemplatePageVo landscape = portrait.withOrientation(TemplatePageOrientation.LANDSCAPE);

        assertThat(portrait.getOrientation()).isEqualTo(TemplatePageOrientation.PORTRAIT);
        assertThat(landscape.getOrientation()).isEqualTo(TemplatePageOrientation.LANDSCAPE);
        assertThat(landscape.getBlocks()).containsExactly(block);
        assertThat(landscape.withOrientation(TemplatePageOrientation.PORTRAIT).getBlocks()).containsExactly(block);
    }

    /** 유효한 위치의 텍스트 블록을 만든다. */
    private static GridTemplateBlockVo textBlock(int id, int row, int column, int columnSpan) {
        return GridTemplateBlockVo.of(id, TemplateBlockType.TEXT, "문단",
                GridTemplateBlockPlacementVo.of(row, column, columnSpan));
    }
}
