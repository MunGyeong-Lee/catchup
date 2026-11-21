# 신규 소스 유형의 수정 지원

## 목차

- [1.0 이슈 설명](#section-1-0)
- [2.0 구현 범위](#section-2-0)
- [3.0 구현 내역 및 PR 계획](#section-3-0)
- [4.0 완료 조건](#section-4-0)

**대상 요청** `PUT /api/sources/{sourceId}`

**선행 이슈** [지원회사·문체·기타 텍스트 등록 지원](01-source-create.md), [신규 소스 유형의 상세 조회 지원](03-source-get.md)

<a name="section-1-0"></a>

## 1.0 이슈 설명

지원회사 소스, 문체 및 어투, 기타 텍스트를 최신 상태로 유지할 수 있게 한다.

<a name="section-2-0"></a>

## 2.0 구현 범위

- JSON과 multipart 수정은 최상위 필수 `name`을 보내며, 수정 화면은 상세 응답의 이름을 초기값으로 사용한다. [소스 이름 계약](../shared/source-name.md)

- 기존 유형 변경과 첨부 추가·제거 흐름을 재사용한다.
- 소스 식별자와 최초 등록 시각을 유지하고 실패하면 기존 값과 원본을 보존한다.

<a name="section-3-0"></a>

## 3.0 구현 내역 및 PR 계획

각 변경 요청은 생성·수정 파일을 테스트·문서·스키마·설정을 포함해 최대 5개로 제한한다. 아래 경로는 저장소 루트 기준의 예상 위치다. 신규 클래스 이름은 설계 시 확정한다. 번호 순으로 진행하며 이전 변경 요청이 병합된 뒤 다음 변경 요청을 시작한다. 실제 변경에서 추가 파일이 필요하면 변경 요청을 더 나눈다.

- [ ] PR 1. 수정 입력 검증과 저장 연결
  - 예상 생성·수정 파일 3개
  - `back/src/main/java/com/moon/catchup/service/source/SourceServiceImpl.java`
  - `back/src/main/java/com/moon/catchup/service/source/SourceManagementServiceImpl.java`
  - `back/src/test/java/com/moon/catchup/service/source/SourceExtendedUpdateTest.java`
  - 신규 유형으로 변경, 잘못된 입력, 저장 실패 롤백을 검증한다.

- [ ] PR 2. 첨부 변경 회귀와 수정 계약
  - 예상 생성·수정 파일 3개
  - `back/src/test/java/com/moon/catchup/controller/source/SourceExtendedUpdateIntegrationTest.java`
  - `back/src/main/java/com/moon/catchup/controller/source/SourceDocs.java`
  - `specs/issues/contracts/source-update.md`
  - 지원회사 소스, 문체 및 어투, 기타 텍스트의 첨부 교체, 기존 첨부 유지, 원본 정리 실패에도 수정 확정 상태 유지 동작을 검증한다.
  - 이 이슈의 PR 1 병합 후 진행한다.

- [ ] PR 3. 프론트엔드 작업 및 화면 연결 — 신규 소스 수정 화면
  - 예상 생성·수정 파일 4개
  - `front/src/SourceForm.jsx`
  - `front/src/App.jsx`
  - `front/src/SourceForm.css`
  - `front/tests/04-source-update.spec.js`
  - 기존 저장값을 불러와 신규 유형 입력과 첨부 추가·제거를 저장한다. 실패하면 편집값과 파일 선택을 유지하고 성공하면 재조회한다.
  - 이 이슈의 PR 2 및 화면에서 재사용할 선행 이슈의 백엔드 계약 병합 후 진행한다. 프론트엔드 공통 컴포넌트가 이미 있으면 재사용한다.
  - 구현 후 단위 및 브라우저 통합 검증으로 정상·실패 상황을 확인하고 `npm run lint`, `npm run build`를 실행한다. 브라우저 검증 도구 설정이 추가로 필요하면 선행 준비 변경 요청을 분리해 마지막 프론트엔드 변경 요청의 파일 수를 5개 이하로 유지한다.

<a name="section-4-0"></a>

## 4.0 완료 조건

- [ ] 신규 소스 수정과 첨부 변경을 화면에서 저장하고 실패 시 편집값을 유지한다.
- [ ] 실제 백엔드와 프론트엔드를 연결한 브라우저 검증 및 프론트엔드 검사·빌드가 통과한다.
- [ ] 지원회사, 문체 및 어투, 기타 텍스트 유형을 수정하고 다시 조회할 수 있다.
- [ ] 저장 실패 시 기존 소스와 첨부가 유지된다.
- [ ] 첨부 원본 정리는 기존 커밋 후 비동기 삭제 규칙을 따른다.
- [ ] 기능 구현 후 성공·실패 조건의 단위 테스트와 통합 테스트를 작성·실행하고 관련 테스트를 통과시킨다.
- [ ] 각 변경 요청의 생성·수정 파일 수가 5개 이하이며 컴파일과 해당 단계 검증이 통과한다.
