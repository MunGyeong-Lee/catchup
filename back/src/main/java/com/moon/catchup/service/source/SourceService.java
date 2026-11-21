package com.moon.catchup.service.source;

import java.util.Map;

import com.moon.catchup.domain.source.SourceSummaryVo;
import org.springframework.data.domain.Page;

/** SourceService의 공개 업무 계약이다. */
public interface SourceService {
    /**
     * 검증된 입력을 등록하고 소스 식별자를 반환한다.
     *
     * @param sourceType 요청 소스 유형 코드
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @return 저장된 소스 식별자
     * @throws com.moon.catchup.common.http.ApiException 소스 이름이나 유형별 입력이 유효하지 않은 경우
     */
    public long register(String sourceType, Object name, Map<String, Object> details);

    /**
     * 소스 목록에 표시할 요약을 최신 등록 순서로 반환한다.
     *
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize 페이지당 항목 수
     * @param orderBy 쉼표로 연결한 선택 정렬 조건
     * @return 현재 페이지의 소스 요약과 전체 건수
     * @throws com.moon.catchup.common.http.ApiException 페이지 번호나 크기가 허용 범위를 벗어난 경우
     */
    public Page<SourceSummaryVo> list(int pageNumber, int pageSize, String orderBy);
}
