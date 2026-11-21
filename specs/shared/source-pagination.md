# 소스 목록 페이지

`GET /api/sources`는 `page_number`와 `page_size`를 받는다. 페이지 번호는 1부터 시작하며 기본값은 1이다. 페이지 크기의 기본값은 20이며, 허용 범위는 1~100이다. 범위를 벗어난 값은 HTTP 400으로 반환한다.

정상 응답의 `response`에는 `items`, `pageNumber`, `pageSize`, `totalPages`, `totalElements`가 있다. 마지막 페이지 다음을 요청하면 요청한 페이지 번호와 전체 건수를 유지하고 빈 `items`를 응답한다. 소스가 없으면 전체 페이지와 건수가 모두 0이다.

페이지 데이터는 공통 `PageResponse<T>`로 직렬화한다. 소스 목록의 항목 타입은 `SourceSummaryResponse`이며 OpenAPI에도 이 타입을 명시한다. 기존 성공 및 실패 응답 계약을 유지한다.

DB는 요청한 페이지만 읽는다. 선택 `order_by`는 쉼표로 구분한 `{property}:{ASC|DESC}` 목록이며 `createdAt`, `sourceId`, `sourceType`, `name`만 허용한다. 생략한 등록 시각과 소스 ID는 각 내림차순으로 뒤에 추가한다. 중복 속성이나 유효하지 않은 조건에는 HTTP 400과 `field_errors.order_by`를 반환한다. 목록 항목은 기존처럼 저장된 `sources.name`을 `displayName`에 담으며 이력 구분·시작 연월, 자격증 취득일은 각 유형 응답에 유지한다. 화면은 이전·다음으로 페이지를 이동하고, 삭제 후 현재 페이지를 갱신한다. 마지막 페이지가 사라지면 새 마지막 페이지를 읽는다.

검증은 기본값, 페이지 크기 상한, 정렬 동률, 유형별 표시 정보, 범위 초과 페이지, 잘못된 매개변수와 삭제 후 페이지 이동을 확인한다.

## 미결정 사항

없음.
