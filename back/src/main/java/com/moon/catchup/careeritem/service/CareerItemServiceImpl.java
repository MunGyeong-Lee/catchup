package com.moon.catchup.careeritem.service;

import java.net.URI;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.moon.catchup.careeritem.domain.CareerItemDetails;
import com.moon.catchup.careeritem.domain.CareerItemSummary;
import com.moon.catchup.careeritem.domain.CareerItemType;
import com.moon.catchup.careeritem.domain.CareerPeriod;
import com.moon.catchup.careeritem.repository.CareerItemRepository;
import com.moon.catchup.common.http.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 자료 등록 규칙과 자료 목록 조회를 담당한다. */
@Service
@RequiredArgsConstructor
public class CareerItemServiceImpl implements CareerItemService {

    private final CareerItemRepository repository;

    /** 자료 입력을 검증하고 등록한 자료 식별자를 반환한다. */
    @Override
    @Transactional
    public long register(String materialType, Map<String, Object> details) {
        return insert(validate(materialType, details));
    }

    /** 자료 입력을 검증하고 상세 입력 값 객체를 생성한다. */
    CareerItemDetails validate(String materialType, Map<String, Object> details) {
        CareerItemType type = CareerItemType.fromRequest(materialType);
        if (type == null) {
            throw ApiException.validation(Map.of("materialType", "자료 유형을 선택해 주세요."));
        }
        if (details == null) {
            throw ApiException.validation(Map.of("details", "자료 내용을 입력해 주세요."));
        }

        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Object> normalizedDetails = new HashMap<>();
        details.forEach((field, value) -> {
            if (!type.acceptsField(field)) {
                errors.put("details", "선택한 자료 유형에 해당하는 항목만 입력해 주세요.");
            } else if (field.equals("ongoing") && value instanceof Boolean) {
                normalizedDetails.put(field, value);
            } else if (!field.equals("ongoing") && value instanceof String text) {
                if (!text.isBlank()) {
                    normalizedDetails.put(field, text.strip());
                }
            } else if (value != null) {
                errors.put(field, "입력 형식을 확인해 주세요.");
            }
        });
        for (String field : type.getRequiredFields()) {
            if (!normalizedDetails.containsKey(field)) {
                errors.putIfAbsent(field, "필수 입력 항목입니다.");
            }
        }

        validateDetails(type, normalizedDetails, errors);
        if (!errors.isEmpty()) throw ApiException.validation(errors);
        return CareerItemDetails.fromValidated(type, normalizedDetails);
    }

    /** 검증된 입력을 저장하고 데이터베이스가 발급한 식별자를 반환한다. */
    long insert(CareerItemDetails details) {
        return this.repository.insert(details).getId();
    }

    /** 저장소에서 목록 표시용 자료 요약을 조회한다. */
    @Override
    @Transactional(readOnly = true)
    public List<CareerItemSummary> list() {
        return this.repository.list();
    }

    /** 자료 유형별 기간, 진행 상태, 링크 규칙을 검증한다. */
    private static void validateDetails(CareerItemType materialType, Map<String, Object> details, Map<String, String> errors) {
        if (materialType == CareerItemType.CAREER_HISTORY) {
            String historyType = (String) details.get("historyType");
            if (historyType != null && !Set.of("WORK", "STUDY", "TRAINING", "OTHER").contains(historyType)) {
                errors.put("historyType", "이력 구분을 확인해 주세요.");
            }
        }
        if (materialType == CareerItemType.CERTIFICATION) {
            validatePeriod(details, errors, "issuedOn", "expiresOn", "\\d{4}-\\d{2}-\\d{2}", LocalDate::parse);
            validateLink(details, errors, "verificationLink");
            return;
        }

        validatePeriod(details, errors, "startMonth", "endMonth", "\\d{4}-\\d{2}", YearMonth::parse);
        if (Boolean.TRUE.equals(details.get("ongoing")) && details.containsKey("endMonth")) {
            errors.put("endMonth", "진행 중인 이력에는 종료 연월을 입력할 수 없습니다.");
        }
        if (materialType == CareerItemType.EXPERIENCE) {
            validateLink(details, errors, "relatedLink");
        }
    }

    /** 입력 날짜를 파싱하고 기간 순서 오류를 종료 항목에 기록한다. */
    private static <T extends Comparable<? super T>> void validatePeriod(
            Map<String, Object> details, Map<String, String> errors, String startField, String endField,
            String pattern, java.util.function.Function<String, T> parse) {
        T start = parseDate(details, errors, startField, pattern, parse);
        T end = parseDate(details, errors, endField, pattern, parse);
        try {
            new CareerPeriod<>(start, end);
        } catch (IllegalArgumentException exception) {
            errors.put(endField, "시작 연월 또는 취득일과 같거나 이후여야 합니다.");
        }
    }

    /** 입력 문자열을 날짜로 변환하고 유효하지 않은 날짜에는 오류를 기록한다. */
    private static <T> T parseDate(Map<String, Object> details, Map<String, String> errors, String field,
                                   String pattern, java.util.function.Function<String, T> parse) {
        String value = (String) details.get(field);
        if (value == null) {
            return null;
        }
        try {
            if (value.matches(pattern) && !value.startsWith("0000")) {
                return parse.apply(value);
            }
        } catch (DateTimeException exception) {
            // Report invalid calendar dates alongside invalid input formats.
        }
        errors.put(field, "날짜 형식을 확인해 주세요.");
        return null;
    }

    /** 링크가 호스트가 있는 웹 주소인지 검증한다. */
    private static void validateLink(Map<String, Object> details, Map<String, String> errors, String field) {
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
        errors.put(field, "http 또는 https 웹 주소를 입력해 주세요.");
    }
}
