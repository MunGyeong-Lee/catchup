package com.moon.catchup.service.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.moon.catchup.common.http.ApiException;
import com.moon.catchup.common.http.ApiExceptionHandler;
import com.moon.catchup.common.http.ErrorCode;
import com.moon.catchup.controller.source.SourceController;
import com.moon.catchup.domain.source.SourceDetailsVo;
import com.moon.catchup.domain.source.SourceType;
import com.moon.catchup.repository.source.SourceAttachmentRepository;
import com.moon.catchup.repository.source.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 상세 입력의 검증 우선순위와 HTTP 오류 응답을 검증한다. */
public class SourceValidationTest {

    private final SourceRepository sourceRepository = mock(SourceRepository.class);
    private final SourceServiceImpl sourceService = new SourceServiceImpl(
            this.sourceRepository, mock(SourceAttachmentRepository.class), mock(ApplicationEventPublisher.class));

    /** 여러 검증 규칙을 위반한 입력에서 우선순위가 가장 높은 항목만 반환한다. */
    @ParameterizedTest
    @MethodSource("invalidDetails")
    public void reportsOnlyHighestPriorityFailure(
            String sourceType, Map<String, Object> details, String field, String message) {
        assertThatThrownBy(() -> this.sourceService.validate(sourceType, "소스 이름", details))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getFieldErrors()).isEqualTo(Map.of(field, message));
                });
    }

    /** 검증 단계와 유형별 규칙이 충돌하는 입력을 제공한다. */
    private static Stream<Arguments> invalidDetails() {
        return Stream.of(
                Arguments.of("UNKNOWN", null, "sourceType", "소스 유형을 선택해 주세요."),
                Arguments.of("EXPERIENCE", null, "details", "소스 내용을 입력해 주세요."),
                Arguments.of("EXPERIENCE", Map.of("unknown", true, "title", 1), "details",
                        "선택한 소스 유형에 해당하는 항목만 입력해 주세요."),
                Arguments.of("EXPERIENCE", Map.of("endMonth", true, "startMonth", 2026), "endMonth",
                        "입력 형식을 확인해 주세요."),
                Arguments.of("EXPERIENCE", Map.of("relatedLink", "invalid"), "title", "필수 입력 항목입니다."),
                Arguments.of("CAREER_HISTORY", Map.of("organization", "회사", "historyType", "INVALID",
                        "startMonth", "2026-13"), "historyType", "이력 구분을 확인해 주세요."),
                Arguments.of("CAREER_HISTORY", Map.of("organization", "회사", "historyType", "WORK",
                        "startMonth", "2026-13"), "startMonth", "날짜 형식을 확인해 주세요."),
                Arguments.of("CERTIFICATION", Map.of("name", "자격증", "issuedOn", "2026-02-30",
                        "expiresOn", "0000-01-01", "verificationLink", "invalid"), "issuedOn", "날짜 형식을 확인해 주세요."),
                Arguments.of("EXPERIENCE", Map.of("title", "경험", "contribution", "기여",
                        "startMonth", "2026-11", "endMonth", "2026-10", "relatedLink", "invalid"),
                        "endMonth", "시작 연월 또는 취득일과 같거나 이후여야 합니다."));
    }

    /** 바인딩한 등록 요청에서 유형, 필수값과 날짜 오류를 순서대로 검증한다. */
    @ParameterizedTest
    @MethodSource("invalidRegistrationRequests")
    public void rejectsInvalidTypedRegistration(String body, String field, String message) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SourceController(
                        this.sourceService, mock(SourceAttachmentRegistrationService.class),
                        mock(SourceManagementService.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();

        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"error":"VALIDATION_FAILED","error_message":"입력한 내용을 확인해 주세요.",
                         "response":null,"field_errors":{"%s":"%s"}}
                        """.formatted(field, message), JsonCompareMode.STRICT));
        verifyNoInteractions(this.sourceRepository);
    }

    /** 동시에 여러 오류가 있는 등록 요청과 첫 오류를 제공한다. */
    private static Stream<Arguments> invalidRegistrationRequests() {
        return Stream.of(
                Arguments.of("""
                        {"sourceType":"UNKNOWN","name":"소스"}
                        """, "sourceType", "소스 유형을 선택해 주세요."),
                Arguments.of("""
                        {"name":"소스"}
                        """, "sourceType", "소스 유형을 선택해 주세요."),
                Arguments.of("""
                        {"sourceType":"CERTIFICATION","name":"소스","issuedOn":"2025-06-13"}
                        """, "certificateName", "필수 입력 항목입니다."),
                Arguments.of("""
                        {"sourceType":"CERTIFICATION","name":"소스","certificateName":"자격증",
                         "issuedOn":"2026-01-02","expiresOn":"2026-01-01"}
                        """, "expiresOn", "시작 연월 또는 취득일과 같거나 이후여야 합니다."),
                Arguments.of("""
                        {"sourceType":"EXPERIENCE","name":"소스","title":"경험","contribution":"기여",
                         "startMonth":"2026-12","endMonth":"2026-01"}
                        """, "endMonth", "시작 연월 또는 취득일과 같거나 이후여야 합니다."));
    }

    /** 정상 문자열의 앞뒤 공백을 제거하고 선택 항목의 빈 문자열을 생략한다. */
    @Test
    public void normalizesValidDetails() {
        SourceDetailsVo details = this.sourceService.validate(
                "EXPERIENCE", " 소스 이름 ", Map.of("title", " 경험 ", "contribution", " 기여 ", "relatedLink", "  "));

        assertThat(details.getType()).isEqualTo(SourceType.EXPERIENCE);
        assertThat(details.getName()).isEqualTo("소스 이름");
        assertThat(details.getFields()).isEqualTo(Map.of("title", "경험", "contribution", "기여"));
    }

    /** 공통 이름과 자격증 이름의 누락을 서로 다른 필드로 보고한다. */
    @Test
    public void distinguishesCommonNameFromCertificationName() {
        assertThatThrownBy(() -> this.sourceService.validate(
                "CERTIFICATION", "   ", Map.of("name", "자격증", "issuedOn", "2026-01-01")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getFieldErrors()).isEqualTo(Map.of("name", "필수 입력 항목입니다.")));
        assertThatThrownBy(() -> this.sourceService.validate(
                "CERTIFICATION", "소스 이름", Map.of("issuedOn", "2026-01-01")))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getFieldErrors()).isEqualTo(Map.of("details.name", "필수 입력 항목입니다.")));
    }

    /** JSON과 multipart 등록 모두 공통 이름이 누락되면 저장 전에 거부한다. */
    @Test
    public void rejectsMissingCommonNameInBothRegistrationFormats() throws Exception {
        SourceAttachmentTransactionService attachmentTransactionService =
                mock(SourceAttachmentTransactionService.class);
        SourceAttachmentRegistrationService attachmentRegistrationService = new SourceAttachmentRegistrationServiceImpl(
                mock(SourceAttachmentRepository.class), attachmentTransactionService,
                this.sourceService, new SourceAttachmentValidator());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SourceController(
                        this.sourceService, attachmentRegistrationService, mock(SourceManagementService.class)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        String body = """
                {"sourceType":"EXPERIENCE","title":"경험","contribution":"기여"}
                """;
        String expected = """
                {
                  "error": "VALIDATION_FAILED",
                  "error_message": "입력한 내용을 확인해 주세요.",
                  "response": null,
                  "field_errors": {"name": "필수 입력 항목입니다."}
                }
                """;
        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(expected, JsonCompareMode.STRICT));
        mockMvc.perform(multipart("/api/sources")
                        .file(new MockMultipartFile("source", "source.json", "application/json",
                                body.getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("files", "source.pdf", "application/pdf",
                                "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8)))
                        .param("requestId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(expected, JsonCompareMode.STRICT));
        verifyNoInteractions(this.sourceRepository, attachmentTransactionService);
    }

    /** JSON 키 순서를 바꿔도 MVC가 같은 단일 필드 오류와 기존 공통 응답을 반환한다. */
    @ParameterizedTest
    @ValueSource(strings = {
            """
            {"sourceType":"EXPERIENCE","name":"소스 이름","title":7,"contribution":false,"startMonth":"2026-01"}
            """,
            """
            {"startMonth":"2026-01","contribution":false,"title":7,"sourceType":"EXPERIENCE","name":"소스 이름"}
            """
    })
    public void returnsSameHttpErrorForReorderedFields(String requestBody) throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SourceController(
                        this.sourceService, mock(SourceAttachmentRegistrationService.class),
                        mock(SourceManagementService.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/api/sources").contentType(MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "error": "VALIDATION_FAILED",
                          "error_message": "입력한 내용을 확인해 주세요.",
                          "response": null,
                          "field_errors": {"contribution": "입력 형식을 확인해 주세요."}
                        }
                        """, JsonCompareMode.STRICT));
        verifyNoInteractions(this.sourceRepository);
    }
}
