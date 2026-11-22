package com.moon.catchup.service.source;

import java.util.Map;

import com.moon.catchup.domain.source.SourceSummaryVo;
import com.moon.catchup.domain.source.SourceType;
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
     * 모든 유형의 소스 목록을 {@link #list(int, int, String, SourceType)}의 정렬·페이지 규칙으로 조회한다.
     *
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize 페이지당 항목 수
     * @param orderBy 쉼표로 연결한 선택 정렬 조건
     * @return 현재 페이지의 소스 요약과 전체 건수
     * @throws com.moon.catchup.common.http.ApiException 페이지 값이나 정렬 조건이 유효하지 않은 경우
     */
    public Page<SourceSummaryVo> list(int pageNumber, int pageSize, String orderBy);

    /**
     * 지정한 소스 유형의 목록을 필터링한 뒤 정렬하고 페이지를 반환한다.
     *
     * <p>정렬에서 생략한 등록 시각과 소스 식별자에 각각 내림차순을 적용한다.
     * 범위를 벗어난 페이지는 항목 없이 해당 유형의 전체 건수를 유지한다.
     *
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize 페이지당 항목 수
     * @param orderBy 쉼표로 연결한 선택 정렬 조건
     * @param sourceType 조회할 소스 유형. null이면 전체 유형 조회
     * @return 현재 페이지의 소스 요약과 필터 조건에 해당하는 전체 건수
     * @throws com.moon.catchup.common.http.ApiException 페이지 값이나 정렬 조건이 유효하지 않은 경우
     */
    public Page<SourceSummaryVo> list(int pageNumber, int pageSize, String orderBy, SourceType sourceType);
}
