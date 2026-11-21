# 소스 유형별 목록 필터 제공

## 목차

- [1.0 이슈 설명](#section-1-0)
- [2.0 구현 범위](#section-2-0)
- [3.0 구현 내역 및 PR 계획](#section-3-0)
- [4.0 완료 조건](#section-4-0)

**대상 요청** `GET /api/sources`

**선행 이슈** [지원회사·문체·기타 텍스트 등록 지원](01-source-create.md)

<a name="section-1-0"></a>

## 1.0 이슈 설명

문서 작성의 소재, 지원회사, 문체 및 어투 선택에서 필요한 소스 유형만 조회할 수 있게 한다.

<a name="section-2-0"></a>

## 2.0 구현 범위

- 목록 응답의 `displayName`은 `sources.name`을 사용한다. [소스 이름 계약](../shared/source-name.md)

- 소스 목록은 [페이지 응답 계약](../shared/source-pagination.md)에 따라 반환한다. 선택 정렬 `order_by`는 공개 속성과 방향의 목록을 받는다.
- 후속 작업에서 sourceType 선택 조건을 추가하며, 생략하면 유형에 관계없이 조회한다.
- 신규 소스 유형의 표시 이름을 정의하고 최근 등록 순과 동률 순서를 유지한다.

<a name="section-3-0"></a>

## 3.0 구현 내역 및 PR 계획

각 변경 요청은 생성·수정 파일을 테스트·문서·스키마·설정을 포함해 최대 5개로 제한한다. 아래 경로는 저장소 루트 기준의 예상 위치다. 신규 클래스 이름은 설계 시 확정한다. 번호 순으로 진행하며 이전 변경 요청이 병합된 뒤 다음 변경 요청을 시작한다. 실제 변경에서 추가 파일이 필요하면 변경 요청을 더 나눈다.

- [ ] PR 1. 유형 필터와 표시 이름 조회
  - 예상 생성·수정 파일 4개
  - `back/src/main/java/com/moon/catchup/repository/source/SourceRepository.java`
  - `back/src/main/java/com/moon/catchup/service/source/SourceService.java`
  - `back/src/main/java/com/moon/catchup/service/source/SourceServiceImpl.java`
  - `back/src/test/java/com/moon/catchup/repository/source/SourceFilteredListTest.java`
  - 유형별 결과, 0건, 정렬을 검증한 뒤 조회 조건과 신규 유형의 표시 이름을 구현한다.

- [ ] PR 2. 목록 요청 조건 연결
  - 예상 생성·수정 파일 4개
  - `back/src/main/java/com/moon/catchup/controller/source/SourceController.java`
  - `back/src/main/java/com/moon/catchup/controller/source/SourceDocs.java`
  - `back/src/test/java/com/moon/catchup/controller/source/SourceFilteredListIntegrationTest.java`
  - `specs/issues/contracts/source-list.md`
  - 유형 조건과 잘못된 유형의 오류 응답을 문서화한다.
  - 이 이슈의 PR 1 병합 후 진행한다.

- [ ] PR 3. 프론트엔드 작업 및 화면 연결 — 소스 유형 필터 화면
  - 예상 생성·수정 파일 3개
  - `front/src/SourceList.jsx`
  - `front/src/SourceList.css`
  - `front/tests/02-source-list.spec.js`
  - 전체 및 유형별 선택을 목록 요청의 sourceType에 연결한다. 조회 중, 빈 목록, 실패와 다시 시도를 제공하고 조건 변경 중 이전 요청이 최신 결과를 덮어쓰지 않게 한다.
  - 이 이슈의 PR 2 및 화면에서 재사용할 선행 이슈의 백엔드 계약 병합 후 진행한다. 프론트엔드 공통 컴포넌트가 이미 있으면 재사용한다.
  - 구현 후 단위 및 브라우저 통합 검증으로 정상·실패 상황을 확인하고 `npm run lint`, `npm run build`를 실행한다. 브라우저 검증 도구 설정이 추가로 필요하면 선행 준비 변경 요청을 분리해 마지막 프론트엔드 변경 요청의 파일 수를 5개 이하로 유지한다.

<a name="section-4-0"></a>

## 4.0 완료 조건

- [ ] 소스 유형 선택에 따라 목록이 바뀌고 빈 결과와 실패 후 재시도가 동작한다.
- [ ] 실제 백엔드와 프론트엔드를 연결한 브라우저 검증 및 프론트엔드 검사·빌드가 통과한다.
- [ ] 유형 조건을 생략하면 모든 유형에서 현재 페이지를 반환한다.
- [ ] 유형을 지정하면 해당 유형만 반환하고 결과가 없으면 빈 `items`를 반환한다.
- [ ] 알 수 없는 유형을 요청하면 공통 입력 오류를 반환한다.
- [ ] 기능 구현 후 성공·실패 조건의 단위 테스트와 통합 테스트를 작성·실행하고 관련 테스트를 통과시킨다.
- [ ] 각 변경 요청의 생성·수정 파일 수가 5개 이하이며 컴파일과 해당 단계 검증이 통과한다.
