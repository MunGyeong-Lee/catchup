# 신규 소스 유형의 상세 조회 지원

## 목차

- [1.0 이슈 설명](#section-1-0)
- [2.0 구현 범위](#section-2-0)
- [3.0 구현 내역 및 PR 계획](#section-3-0)
- [4.0 완료 조건](#section-4-0)

**대상 요청** `GET /api/sources/{sourceId}`

**선행 이슈** [지원회사·문체·기타 텍스트 등록 지원](01-source-create.md)

<a name="section-1-0"></a>

## 1.0 이슈 설명

문서 작성에 사용할 소스를 선택하기 전에 저장된 입력과 첨부를 확인할 수 있게 한다.

<a name="section-2-0"></a>

## 2.0 구현 범위

- 상세 응답은 공통 이름 `response.name`과 유형별 필드를 같은 수준에 반환한다. 자격증 이름은 `certificateName`을 사용한다. [소스 이름 계약](../shared/source-name.md)

- 기존 상세 조회에서 지원회사, 문체 및 어투, 기타 텍스트 유형의 입력값과 첨부 목록을 반환한다.
- 저장 키와 자격 증명은 응답에 포함하지 않는다.

<a name="section-3-0"></a>

## 3.0 구현 내역 및 PR 계획

각 변경 요청은 생성·수정 파일을 테스트·문서·스키마·설정을 포함해 최대 5개로 제한한다. 아래 경로는 저장소 루트 기준의 예상 위치다. 신규 클래스 이름은 설계 시 확정한다. 번호 순으로 진행하며 이전 변경 요청이 병합된 뒤 다음 변경 요청을 시작한다. 실제 변경에서 추가 파일이 필요하면 변경 요청을 더 나눈다.

- [ ] PR 1. 신규 유형 상세 매핑 검증
  - 예상 생성·수정 파일 3개
  - `back/src/main/java/com/moon/catchup/repository/source/SourceRepository.java`
  - `back/src/main/java/com/moon/catchup/controller/source/dto/SourceResponse.java`
  - `back/src/test/java/com/moon/catchup/controller/source/SourceExtendedDetailIntegrationTest.java`
  - 신규 유형별 입력과 첨부 목록 복원, 없는 소스 오류를 검증한다. 기존 매핑으로 충족되는 파일은 실제 변경에서 제외한다.

- [ ] PR 2. 상세 조회 계약 갱신
  - 예상 생성·수정 파일 2개
  - `back/src/main/java/com/moon/catchup/controller/source/SourceDocs.java`
  - `specs/issues/contracts/source-detail.md`
  - 신규 유형의 상세 응답 예시와 누락 소스 응답을 기록한다.
  - 이 이슈의 PR 1 병합 후 진행한다.

- [ ] PR 3. 프론트엔드 작업 및 화면 연결 — 소스 상세 확인 화면
  - 예상 생성·수정 파일 4개
  - `front/src/SourceDetail.jsx`
  - `front/src/App.jsx`
  - `front/src/SourceAttachments.jsx`
  - `front/tests/03-source-get.spec.js`
  - 소재 선택 전 상세값과 첨부를 확인하도록 연결한다. 조회 중 안내, 소스 없음, 조회 실패와 다시 시도를 제공한다.
  - 이 이슈의 PR 2 및 화면에서 재사용할 선행 이슈의 백엔드 계약 병합 후 진행한다. 프론트엔드 공통 컴포넌트가 이미 있으면 재사용한다.
  - 구현 후 단위 및 브라우저 통합 검증으로 정상·실패 상황을 확인하고 `npm run lint`, `npm run build`를 실행한다. 브라우저 검증 도구 설정이 추가로 필요하면 선행 준비 변경 요청을 분리해 마지막 프론트엔드 변경 요청의 파일 수를 5개 이하로 유지한다.

<a name="section-4-0"></a>

## 4.0 완료 조건

- [ ] 신규 유형의 저장값과 첨부를 화면에서 확인할 수 있다.
- [ ] 실제 백엔드와 프론트엔드를 연결한 브라우저 검증 및 프론트엔드 검사·빌드가 통과한다.
- [ ] 신규 유형의 저장값과 첨부 정보를 확인할 수 있다.
- [ ] 존재하지 않는 소스는 공통 소스 없음 오류를 반환한다.
- [ ] 기능 구현 후 성공·실패 조건의 단위 테스트와 통합 테스트를 작성·실행하고 관련 테스트를 통과시킨다.
- [ ] 각 변경 요청의 생성·수정 파일 수가 5개 이하이며 컴파일과 해당 단계 검증이 통과한다.
