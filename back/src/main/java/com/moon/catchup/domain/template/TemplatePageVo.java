package com.moon.catchup.domain.template;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** A4 용지의 방향과 그리드에 배치한 블록 목록을 담는다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class TemplatePageVo {
    private final TemplatePageOrientation orientation;
    private final List<TemplateBlockVo> blocks;

    /**
     * A4 페이지를 만들고 블록 순서를 보존한다.
     *
     * @param orientation 용지 방향. 생략하면 세로
     * @param blockList 페이지에 배치한 블록 목록
     * @return 템플릿 페이지
     */
    public static TemplatePageVo of(TemplatePageOrientation orientation, List<TemplateBlockVo> blockList) {
        TemplatePageOrientation pageOrientation = orientation == null ? TemplatePageOrientation.PORTRAIT : orientation;
        return new TemplatePageVo(pageOrientation, List.copyOf(blockList));
    }

    /**
     * 블록의 식별자와 배치를 보존한 채 A4 용지 방향을 변경한다.
     *
     * @param orientation 새 용지 방향. 생략하면 세로
     * @return 새 방향을 적용한 페이지
     */
    public TemplatePageVo withOrientation(TemplatePageOrientation orientation) {
        TemplatePageOrientation pageOrientation = orientation == null ? TemplatePageOrientation.PORTRAIT : orientation;
        return new TemplatePageVo(pageOrientation, this.blocks);
    }
}
