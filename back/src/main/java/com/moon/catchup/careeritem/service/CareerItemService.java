package com.moon.catchup.careeritem.service;

import java.util.List;
import java.util.Map;

import com.moon.catchup.careeritem.domain.CareerItemSummaryVo;

/** CareerItemService의 공개 업무 계약이다. */
public interface CareerItemService {
    /** 검증된 입력을 등록하고 자료 식별자를 반환한다. */
    public long register(String careerItemType, Map<String, Object> details);

    /** 자료 목록에 표시할 요약을 최신 등록 순서로 반환한다. */
    public List<CareerItemSummaryVo> list();
}
