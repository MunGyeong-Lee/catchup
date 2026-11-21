# 지원회사·문체·기타 텍스트 등록 지원

## 목차

- [1.0 이슈 설명](#section-1-0)
- [2.0 구현 범위](#section-2-0)
- [3.0 구현 내역 및 PR 계획](#section-3-0)
- [4.0 완료 조건](#section-4-0)
- [5.0 미결정 사항](#section-5-0)

**대상 요청** `POST /api/sources`

<a name="section-1-0"></a>

## 1.0 이슈 설명

사용자가 지원회사 소스, 문체 및 어투, 기타 텍스트를 등록해 문서 작성의 참고 소스로 사용할 수 있게 한다.

<a name="section-2-0"></a>

## 2.0 구현 범위

- JSON 본문과 multipart의 `source` 파트는 공통 필수 `name`과 유형별 입력을 최상위로 받는다. 자격증 이름은 `certificateName`이며 기존 `sources.details` JSONB의 `name`으로 매핑한다. [소스 이름 계약](../shared/source-name.md)

- 기존 소스 등록의 일반 본문 요청과 파일 첨부 요청을 확장한다.
- 지원회사, 문체 및 어투, 기타 텍스트 유형을 추가한다.
- 유형별 입력값과 파일 조건을 검증하고 PostgreSQL 제약을 변경한다. 기존 소스와 첨부 파일 등록을 회귀 검증한다.

<a name="section-3-0"></a>

## 3.0 구현 내역 및 PR 계획

각 변경 요청은 생성·수정 파일을 테스트·문서·스키마·설정을 포함해 최대 5개로 제한한다. 아래 경로는 저장소 루트 기준의 예상 위치다. 신규 클래스 이름은 설계 시 확정한다. 번호 순으로 진행하며 이전 변경 요청이 병합된 뒤 다음 변경 요청을 시작한다. 실제 변경에서 추가 파일이 필요하면 변경 요청을 더 나눈다.

- [ ] PR 1. 소스 유형과 입력 검증 확장
  - 예상 생성·수정 파일 3개
  - `back/src/main/java/com/moon/catchup/domain/source/SourceType.java`
  - `back/src/main/java/com/moon/catchup/service/source/SourceServiceImpl.java`
  - `back/src/test/java/com/moon/catchup/service/source/SourceTypeValidationTest.java`
  - 각 신규 유형의 입력 검증을 구현하고 유효한 입력과 필수 항목 누락 테스트를 작성·실행한다. 필수 항목은 아래 미결정 사항을 합의한 뒤 확정한다.

- [ ] PR 2. 기존 데이터베이스 제약 이관
  - 예상 생성·수정 파일 3개
  - `back/src/main/resources/schema.sql`
  - `back/scripts/extend-source-types.sql`
  - `back/src/test/java/com/moon/catchup/repository/source/SourceTypeMigrationTest.java`
  - 기존 행을 보존하고 신규 유형을 저장하도록 제약을 변경한다. 새 설치와 기존 설치, 반복 실행을 검증한다.
  - 이 이슈의 PR 1 병합 후 진행한다.

- [ ] PR 3. 파일 조건과 등록 계약 검증
  - 예상 생성·수정 파일 4개
  - `back/src/main/java/com/moon/catchup/service/source/SourceAttachmentValidator.java`
  - `back/src/main/java/com/moon/catchup/service/source/SourceAttachmentRegistrationServiceImpl.java`
  - `back/src/test/java/com/moon/catchup/service/source/SourceExtendedRegistrationTest.java`
  - `specs/issues/contracts/source-registration.md`
  - 지원회사, 문체 및 어투, 기타 텍스트의 일반 입력·첨부 등록 성공과 실패를 검증하고 기존 파일 조건을 유지한다.
  - 이 이슈의 PR 2 병합 후 진행한다.

- [ ] PR 4. 프론트엔드 작업 및 화면 연결 — 신규 소스 등록 화면
  - 예상 생성·수정 파일 4개
  - `front/src/App.jsx`
  - `front/src/SourceForm.jsx`
  - `front/src/SourceForm.css`
  - `front/tests/01-source-create.spec.js`
  - 지원회사 소스, 문체 및 어투, 기타 텍스트의 유형별 입력과 첨부 선택을 연결한다. 필수 입력 오류, 저장 중 중복 제출 방지, 저장 실패 시 입력 유지와 성공 안내를 제공한다.
  - 이 이슈의 PR 3 및 화면에서 재사용할 선행 이슈의 백엔드 계약 병합 후 진행한다. 프론트엔드 공통 컴포넌트가 이미 있으면 재사용한다.
  - 구현 후 단위 및 브라우저 통합 검증으로 정상·실패 상황을 확인하고 `npm run lint`, `npm run build`를 실행한다. 브라우저 검증 도구 설정이 추가로 필요하면 선행 준비 변경 요청을 분리해 마지막 프론트엔드 변경 요청의 파일 수를 5개 이하로 유지한다.

<a name="section-4-0"></a>

## 4.0 완료 조건

- [ ] 세 신규 소스 유형을 화면에서 등록하고 소스 목록에서 확인할 수 있다.
- [ ] 실제 백엔드와 프론트엔드를 연결한 브라우저 검증 및 프론트엔드 검사·빌드가 통과한다.
- [ ] 지원회사, 문체 및 어투, 기타 텍스트 유형의 소스를 등록하고 식별자를 받을 수 있다.
- [ ] 유효하지 않은 입력과 파일은 항목별 오류를 반환한다.
- [ ] 기존 이력·경험·자격증과 첨부 등록 테스트가 통과한다.
- [ ] 기능 구현 후 성공·실패 조건의 단위 테스트와 통합 테스트를 작성·실행하고 관련 테스트를 통과시킨다.
- [ ] 각 변경 요청의 생성·수정 파일 수가 5개 이하이며 컴파일과 해당 단계 검증이 통과한다.

<a name="section-5-0"></a>

## 5.0 미결정 사항

- 신규 소스 유형별 필수·선택 입력을 정한다. 회사명과 지원 직무 필수 여부도 여기서 확정한다.
