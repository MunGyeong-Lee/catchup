package com.moon.catchup.domain.template;

import java.util.List;

import lombok.Getter;

/** A4 용지의 방향과 그리드에 배치한 블록 목록을 담는다. */
@Getter
public final class GridTemplatePageVo {
    private final TemplatePageOrientation orientation;
    private final List<GridTemplateBlockVo> blocks;

    /**
     * 용지 방향을 정하고 블록을 기존 순서대로 복사한다.
     *
     * @param orientation 용지 방향. 생략하면 세로
     * @param blockList 페이지에 배치한 블록 목록
     */
    private GridTemplatePageVo(TemplatePageOrientation orientation, List<GridTemplateBlockVo> blockList) {
        this.orientation = orientation == null ? TemplatePageOrientation.PORTRAIT : orientation;
        this.blocks = List.copyOf(blockList);
    }

    /**
     * A4 페이지를 만들고 블록 순서를 보존한다.
     *
     * @param orientation 용지 방향. 생략하면 세로
     * @param blockList 페이지에 배치한 블록 목록
     * @return 템플릿 페이지
     */
    public static GridTemplatePageVo of(TemplatePageOrientation orientation, List<GridTemplateBlockVo> blockList) {
        return new GridTemplatePageVo(orientation, blockList);
    }

    /**
     * 블록의 식별자와 배치를 보존한 채 A4 용지 방향을 변경한다.
     *
     * @param orientation 새 용지 방향. 생략하면 세로
     * @return 새 방향을 적용한 페이지
     */
    public GridTemplatePageVo withOrientation(TemplatePageOrientation orientation) {
        return new GridTemplatePageVo(orientation, this.blocks);
    }
}
