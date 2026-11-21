package com.moon.catchup.service.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.Map;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.repository.source.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;

/** 지원회사, 문체 및 어투, 기타의 상세 입력 검증을 확인한다. */
public class SourceTypeValidationTest {

    private final SourceServiceImpl sourceService = new SourceServiceImpl(
            mock(SourceRepository.class), mock(SourceAttachmentRepository.class), mock(ApplicationEventPublisher.class));

    /** 유형별 필수값만으로 검증을 통과하고 공통 이름과 상세 문자열을 정규화한다. */
    @ParameterizedTest
    @CsvSource({
            "TARGET_COMPANY, companyName, 지원 회사",
            "WRITING_STYLE, content, 간결한 문장으로 작성",
            "OTHER_TEXT, content, 참고 자료 본문"
    })
    public void acceptsRequiredString(SourceType sourceType, String field, String value) {
        SourceDetailsVo details = this.sourceService.validate(
                sourceType.name(), " 소스 이름 ", Map.of(field, " " + value + " "));

        assertThat(details.getType()).isEqualTo(sourceType);
        assertThat(details.getName()).isEqualTo("소스 이름");
        assertThat(details.toStoredFields()).isEqualTo(Map.of(field, value));
    }

    /** 지원회사 선택 문자열을 정규화하고 저장용 상세 맵에 보존한다. */
    @Test
    public void preservesOptionalCompanyDetails() {
        SourceDetailsVo details = this.sourceService.validate("TARGET_COMPANY", "채용 공고", Map.of(
                "companyName", "지원 회사", "jobTitle", " 개발자 ", "content", " 공고 메모 ",
                "idealCandidateProfile", " 협업을 중시하는 사람 ", "jobDescription", " 서비스 개발 "));

        assertThat(details.toStoredFields()).isEqualTo(Map.of(
                "companyName", "지원 회사", "jobTitle", "개발자", "content", "공고 메모",
                "idealCandidateProfile", "협업을 중시하는 사람", "jobDescription", "서비스 개발"));
    }

    /** 상세 필수값 누락과 공백을 같은 필드 오류로 반환한다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY, companyName", "WRITING_STYLE, content", "OTHER_TEXT, content"})
    public void rejectsMissingRequiredDetail(SourceType sourceType, String field) {
        assertValidationFailure(sourceType, "소스 이름", Map.of(), field, "필수 입력 항목입니다.");
        assertValidationFailure(sourceType, "소스 이름", Map.of(field, "  "), field, "필수 입력 항목입니다.");
    }

    /** 상세 입력을 제공해도 공통 이름은 필수다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY, companyName", "WRITING_STYLE, content", "OTHER_TEXT, content"})
    public void rejectsMissingName(SourceType sourceType, String field) {
        assertValidationFailure(sourceType, null, Map.of(field, "필수 상세"), "name", "필수 입력 항목입니다.");
    }

    /** 기존 날짜 입력에 사용하는 타입을 신규 문자열 필드에 넣으면 형식 오류를 반환한다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY, companyName", "WRITING_STYLE, content", "OTHER_TEXT, content"})
    public void rejectsDateInStringField(SourceType sourceType, String field) {
        assertValidationFailure(sourceType, "소스 이름", Map.of(field, LocalDate.of(2026, 1, 1)),
                field, "입력 형식을 확인해 주세요.");
    }

    private void assertValidationFailure(
            SourceType sourceType, String name, Map<String, Object> details, String field, String message) {
        assertThatThrownBy(() -> this.sourceService.validate(sourceType.name(), name, details))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getFieldErrors()).isEqualTo(Map.of(field, message));
                });
    }
}
