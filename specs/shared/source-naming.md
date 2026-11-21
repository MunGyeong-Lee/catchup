# 소스 관리 명칭과 로컬 DB 이관

소스 관리 기능의 Java 및 JavaScript 이름은 `Source` 또는 `source`를 사용한다. API는 `/api/sources`를 사용하고, 요청과 응답은 `sourceId`, `sourceType`, 첨부 등록 JSON 파트는 `source`를 사용한다.

Java 영속 모델은 `SourceEntity`이고 물리 테이블명은 `sources`다. `career_items`에서는 테이블과 식별자·유형 컬럼의 이름을 바꾸고, 이미 단수형 `source`를 쓰는 DB에서는 테이블 이름을 바꾼다. `sources`가 이미 있으면 현재 이름을 유지한다. `attachments`와 `upload_attempts`의 테이블명은 유지한다. 소스 참조 컬럼은 `source_id`, 수정 예약 대상은 `target_source_id`다. 수정 대상은 삭제 이후에도 예약 기록으로 남을 수 있으므로 기존과 같이 FK를 두지 않는다. 기존 소스와 첨부 ID, FK, 인덱스 및 IDENTITY 시퀀스는 이름 변경으로 보존한다.

## public 이관 절차

1. 백엔드를 중지하고 `localhost:5432/catchup`의 `public` 스키마를 백업한다. `career_items`, `sources`, `source` 중 하나만 존재하는지 확인한다.
2. 저장소 루트에서 아래 SQL 파일을 순서대로 실행한다. 첫 스크립트는 이름과 참조 구조를 이관하고, 이미 소스가 확정된 업로드 예약에 완료 시각이 없으면 생성 시각으로 채운다. 미완료 예약의 완료 시각은 NULL을 유지한다. 두 번째는 상세 JSON 컬럼을 `details`로 통일하고, 세 번째 스크립트는 [공통 소스 이름](source-name.md)을 채운다. 마지막 스크립트는 기존 소스의 수정 시각을 생성 시각으로 초기화한다. 어느 단계든 실패하면 다음 단계 전에 원인을 해소한다.
3. 이관 전후 세 테이블의 행 수, 기존 ID와 첨부 연결, `sources.name`의 NULL 여부와 `sources.details`의 기존 JSON을 비교한다. 백엔드를 기동해 소스 목록과 첨부 조회를 확인한다.

```sh
sed '/^BEGIN;$/d; /^COMMIT;$/d' back/scripts/migrate-source-naming.sql \
  back/scripts/rename-source-details.sql back/scripts/add-source-name.sql back/scripts/add-source-updated-at.sql | \
  PGOPTIONS='-c search_path=public' psql -X -h localhost -p 5432 -U postgres -d catchup \
  -v ON_ERROR_STOP=1 --single-transaction -f -
```

검증 스키마에서 세 시작 상태의 이관 경로, 이관 재실행, 이름 백필, FK와 시퀀스 보존을 확인한다. API와 신규 스키마는 전체 백엔드 테스트로 검증한다.

## 미결정 사항

없음.
