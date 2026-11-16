package com.moon.catchup.careeritem.domain;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 자료 목록에 표시하는 자료 유형별 요약 정보를 담는다. */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class CareerItemSummaryVo {
    private final long id;
    private final CareerItemType type;
    private final String displayName;
    private final String historyType;
    private final String startMonth;
    private final String issuedOn;
}
