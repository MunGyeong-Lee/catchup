# 소스 이름

JSON 등록·수정 본문과 multipart의 `source` JSON 파트에 공통 필수 문자열 `name`을 보낸다. 앞뒤 공백을 제거해 `sources.name`에 저장한다. 누락, 공백만 있는 값, 문자열이 아닌 값은 HTTP 400의 `field_errors.name`으로 반환한다. 자격증 이름은 등록에서 최상위 `certificateName`, 수정에서 `details.name`을 사용한다. 오류는 해당 요청의 필드 이름으로 전달한다.

상세 응답은 `response.name`을 반환한다. 수정 화면은 이 값으로 입력을 초기화하고 JSON 및 multipart 수정에서 이름을 보낸다. 목록의 `displayName`은 저장된 공통 이름을 사용한다. 유형별 기존 상세 필드와 응답 식별자는 유지한다.

기존 DB는 백엔드 기동 전 `back/scripts/add-source-name.sql`을 실행한다. 기존 목록에 사용하던 이력의 기관명, 경험 제목, 자격증 이름으로 채운다. 기존 표시 이름이 없는 행에는 소스 ID를 포함한 이름을 사용한다. 다시 실행해도 사용자가 저장한 이름을 덮어쓰지 않는다.

단위 및 HTTP 검증에서 이름의 정규화와 실패 응답을 확인한다. PostgreSQL 검증 스키마에서는 기존 행의 백필, 등록과 수정의 공통 이름 저장, 목록·상세 조회를 확인한다.

## 미결정 사항

없음.

## 수정용 상세 응답

`GET /api/sources/{sourceId}`의 `response`는 공통 `sourceId`, `sourceType`, `name`, `attachments`와 유형별 입력 필드를 같은 수준에서 반환한다. 이력은 소속명·이력 구분·기간 등, 경험은 제목·기여·기간 등, 자격증은 `certificateName`·취득일 등을 반환한다. 자격증의 기존 상세 이름은 `certificateName`으로 읽고, 수정 요청을 보낼 때 기존 `details.name`으로 변환한다. 등록은 유형별 필드를 최상위로 보내고, 수정은 기존 `details` 객체를 보낸다.

물리 컬럼은 `sources.details jsonb`다. `back/scripts/rename-source-details.sql`은 기존 `attributes` 또는 `detail` 컬럼 이름을 바꿔 JSON과 제약을 보존한다. `details`만 있으면 재실행해도 변경이 없으며, 후보 컬럼이 여러 개 있으면 이관을 중단한다.
