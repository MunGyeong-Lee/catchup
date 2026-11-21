package com.moon.catchup.service.source;

import java.net.URI;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.moon.catchup.domain.source.SourceDateFormat;
import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceSummaryVo;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.domain.source.CareerPeriodVo;
import com.moon.catchup.repository.source.SourceRepository;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.common.http.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 소스 등록 규칙과 소스 목록 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class SourceServiceImpl implements SourceService {

    private static final Map<String, String> ORDER_PROPERTIES =
            Map.of("createdAt", "createdAt", "sourceId", "id", "sourceType", "type", "name", "name");

    private final SourceRepository sourceRepository;
    private final SourceAttachmentRepository sourceAttachmentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 소스를 등록하고 DB가 발급한 식별자를 반환한다.
     *
     * <p>저장 트랜잭션에서 {@link #validate}를 호출하며 입력 검증에 실패하면 저장소를 호출하지 않는다.
     *
     * @param sourceType 요청 소스 유형 코드
     * @param name 요청의 공통 소스 이름
     * @param details 유형별 상세 입력
     * @return 저장된 소스 식별자
     * @throws ApiException 소스 입력이 유효하지 않은 경우
     */
    @Override
    @Transactional
    public long register(String sourceType, Object name, Map<String, Object> details) {
        return insert(validate(sourceType, name, details));
    }

    /**
     * 소스 입력을 검증해 정규화한 상세 값 객체를 반환한다.
     *
     * <p>유형, 공통 이름, 상세 객체, 허용되지 않은 필드, 필드 형식, 필수 값, 유형별 값 규칙의
     * 순서로 확인한다. 형식은 필드 이름 오름차순으로, 필수 값은 {@link SourceType}에 선언된 순서로
     * 검사한다. 문자열의 앞뒤 공백은 제거하고 빈 상세 값은 생략한다.
     * 지원회사는 회사명, 문체 및 어투와 기타 텍스트는 본문 필드를 필수 문자열로 받는다.
     * 지원회사의 직무, 회사·공고 메모, 인재상, 직무 기술서는 선택 문자열이다.
     * 동일한 규칙을 첨부 포함 여부와 관계없이 적용한다.
     * 첫 실패에서 필드 오류 한 건을 던지므로 요청 객체의 키 순서는 결과에 영향을 주지 않는다.
     *
     * @param sourceType 요청 소스 유형 코드
     * @param name 요청의 공통 소스 이름
     * @param details 사용자가 보낸 유형별 필드와 값
     * @return 검증한 소스 상세 값 객체
     * @throws ApiException 처음 위반한 입력 규칙이 있는 경우
     */
    public SourceDetailsVo validate(String sourceType, Object name, Map<String, Object> details) {
        SourceType type = SourceType.fromRequest(sourceType);
        if (type == null) {
            throw ApiException.validation(Map.of("sourceType", "소스 유형을 선택해 주세요."));
        }
        if (name == null || name instanceof String text && text.isBlank()) {
            throw ApiException.validation(Map.of("name", "필수 입력 항목입니다."));
        }
        if (!(name instanceof String sourceName)) {
            throw ApiException.validation(Map.of("name", "입력 형식을 확인해 주세요."));
        }
        if (details == null) {
            throw ApiException.validation(Map.of("details", "소스 내용을 입력해 주세요."));
        }

        if (details.keySet().stream().anyMatch(field -> field == null || !type.acceptsField(field))) {
            throw ApiException.validation(Map.of("details", "선택한 소스 유형에 해당하는 항목만 입력해 주세요."));
        }

        Map<String, Object> normalizedDetails = new HashMap<>();
        for (String field : details.keySet().stream().sorted().toList()) {
            Object value = details.get(field);
            if (field.equals("ongoing") && value instanceof Boolean) {
                normalizedDetails.put(field, value);
            } else if (!field.equals("ongoing") && value instanceof String text) {
                if (!text.isBlank()) {
                    normalizedDetails.put(field, text.strip());
                }
            } else if (isDateField(field)
                    && (value instanceof YearMonth || value instanceof LocalDate || value == SourceDateFormat.Invalid.VALUE)) {
                normalizedDetails.put(field, value);
            } else if (value != null) {
                throw ApiException.validation(Map.of(detailErrorField(field), "입력 형식을 확인해 주세요."));
            }
        }
        for (String field : type.getRequiredFields()) {
            if (!normalizedDetails.containsKey(field)) {
                throw ApiException.validation(Map.of(detailErrorField(field), "필수 입력 항목입니다."));
            }
        }

        validateDetails(type, normalizedDetails);
        return SourceDetailsVo.fromValidated(type, sourceName.strip(), normalizedDetails);
    }

    /**
     * 상세 필드가 등록 바인딩에서 날짜 또는 연월로 변환되는지 확인한다.
     *
     * @param field 상세 입력의 필드 이름
     * @return 날짜 또는 연월 필드이면 true
     */
    private static boolean isDateField(String field) {
        return switch (field) {
            case "startMonth", "endMonth", "issuedOn", "expiresOn" -> true;
            default -> false;
        };
    }

    /**
     * 자격증 이름의 오류는 공통 소스 이름의 오류와 구분한다.
     *
     * @param field 상세 입력의 필드 이름
     * @return HTTP 필드 오류에 사용할 키
     */
    private static String detailErrorField(String field) {
        return field.equals("name") ? "details.name" : field;
    }

    /**
     * 검증된 입력을 저장하고 데이터베이스가 발급한 식별자를 반환한다.
     *
     * @param details 유형별 상세 입력
     * @return 저장된 소스 식별자
     */
    public long insert(SourceDetailsVo details) {
        return this.sourceRepository.insert(details).getId();
    }

    /**
     * 수정·삭제 전 소스를 잠그며 이미 삭제된 소스의 변경을 차단한다.
     *
     * @param id 소스 식별자
     * @throws ApiException 소스가 존재하지 않는 경우
     */
    public void lock(long id) {
        this.sourceRepository.find(id, true).orElseThrow(() -> new ApiException(ErrorCode.SOURCE_NOT_FOUND));
    }

    /**
     * 잠근 소스와 첨부를 수정하고 실제 변경이 있으면 같은 트랜잭션에서 수정 시각을 기록한다.
     *
     * <p>제거한 첨부 원본의 삭제는 커밋 후에 요청한다. 소스 필드와 첨부가 그대로면
     * 수정 시각을 보존한다.
     *
     * @param id 소스 식별자
     * @param details 유형별 상세 입력
     * @param removedIds 제거할 기존 첨부파일 식별자 목록
     * @param addingAttachments 같은 트랜잭션에서 새 첨부를 저장하는지 여부
     * @throws ApiException 삭제할 첨부가 해당 소스에 속하지 않는 경우
     */
    public void modify(long id, SourceDetailsVo details, List<UUID> removedIds, boolean addingAttachments) {
        List<String> removedKeys = new ArrayList<>();
        for (UUID attachmentId : new LinkedHashSet<>(removedIds)) {
            String key = this.sourceAttachmentRepository.remove(id, attachmentId).orElseThrow(() ->
                    ApiException.validation(Map.of("removeAttachmentIds", "이 소스의 첨부 파일만 삭제할 수 있어요.")));
            removedKeys.add(key);
        }
        boolean sourceChanged = this.sourceRepository.update(id, details);
        if (sourceChanged || addingAttachments || !removedKeys.isEmpty()) {
            this.sourceRepository.updateTimestamp(id);
        }
        if (!removedKeys.isEmpty()) this.applicationEventPublisher.publishEvent(new SourceAttachmentCleanup.Removed(removedKeys));
    }

    /**
     * 검증한 페이지의 항목과 전체 건수를 같은 DB 스냅샷에서 읽는다.
     *
     * <p>JPA의 엔티티 속성 정렬을 사용한다. 요청 순서 뒤에 누락된 등록 시각·식별자 내림차순을
     * 적용한다. 전체 범위를 벗어난 페이지는 요청 정보와 전체 건수를 유지하고 빈 항목을 반환한다.
     *
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize 페이지당 항목 수
     * @param orderBy 쉼표로 연결한 선택 정렬 조건
     * @return 현재 페이지의 소스 요약과 전체 건수
     * @throws ApiException 페이지 값이나 정렬 조건이 유효하지 않은 경우
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<SourceSummaryVo> list(int pageNumber, int pageSize, String orderBy) {
        if (pageNumber < 1) {
            throw ApiException.validation(Map.of("page_number", "페이지 번호는 1 이상이어야 합니다."));
        }
        if (pageSize < 1 || pageSize > 100) {
            throw ApiException.validation(Map.of("page_size", "페이지 크기는 1~100이어야 합니다."));
        }
        PageRequest pageRequest = PageRequest.of(pageNumber - 1, pageSize, parseSort(orderBy));
        if (pageRequest.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageRequest, this.sourceRepository.count());
        }
        return this.sourceRepository.findAll(pageRequest).map(SourceSummaryVo::from);
    }

    /**
     * 공개된 정렬 조건을 JPA 속성으로 변환하고 기본 동률 순서를 추가한다.
     *
     * @param orderBy 쉼표로 연결한 property:direction 조건 또는 생략 시 null
     * @return 요청 방향과 안정적인 동률 순서를 포함한 정렬
     * @throws ApiException 조건 형식이나 속성이 유효하지 않거나 속성을 반복한 경우
     */
    private static Sort parseSort(String orderBy) {
        Map<String, Sort.Direction> directions = new LinkedHashMap<>();
        if (orderBy != null) {
            for (String condition : orderBy.split(",", -1)) {
                String[] parts = condition.split(":", -1);
                String property = parts.length == 2 ? ORDER_PROPERTIES.get(parts[0]) : null;
                if (property == null || !Set.of("ASC", "DESC").contains(parts[1])
                        || directions.putIfAbsent(property, Sort.Direction.valueOf(parts[1])) != null) {
                    throw ApiException.validation(Map.of("order_by", "정렬 속성과 방향을 확인해 주세요."));
                }
            }
        }
        directions.putIfAbsent("createdAt", Sort.Direction.DESC);
        directions.putIfAbsent("id", Sort.Direction.DESC);
        return Sort.by(directions.entrySet().stream()
                .map(entry -> new Sort.Order(entry.getValue(), entry.getKey()))
                .toList());
    }

    /**
     * 소스 유형별 기간, 진행 상태, 링크 규칙을 검증한다.
     *
     * <p>이력 구분을 먼저 확인한다. 기간은 시작 형식, 종료 형식, 시작과 종료의 순서를 차례로
     * 확인한다. 이력의 진행 중 여부와 종료 연월의 관계를 검사한 뒤 해당 유형의 링크를 검증한다.
     *
     * @param sourceType 요청 소스 유형 코드
     * @param details 유형별 상세 입력
     * @throws ApiException 유형별 값 규칙을 위반한 경우
     */
    private static void validateDetails(SourceType sourceType, Map<String, Object> details) {
        if (sourceType == SourceType.CAREER_HISTORY) {
            String historyType = (String) details.get("historyType");
            if (historyType != null && !Set.of("WORK", "STUDY", "TRAINING", "OTHER").contains(historyType)) {
                throw ApiException.validation(Map.of("historyType", "이력 구분을 확인해 주세요."));
            }
        }
        if (sourceType == SourceType.CERTIFICATION) {
            validatePeriod(details, "issuedOn", "expiresOn", LocalDate.class, SourceDateFormat::parseDate);
            validateLink(details, "verificationLink");
            return;
        }

        if (sourceType != SourceType.CAREER_HISTORY && sourceType != SourceType.EXPERIENCE) {
            return;
        }

        validatePeriod(details, "startMonth", "endMonth", YearMonth.class, SourceDateFormat::parseMonth);
        if (Boolean.TRUE.equals(details.get("ongoing")) && details.containsKey("endMonth")) {
            throw ApiException.validation(Map.of("endMonth", "진행 중인 이력에는 종료 연월을 입력할 수 없습니다."));
        }
        if (sourceType == SourceType.EXPERIENCE) {
            validateLink(details, "relatedLink");
        }
    }

    /**
     * 시작과 종료를 한 번씩 파싱하고 기간 순서를 확인한다.
     *
     * <p>검증된 날짜 또는 연월을 상세 맵에 두어 도메인에서 다시 파싱하지 않는다.
     *
     * @param <T> 비교 가능한 날짜 또는 연월 값의 타입
     * @param details 유형별 상세 입력
     * @param startField 시작 날짜 또는 연월의 필드 이름
     * @param endField 종료 날짜 또는 연월의 필드 이름
     * @param dateType 바인딩된 날짜 타입
     * @param parse 문자열을 날짜나 연월로 변환하는 함수
     * @throws ApiException 시작이나 종료가 유효하지 않거나 종료가 시작보다 빠른 경우
     */
    private static <T extends Comparable<? super T>> void validatePeriod(
            Map<String, Object> details, String startField, String endField, Class<T> dateType, Function<String, T> parse) {
        T start = parseDate(details, startField, dateType, parse);
        T end = parseDate(details, endField, dateType, parse);
        try {
            CareerPeriodVo.of(start, end);
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation(Map.of(endField, "시작 연월 또는 취득일과 같거나 이후여야 합니다."));
        }
        if (start != null) {
            details.put(startField, start);
        }
        if (end != null) {
            details.put(endField, end);
        }
    }

    /**
     * 입력 문자열을 날짜로 변환하고 유효하지 않은 날짜에 검증 예외를 던진다.
     *
     * @param <T> 비교 가능한 날짜 또는 연월 값의 타입
     * @param details 유형별 상세 입력
     * @param field 상세 입력의 필드 이름
     * @param dateType 바인딩된 날짜 타입
     * @param parse 문자열을 날짜나 연월로 변환하는 함수
     * @return 변환한 날짜 또는 입력이 없으면 null
     * @throws ApiException 형식이나 달력 날짜가 유효하지 않은 경우
     */
    private static <T> T parseDate(Map<String, Object> details, String field, Class<T> dateType, Function<String, T> parse) {
        Object value = details.get(field);
        if (value == null || dateType.isInstance(value)) {
            return dateType.cast(value);
        }
        if (value instanceof String text) {
            try {
                return parse.apply(text);
            } catch (DateTimeParseException exception) {
                throw ApiException.validation(Map.of(field, "날짜 형식을 확인해 주세요."));
            }
        }
        throw ApiException.validation(Map.of(field, "날짜 형식을 확인해 주세요."));
    }

    /**
     * 링크가 호스트가 있는 웹 주소인지 검증한다.
     *
     * @param details 유형별 상세 입력
     * @param field 상세 입력의 필드 이름
     * @throws ApiException 유효한 HTTP 또는 HTTPS 주소가 아닌 경우
     */
    private static void validateLink(Map<String, Object> details, String field) {
        String value = (String) details.get(field);
        if (value == null) {
            return;
        }
        try {
            URI link = URI.create(value);
            if (link.getHost() != null && ("https".equalsIgnoreCase(link.getScheme())
                    || "http".equalsIgnoreCase(link.getScheme()))) {
                return;
            }
        } catch (IllegalArgumentException exception) {
            // Report malformed links as field errors.
        }
        throw ApiException.validation(Map.of(field, "http 또는 https 웹 주소를 입력해 주세요."));
    }
}
