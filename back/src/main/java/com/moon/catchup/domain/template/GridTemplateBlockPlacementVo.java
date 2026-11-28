package com.moon.catchup.domain.template;

import lombok.Getter;

/** A4 페이지의 12열 그리드에서 블록의 시작 위치와 크기를 담는다. */
@Getter
public final class GridTemplateBlockPlacementVo {
    private final int row;
    private final int column;
    private final int columnSpan;
    private final int rowSpan;

    /**
     * 블록의 시작 위치와 크기를 초기화한다.
     *
     * @param row 시작 행
     * @param column 시작 열
     * @param columnSpan 차지하는 열 수
     * @param rowSpan 차지하는 행 수
     */
    private GridTemplateBlockPlacementVo(int row, int column, int columnSpan, int rowSpan) {
        this.row = row;
        this.column = column;
        this.columnSpan = columnSpan;
        this.rowSpan = rowSpan;
    }

    /**
     * 주어진 시작 위치와 행·열 크기를 담는다.
     *
     * @param row 시작 행
     * @param column 시작 열
     * @param columnSpan 차지하는 열 수
     * @param rowSpan 차지하는 행 수
     * @return 그리드 배치
     */
    public static GridTemplateBlockPlacementVo of(int row, int column, int columnSpan, int rowSpan) {
        return new GridTemplateBlockPlacementVo(row, column, columnSpan, rowSpan);
    }
}
