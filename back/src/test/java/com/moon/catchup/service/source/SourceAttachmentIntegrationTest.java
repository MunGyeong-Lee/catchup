package com.moon.catchup.service.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.moon.catchup.storage.source.SourceAttachmentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** HTTP 요청과 PostgreSQL을 연결해 첨부 등록과 읽기 계약을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SourceAttachmentIntegrationTest {
    private static final String SOURCE_PATH = "/api/sources";
    private static final byte[] PDF = "%PDF-1.7\nTest document\n".getBytes(StandardCharsets.UTF_8);

    @LocalServerPort private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SourceAttachmentCleanup cleanup;
    @MockitoBean private SourceAttachmentStorage storage;
    @MockitoBean private TaskScheduler taskScheduler;
    @MockitoSpyBean private SourceServiceImpl sources;
    @Autowired private SourceAttachmentRegistrationServiceImpl recovery;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newHttpClient();

    /** 원본 저장, 읽기, 삭제를 재현하는 대체 객체 저장소를 준비한다. */
    @BeforeEach
    public void prepareStorage() throws Exception {
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.<InputStream>getArgument(3).readAllBytes());
            return null;
        }).when(storage).put(anyString(), anyString(), anyLong(), any());
        doAnswer(call -> {
            objects.remove(call.getArgument(0));
            return null;
        }).when(storage).delete(anyString());
        when(storage.read(anyString(), nullable(String.class))).thenAnswer(call -> {
            byte[] bytes = objects.get(call.getArgument(0));
            if (bytes == null) throw new IllegalStateException("Object missing");
            String range = call.getArgument(1);
            if (range != null) {
                String[] bounds = range.substring("bytes=".length()).split("-");
                bytes = Arrays.copyOfRange(bytes, Integer.parseInt(bounds[0]), Integer.parseInt(bounds[1]) + 1);
            }
            return new ByteArrayInputStream(bytes);
        });
    }

    /** 기존 등록, 목록 응답, 다시 열기, 바이트 범위, 원본 내려받기를 검증한다. */
    @Test
    public void keepsExistingRegistrationAndSupportsInlineRangeAndOriginalDownload() throws Exception {
        HttpResponse<byte[]> withoutFiles = send(request(SOURCE_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(source("Legacy registration"))));
        assertEquals(201, withoutFiles.statusCode());
        JsonNode originalId = successBody(withoutFiles).get("sourceId");
        assertTrue(originalId.isIntegralNumber());
        assertTrue(originalId.asLong() > 0);
        String otherSourceId = originalId.asText();
        assertEquals("[]", successBody(send(request(SOURCE_PATH + "/" + otherSourceId + "/attachments"))).toString());

        UUID requestId = UUID.randomUUID();
        HttpResponse<byte[]> created = upload(requestId, "Attachments", PDF, PDF);
        assertEquals(201, created.statusCode(), new String(created.body(), StandardCharsets.UTF_8));
        JsonNode addedId = successBody(created).get("sourceId");
        assertTrue(addedId.isIntegralNumber());
        assertEquals(originalId.asLong() + 1, addedId.asLong());
        String sourceId = addedId.asText();
        assertArrayEquals(created.body(), upload(requestId, "Attachments", PDF, PDF).body());
        assertEquals(2, objects.size());

        JsonNode summaries = successBody(send(request(SOURCE_PATH))).path("items");
        JsonNode summary = summaries.valueStream()
                .filter(source -> sourceId.equals(source.get("sourceId").asText())).findFirst().orElseThrow();
        assertEquals("EXPERIENCE", summary.get("sourceType").asText());
        assertEquals("소스 Attachments", summary.path("name").asText());
        assertFalse(summary.has("displayName"));
        assertTrue(summary.path("hasAttachments").isBoolean());
        assertTrue(summary.path("hasAttachments").asBoolean());
        JsonNode summaryWithoutFiles = summaries.valueStream()
                .filter(source -> otherSourceId.equals(source.path("sourceId").asText())).findFirst().orElseThrow();
        assertTrue(summaryWithoutFiles.path("hasAttachments").isBoolean());
        assertFalse(summaryWithoutFiles.path("hasAttachments").asBoolean());
        JsonNode registeredDetails = successBody(send(request(SOURCE_PATH + "/" + sourceId)));
        assertEquals("소스 Attachments", registeredDetails.path("name").asText());
        assertEquals("Attachments", registeredDetails.path("title").asText());
        assertEquals("소스 Legacy registration", successBody(send(request(SOURCE_PATH + "/" + otherSourceId)))
                .path("name").asText());
        assertFalse(summary.has("historyType"));

        String attachments = SOURCE_PATH + "/" + sourceId + "/attachments";
        JsonNode list = successBody(send(request(attachments)));
        assertEquals(2, list.size());
        assertFalse(list.get(0).has("objectKey"));
        String attachmentId = list.get(0).get("attachmentId").asText();
        String file = attachments + "/" + attachmentId;
        HttpResponse<byte[]> inline = send(request(file + "/view"));
        assertEquals(200, inline.statusCode());
        assertEquals("application/pdf", inline.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("nosniff", inline.headers().firstValue("X-Content-Type-Options").orElseThrow());
        assertTrue(inline.headers().firstValue("Content-Disposition").orElseThrow().startsWith("inline"));
        assertArrayEquals(PDF, inline.body());

        HttpResponse<byte[]> partial = send(request(file + "/view").header("Range", "bytes=0-4"));
        assertEquals(206, partial.statusCode());
        assertArrayEquals(Arrays.copyOf(PDF, 5), partial.body());
        assertEquals("bytes 0-4/" + PDF.length, partial.headers().firstValue("Content-Range").orElseThrow());
        HttpResponse<byte[]> invalidRange = send(request(file + "/view").header("Range", "bytes=500-501"));
        assertEquals("bytes */" + PDF.length, invalidRange.headers().firstValue("Content-Range").orElseThrow());
        assertError(invalidRange, 416, "INVALID_RANGE");

        HttpResponse<byte[]> download = send(request(file + "/download"));
        assertArrayEquals(PDF, download.body());
        assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow()
                .startsWith("attachment;"));
        assertTrue(download.headers().firstValue("Content-Disposition").orElseThrow()
                .contains("filename*=UTF-8''%ED%95%9C%EA%B8%80.pdf"));
        String wrongParent = SOURCE_PATH + "/" + otherSourceId + "/attachments/" + attachmentId;
        assertError(send(request(wrongParent + "/view")), 404, "ATTACHMENT_NOT_FOUND");
        assertError(send(request(wrongParent + "/download")), 404, "ATTACHMENT_NOT_FOUND");
        objects.clear();
        assertError(send(request(file + "/view")), 503, "ATTACHMENT_STORAGE_UNAVAILABLE");
    }

    /** 잘못된 파일과 일부 업로드 실패 시 소스가 공개되지 않는지 확인한다. */
    @Test
    public void invalidFileAndPartialUploadDoNotPublishSource() throws Exception {
        UUID invalid = UUID.randomUUID();
        HttpResponse<byte[]> rejected = upload(invalid, "Invalid attachment", PDF, "<html>".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, rejected.statusCode());
        JsonNode error = mapper.readTree(rejected.body());
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertFalse(error.path("error_message").asText().isBlank());
        assertTrue(error.path("response").isNull());
        assertTrue(error.path("field_errors").has("attachments"));
        assertTrue(objects.isEmpty());

        UUID failed = UUID.randomUUID();
        doAnswer(call -> {
            if (!objects.isEmpty()) throw new IOException("Second object failed");
            objects.put(call.getArgument(0), call.<InputStream>getArgument(3).readAllBytes());
            return null;
        }).when(storage).put(anyString(), anyString(), anyLong(), any());
        assertEquals(503, upload(failed, failed.toString(), PDF, PDF).statusCode());
        assertTrue(objects.isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM sources WHERE details ->> 'title' = ?", Integer.class, failed.toString()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM upload_attempts WHERE request_id = ?", Integer.class, failed));
    }

    /** 즉시 정리가 실패하면 나중에 처리할 객체 키가 남는지 확인한다. */
    @Test
    public void failedCleanupLeavesKeysForRecovery() throws Exception {
        UUID requestId = UUID.randomUUID();
        doAnswer(call -> { throw new IOException("Upload failed"); })
                .when(storage).put(anyString(), anyString(), anyLong(), any());
        doAnswer(call -> { throw new IllegalStateException("Cleanup failed"); }).when(storage).delete(anyString());
        assertEquals(503, upload(requestId, requestId.toString(), PDF).statusCode());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM upload_attempts WHERE request_id = ?", Integer.class, requestId));
        jdbc.update("UPDATE upload_attempts SET created_at = now() - interval '10 minutes' WHERE request_id = ?",
                requestId);
        doAnswer(call -> null).when(storage).delete(anyString());
        // The recovery pass uses its own transaction and checks the durable attempt again under a row lock.
        recovery.recoverAbandonedAttempts();
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM upload_attempts WHERE request_id = ?", Integer.class, requestId));
    }

    /** 소스 저장 실패가 데이터베이스에 반영되지 않고 전송한 원본이 정리되는지 확인한다. */
    @Test
    public void databaseFailureRollsBackSourceAndRemovesUploadedObject() throws Exception {
        UUID requestId = UUID.randomUUID();
        doAnswer(call -> {
            call.callRealMethod();
            throw new DataAccessResourceFailureException("Injected failure after inserting source");
        }).when(sources).insert(argThat(details -> requestId.toString().equals(details.getFields().get("title"))));

        assertEquals(503, upload(requestId, requestId.toString(), PDF).statusCode());
        assertTrue(objects.isEmpty());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM sources WHERE details ->> 'title' = ?", Integer.class, requestId.toString()));
    }

    /** 같은 요청을 동시에 등록해도 동일한 소스 식별자를 돌려주는지 확인한다. */
    @Test
    public void concurrentSubmissionsWithSameIdentifierReturnOneSource() throws Exception {
        UUID requestId = UUID.randomUUID();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<byte[]>> first = executor.submit(() -> upload(requestId, requestId.toString(), PDF));
            Future<HttpResponse<byte[]>> second = executor.submit(() -> upload(requestId, requestId.toString(), PDF));
            HttpResponse<byte[]> firstResponse = first.get(15, TimeUnit.SECONDS);
            HttpResponse<byte[]> secondResponse = second.get(15, TimeUnit.SECONDS);
            assertEquals(201, firstResponse.statusCode());
            assertEquals(201, secondResponse.statusCode());
            assertArrayEquals(firstResponse.body(), secondResponse.body());
        }
        assertEquals(1, objects.size());
    }

    /** 여러 개의 작은 파일을 한 요청에서 등록할 수 있는지 확인한다. */
    @Test
    public void multipartParserDoesNotImposeItsDefaultFileCountLimit() throws Exception {
        byte[][] copies = new byte[100][];
        Arrays.fill(copies, PDF);
        HttpResponse<byte[]> result = upload(UUID.randomUUID(), "Many small attachments", copies);
        assertEquals(201, result.statusCode(), new String(result.body(), StandardCharsets.UTF_8));
        assertEquals(100, objects.size());
    }

    /** 소스 입력 오류를 첨부 여부와 관계없이 같은 필드 오류로 반환하는지 확인한다. */
    @Test
    public void missingTitleUsesTheSameFieldErrorWithOrWithoutAttachments() throws Exception {
        HttpResponse<byte[]> plain = send(request(SOURCE_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(source(" "))));
        HttpResponse<byte[]> multipart = upload(UUID.randomUUID(), " ", PDF);
        assertEquals(400, plain.statusCode());
        assertEquals(400, multipart.statusCode());
        JsonNode error = mapper.readTree(plain.body());
        assertEquals(error, mapper.readTree(multipart.body()));
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertTrue(error.path("field_errors").has("title"));
        assertTrue(objects.isEmpty());
    }

    /** 파싱 실패와 업로드 제한 초과가 항목 오류 없이 공통 응답을 반환하는지 확인한다. */
    @Test
    public void malformedRequestAndOversizeUploadUseCommonErrors() throws Exception {
        assertError(send(request(SOURCE_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{"))), 400, "INVALID_REQUEST");
        assertError(upload(UUID.randomUUID(), "Oversize attachment", new byte[20 * 1024 * 1024 + 1]),
                413, "PAYLOAD_TOO_LARGE");
        assertError(upload("invalid-request-id", "Invalid id", PDF), 400, "INVALID_REQUEST");
    }

    /** 정상 응답에 공통 필드가 포함되고 개별 입력 오류 필드가 생략되는지 확인한다. */
    private JsonNode successBody(HttpResponse<byte[]> response) throws Exception {
        JsonNode body = mapper.readTree(response.body());
        assertTrue(body.path("error").isNull());
        assertTrue(body.path("error_message").isNull());
        assertFalse(body.has("field_errors"));
        return body.get("response");
    }

    /** 입력 검증 외 오류의 HTTP 상태와 공통 응답 필드를 확인한다. */
    private void assertError(HttpResponse<byte[]> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        assertEquals(code, body.path("error").asText());
        assertFalse(body.path("error_message").asText().isBlank());
        assertTrue(body.path("response").isNull());
        assertFalse(body.has("field_errors"));
    }

    /** 문서 인터페이스의 설명이 실제 OpenAPI 문서에 포함되는지 확인한다. */
    @Test
    public void publishesControllerDocumentationFromDocsInterfaces() throws Exception {
        HttpResponse<byte[]> response = send(request("/v3/api-docs"));
        assertEquals(200, response.statusCode());
        JsonNode api = this.mapper.readTree(response.body());
        JsonNode paths = api.path("paths");
        assertEquals("소스 목록 조회", paths.path(SOURCE_PATH).path("get").path("summary").asText());
        assertEquals("첨부 원본 내려받기", paths.path(SOURCE_PATH + "/{sourceId}/attachments/{attachmentId}/download")
                .path("get").path("summary").asText());
        JsonNode registration = paths.path(SOURCE_PATH).path("post").path("responses");
        assertTrue(registration.has("201"));
        assertTrue(registration.has("413"));
        JsonNode schemas = api.path("components").path("schemas");
        JsonNode listSchema = schemas.path("SourceListResponse");
        assertEquals(List.of("#/components/schemas/CertificationListResponse",
                        "#/components/schemas/ExperienceListResponse", "#/components/schemas/HistoryListResponse",
                        "#/components/schemas/TextSourceListResponse"),
                listSchema.path("oneOf").valueStream().map(schema -> schema.path("$ref").asText()).sorted().toList());
        for (String schemaName : List.of("CertificationListResponse", "ExperienceListResponse", "HistoryListResponse", "TextSourceListResponse")) {
            JsonNode properties = schemas.path(schemaName).path("properties");
            assertEquals("string", properties.path("name").path("type").asText());
            assertFalse(properties.has("displayName"));
            assertEquals("boolean", properties.path("hasAttachments").path("type").asText());
        }
        JsonNode registerSchema = schemas.path("SourceRegisterRequest");
        assertEquals("sourceType", registerSchema.path("discriminator").path("propertyName").asText());
        Map<String, String> registrationSchemas = Map.of(
                "CAREER_HISTORY", "CareerHistoryRegisterRequest",
                "EXPERIENCE", "ExperienceRegisterRequest",
                "CERTIFICATION", "CertificationRegisterRequest",
                "TARGET_COMPANY", "TargetCompanyRegisterRequest",
                "WRITING_STYLE", "WritingStyleRegisterRequest",
                "OTHER_TEXT", "OtherTextRegisterRequest");
        String componentPath = "#/components/schemas/";
        assertEquals(registrationSchemas.values().stream().map(name -> componentPath + name).sorted().toList(),
                registerSchema.path("oneOf").valueStream().map(schema -> schema.path("$ref").asText()).sorted().toList());
        assertEquals(registrationSchemas.keySet().stream().sorted().toList(),
                registerSchema.path("properties").path("sourceType").path("enum")
                        .valueStream().map(JsonNode::asText).sorted().toList());
        registrationSchemas.forEach((type, schema) -> assertEquals(componentPath + schema,
                registerSchema.path("discriminator").path("mapping").path(type).asText()));
        assertRegistrationFields(schemas.path("TargetCompanyRegisterRequest"), "companyName",
                List.of("jobTitle", "content", "idealCandidateProfile", "jobDescription"));
        assertRegistrationFields(schemas.path("WritingStyleRegisterRequest"), "content", List.of());
        assertRegistrationFields(schemas.path("OtherTextRegisterRequest"), "content", List.of());
        assertFalse(registerSchema.path("properties").has("details"));
        JsonNode sourceResponse = schemas.path("SourceResponse");
        assertEquals("sourceType", sourceResponse.path("discriminator").path("propertyName").asText());
        assertEquals(List.of("CareerHistoryResponse", "CertificationResponse", "ExperienceResponse",
                        "OtherTextResponse", "TargetCompanyResponse", "WritingStyleResponse")
                        .stream().map(name -> componentPath + name).toList(),
                sourceResponse.path("oneOf").valueStream().map(schema -> schema.path("$ref").asText()).sorted().toList());
        Map.of("TARGET_COMPANY", "TargetCompanyResponse", "WRITING_STYLE", "WritingStyleResponse",
                "OTHER_TEXT", "OtherTextResponse").forEach((type, name) -> assertEquals(componentPath + name,
                sourceResponse.path("discriminator").path("mapping").path(type).asText()));
        JsonNode attachmentView = paths.path(SOURCE_PATH + "/{sourceId}/attachments/{attachmentId}/view")
                .path("get").path("responses");
        assertTrue(attachmentView.has("206"));
        assertTrue(attachmentView.has("416"));
    }

    /** 유형별 OpenAPI 필드의 문자열 형식과 필수 여부를 확인한다. */
    private static void assertRegistrationFields(JsonNode schema, String requiredField, List<String> optionalFieldList) {
        JsonNode fields = schema.path("allOf").valueStream().filter(part -> part.has("properties"))
                .findFirst().orElse(schema);
        List<String> requiredFieldList = schema.path("required").valueStream().map(JsonNode::asText).sorted().toList();
        assertEquals(List.of(requiredField, "name", "sourceType"), requiredFieldList);
        assertEquals("string", fields.path("properties").path(requiredField).path("type").asText());
        for (String field : optionalFieldList) {
            assertEquals("string", fields.path("properties").path(field).path("type").asText());
        }
    }

    /** 소스 변경 실패가 기존 소스와 첨부 원본을 유지하는지 확인한다. */
    @Test
    public void failedEditKeepsExistingAttachment() throws Exception {
        String path = createdWithAttachment("Before edit");
        JsonNode originalUpdatedAt = successBody(send(request(path))).path("updatedAt");
        String removed = successBody(send(request(path + "/attachments"))).get(0).get("attachmentId").asText();
        String body = """
                {"sourceType":"CERTIFICATION","name":"소스 이름","details":{},"removeAttachmentIds":["%s"]}
                """.formatted(removed);
        HttpResponse<byte[]> invalid = send(request(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
        assertEquals(400, invalid.statusCode());
        JsonNode invalidBody = mapper.readTree(invalid.body());
        assertEquals("VALIDATION_FAILED", invalidBody.path("error").asText());
        assertTrue(invalidBody.path("field_errors").has("details.name"));
        assertEquals("Before edit", successBody(send(request(path))).get("title").asText());
        assertEquals(originalUpdatedAt, successBody(send(request(path))).path("updatedAt"));
        assertEquals(200, send(request(path + "/attachments/" + removed + "/view")).statusCode());
    }

    /** 저장소 오류가 아니라 데이터베이스 삭제 오류일 때 소스와 원본을 모두 유지한다. */
    @Test
    public void failedDatabaseDeleteKeepsAttachment() throws Exception {
        String path = createdWithAttachment("Before delete");
        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        this.jdbc.update("INSERT INTO upload_attempts(request_id, object_keys, source_id) VALUES (?, '', ?)", UUID.randomUUID(), id);
        this.jdbc.execute("CREATE TABLE edit_delete_guard (id bigint REFERENCES sources(source_id))");
        try {
            this.jdbc.update("INSERT INTO edit_delete_guard VALUES (?)", id);
            assertError(send(request(path).DELETE()), 500, "INTERNAL_ERROR");
            assertEquals(1, successBody(send(request(path + "/attachments"))).size());
            assertEquals(1, objects.size());
        } finally {
            this.jdbc.execute("DROP TABLE edit_delete_guard");
        }
    }

    /** 원본 삭제 실패를 일일 대조에서 복구하며 예약된 원본은 유지하는지 확인한다. */
    @Test
    public void reconcilesFailedDeletesAndProtectsPendingUpload() throws Exception {
        String path = createdWithAttachment("Cleanup sample");
        String removed = objects.keySet().iterator().next();
        String pending = UUID.randomUUID().toString();
        this.jdbc.update("INSERT INTO upload_attempts(request_id, object_keys) VALUES (?, ?)", UUID.randomUUID(), pending);
        objects.put(pending, PDF);
        CountDownLatch attempted = new CountDownLatch(1);
        doAnswer(call -> {
            attempted.countDown();
            throw new IllegalStateException("Injected storage failure");
        }).when(storage).delete(removed);
        assertEquals(200, send(request(path).DELETE()).statusCode());
        assertTrue(attempted.await(5, TimeUnit.SECONDS));
        assertEquals(404, send(request(path)).statusCode());
        assertTrue(objects.containsKey(removed));
        doAnswer(call -> {
            objects.remove(removed);
            return null;
        }).when(storage).delete(removed);
        when(storage.listKeys()).thenReturn(List.copyOf(objects.keySet()));
        cleanup.reconcile();
        assertFalse(objects.containsKey(removed));
        assertTrue(objects.containsKey(pending));
    }

    /** 실제 수정 쓰기 오류가 함께 삭제한 첨부 행을 복구하는지 확인한다. */
    @Test
    public void failedUpdateRollsBackAttachmentRemoval() throws Exception {
        String path = createdWithAttachment("Before failed write");
        JsonNode originalUpdatedAt = successBody(send(request(path))).path("updatedAt");
        String removed = successBody(send(request(path + "/attachments"))).get(0).get("attachmentId").asText();
        this.jdbc.execute("""
                ALTER TABLE sources ADD CONSTRAINT reject_edit_title
                CHECK (details->>'title' <> 'Rejected update')
                """);
        try {
            String body = """
                    {"sourceType":"EXPERIENCE","name":"Rejected name","details":{"title":"Rejected update","contribution":"Sample"},
                     "removeAttachmentIds":["%s"]}
                    """.formatted(removed);
            assertError(send(request(path).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))), 500, "INTERNAL_ERROR");
            assertEquals("Before failed write", successBody(send(request(path))).get("title").asText());
            assertEquals(originalUpdatedAt, successBody(send(request(path))).path("updatedAt"));
            assertEquals(200, send(request(path + "/attachments/" + removed + "/download")).statusCode());
            assertEquals(1, objects.size());
        } finally {
            this.jdbc.execute("ALTER TABLE sources DROP CONSTRAINT reject_edit_title");
        }
    }

    /** 소스 삭제가 완료된 등록 요청을 재전송해도 소스가 다시 생성되지 않는지 확인한다. */
    @Test
    public void replayAfterDeletionDoesNotRecreateSource() throws Exception {
        UUID requestId = UUID.randomUUID();
        HttpResponse<byte[]> created = upload(requestId, "Deleted upload", PDF);
        assertEquals(201, created.statusCode());
        String path = SOURCE_PATH + "/" + successBody(created).get("sourceId").asText();
        assertEquals(200, send(request(path).DELETE()).statusCode());
        assertError(upload(requestId, "Deleted upload", PDF), 404, "SOURCE_NOT_FOUND");
        assertError(send(request(path)), 404, "SOURCE_NOT_FOUND");
    }

    /** 파일 추가와 제거를 함께 저장하고 같은 수정 요청을 다시 실행하지 않는다. */
    @Test
    public void multipartEditPreservesIdentityAndReusesCompletedAttempt() throws Exception {
        String path = createdWithAttachment("Before multipart edit");
        long id = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        Instant originalCreatedAt = this.jdbc.queryForObject(
                "SELECT created_at FROM sources WHERE source_id = ?",
                (row, index) -> row.getObject(1, OffsetDateTime.class).toInstant(), id);
        String removed = successBody(send(request(path + "/attachments"))).get(0).get("attachmentId").asText();
        String update = """
                {"sourceType":"CERTIFICATION","name":"  Updated name  ",
                 "details":{"name":"Updated item","issuedOn":"2024-02-29","expiresOn":"2025-01-01"},
                 "removeAttachmentIds":["%s"]}
                """.formatted(removed);
        String requestId = UUID.randomUUID().toString();
        assertEquals(200, multipart(path, "PUT", requestId, update, PDF).statusCode());
        assertEquals(200, multipart(path, "PUT", requestId, update, PDF).statusCode());
        JsonNode sourceResponse = successBody(send(request(path)));
        assertEquals(id, sourceResponse.path("sourceId").asLong());
        assertEquals("CERTIFICATION", sourceResponse.path("sourceType").asText());
        assertEquals("Updated name", sourceResponse.path("name").asText());
        assertEquals("Updated item", sourceResponse.path("certificateName").asText());
        assertEquals("2024-02-29", sourceResponse.path("issuedOn").asText());
        assertEquals("2025-01-01", sourceResponse.path("expiresOn").asText());
        assertEquals(originalCreatedAt, Instant.parse(sourceResponse.path("createdAt").asText()));
        assertTrue(sourceResponse.path("createdAt").asText().endsWith("Z"));
        assertFalse(sourceResponse.has("details"));
        assertEquals(1, sourceResponse.path("attachments").size());
        assertFalse(removed.equals(sourceResponse.path("attachments").get(0).path("attachmentId").asText()));
        assertEquals("2024-02-29", this.jdbc.queryForObject(
                "SELECT details ->> 'issuedOn' FROM sources WHERE source_id = ?", String.class, id));
    }

    /** JSON 수정에서 이름을 저장하고 상세 조회에 그대로 반환하는지 확인한다. */
    @Test
    public void updatesCommonNameThroughJson() throws Exception {
        String path = createdWithAttachment("Original name");
        JsonNode original = successBody(send(request(path)));
        assertEquals(original.path("createdAt"), original.path("updatedAt"));
        String body = """
                {"sourceType":"EXPERIENCE","name":"  Updated name  ",
                 "details":{"title":"Experience title","contribution":"Example contribution",
                            "startMonth":"2026-01","endMonth":"2026-12"}}
                """;
        assertEquals(200, send(request(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))).statusCode());
        JsonNode result = successBody(send(request(path)));
        assertEquals("Updated name", result.path("name").asText());
        assertEquals("Experience title", result.path("title").asText());
        assertEquals("2026-01", result.path("startMonth").asText());
        assertEquals("2026-12", result.path("endMonth").asText());
        assertEquals(1, result.path("attachments").size());
        assertEquals(original.path("createdAt"), result.path("createdAt"));
        assertTrue(result.path("updatedAt").asText().endsWith("Z"));
        assertTrue(Instant.parse(result.path("updatedAt").asText())
                .isAfter(Instant.parse(original.path("updatedAt").asText())));

        assertEquals(200, send(request(path).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))).statusCode());
        assertEquals(result.path("updatedAt"), successBody(send(request(path))).path("updatedAt"));
    }

    /** 첨부만 변경해도 수정 시각이 갱신되고 완료 요청 재전송은 시각을 유지한다. */
    @Test
    public void tracksAttachmentChangesAndRollsBackFailedUploads() throws Exception {
        String path = createdWithAttachment("Timestamp sample");
        JsonNode original = successBody(send(request(path)));
        String body = """
                {"sourceType":"EXPERIENCE","name":"소스 Timestamp sample",
                 "details":{"title":"Timestamp sample","contribution":"Example contribution"}}
                """;
        String requestId = UUID.randomUUID().toString();
        assertEquals(200, multipart(path, "PUT", requestId, body, PDF).statusCode());
        JsonNode updated = successBody(send(request(path)));
        assertEquals(2, updated.path("attachments").size());
        assertTrue(Instant.parse(updated.path("updatedAt").asText())
                .isAfter(Instant.parse(original.path("updatedAt").asText())));
        assertEquals(200, multipart(path, "PUT", requestId, body, PDF).statusCode());
        assertEquals(updated.path("updatedAt"), successBody(send(request(path))).path("updatedAt"));

        byte[] rejectedPdf = "%PDF-1.7\nLate attachment failure".getBytes(StandardCharsets.UTF_8);
        this.jdbc.execute("ALTER TABLE attachments ADD CONSTRAINT reject_timestamp_test_file CHECK (size_bytes <> %d)"
                .formatted(rejectedPdf.length));
        try {
            assertError(multipart(path, "PUT", UUID.randomUUID().toString(), body, rejectedPdf), 500, "INTERNAL_ERROR");
            JsonNode rolledBack = successBody(send(request(path)));
            assertEquals(updated.path("updatedAt"), rolledBack.path("updatedAt"));
            assertEquals(updated.path("attachments"), rolledBack.path("attachments"));
        } finally {
            this.jdbc.execute("ALTER TABLE attachments DROP CONSTRAINT reject_timestamp_test_file");
        }
    }

    /** 대조 작업이 원본을 보호하고 복구 작업이 잠긴 만료 예약을 건너뛰는지 확인한다. */
    @Test
    public void dailyScanPreservesActiveUploadAcrossCommit() throws Exception {
        UUID requestId = UUID.randomUUID();
        String objectKey = UUID.randomUUID().toString();
        this.jdbc.update("""
                INSERT INTO upload_attempts (request_id, object_keys, created_at)
                VALUES (?, ?, CURRENT_TIMESTAMP - INTERVAL '10 minutes')
                """, requestId, objectKey);
        CountDownLatch objectWritten = new CountDownLatch(1);
        CountDownLatch finishUpload = new CountDownLatch(1);
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.<InputStream>getArgument(3).readAllBytes());
            objectWritten.countDown();
            assertTrue(finishUpload.await(5, TimeUnit.SECONDS));
            return null;
        }).when(storage).put(anyString(), anyString(), anyLong(), any());
        when(storage.listKeys()).thenAnswer(call -> List.copyOf(objects.keySet()));
        try (ExecutorService clientThread = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<byte[]>> uploading = clientThread.submit(() -> upload(requestId, "Active upload", PDF));
            try {
                assertTrue(objectWritten.await(5, TimeUnit.SECONDS));
                cleanup.reconcile();
                clientThread.submit(this.recovery::recoverAbandonedAttempts).get(2, TimeUnit.SECONDS);
                assertEquals(List.of(objectKey), List.copyOf(this.objects.keySet()));
            } finally {
                finishUpload.countDown();
            }
            assertEquals(201, uploading.get(5, TimeUnit.SECONDS).statusCode());
        }
        cleanup.reconcile();
        assertEquals(1, objects.size());
    }

    /** 저장 시각이 같으면 소스 식별자가 큰 항목을 먼저 반환한다. */
    @Test
    public void listsLatestAndBreaksTiesById() throws Exception {
        List<Long> expected = this.jdbc.queryForList("""
                INSERT INTO sources(source_type, name, details, created_at) VALUES
                    ('EXPERIENCE', 'Older display name', '{"title":"Older sort sample"}', '2020-01-01T00:00:00Z'),
                    ('EXPERIENCE', 'First tied display name', '{"title":"First tied sort sample"}', '2020-01-02T00:00:00Z'),
                    ('EXPERIENCE', 'Second tied display name', '{"title":"Second tied sort sample"}', '2020-01-02T00:00:00Z')
                RETURNING source_id
                """, Long.class).reversed();
        List<Long> actual = successBody(send(request(SOURCE_PATH + "?page_size=100"))).path("items").valueStream()
                .map(source -> source.path("sourceId").asLong()).filter(expected::contains).toList();
        assertEquals(expected, actual);
    }

    /** DB 페이지의 정렬, 표시 정보, 범위 및 전체 건수가 HTTP 응답에 유지되는지 확인한다. */
    @Test
    public void returnsSourcePagesAndRejectsInvalidBounds() throws Exception {
        this.jdbc.update("""
                INSERT INTO sources(source_type, name, details, created_at) VALUES
                    ('CERTIFICATION', 'Page certificate', '{"issuedOn":"2026-01-01"}', '2099-01-01T00:00:00Z'),
                    ('CAREER_HISTORY', 'Page history', '{"historyType":"WORK","startMonth":"2026-02"}',
                        '2099-01-01T00:00:00Z')
                """);
        JsonNode defaultPage = successBody(send(request(SOURCE_PATH)));
        assertEquals(1, defaultPage.path("pageNumber").asInt());
        assertEquals(20, defaultPage.path("pageSize").asInt());
        assertEquals(100, successBody(send(request(SOURCE_PATH + "?page_size=100"))).path("pageSize").asInt());

        JsonNode firstPage = successBody(send(request(SOURCE_PATH + "?page_size=1")));
        JsonNode secondPage = successBody(send(request(SOURCE_PATH + "?page_size=1&page_number=2")));
        JsonNode history = firstPage.path("items").get(0);
        JsonNode certificate = secondPage.path("items").get(0);
        assertEquals(1, firstPage.path("items").size());
        assertEquals(2, secondPage.path("pageNumber").asInt());
        assertEquals("Page history", history.path("name").asText());
        assertFalse(history.has("displayName"));
        assertEquals("WORK", history.path("historyType").asText());
        assertEquals("2026-02", history.path("startMonth").asText());
        assertEquals("2099-01-01T00:00:00Z", history.path("createdAt").asText());
        assertEquals("Page certificate", certificate.path("name").asText());
        assertFalse(certificate.has("displayName"));
        assertEquals("2026-01-01", certificate.path("issuedOn").asText());
        assertEquals(firstPage.path("totalElements").asLong(), firstPage.path("totalPages").asLong());

        JsonNode beyondLast = successBody(send(request(SOURCE_PATH + "?page_number=2147483647")));
        assertEquals(Integer.MAX_VALUE, beyondLast.path("pageNumber").asInt());
        assertEquals(0, beyondLast.path("items").size());
        assertEquals(firstPage.path("totalElements"), beyondLast.path("totalElements"));
        assertInvalidPageParameter("page_number", 0);
        assertInvalidPageParameter("page_size", 0);
        assertInvalidPageParameter("page_size", 101);
    }

    /** 유효하지 않은 페이지 값은 해당 매개변수의 입력 오류를 반환한다. */
    private void assertInvalidPageParameter(String parameter, int value) throws Exception {
        HttpResponse<byte[]> response = send(request(SOURCE_PATH + "?" + parameter + "=" + value));
        assertEquals(400, response.statusCode());
        JsonNode error = this.mapper.readTree(response.body());
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertEquals(1, error.path("field_errors").size());
        assertFalse(error.path("field_errors").path(parameter).asText().isBlank());
    }

    /** 허용한 정렬을 먼저 적용하고 동률인 행의 페이지 순서를 유지하는지 확인한다. */
    @Test
    public void sortsSourcePagesAndRejectsInvalidOrder() throws Exception {
        List<Long> idList = this.jdbc.queryForList("""
                INSERT INTO sources(source_type, name, details, created_at) VALUES
                    ('EXPERIENCE', '000 Sort A', '{}', '2020-01-01T00:00:00Z'),
                    ('EXPERIENCE', '000 Sort A', '{}', '2020-01-02T00:00:00Z'),
                    ('EXPERIENCE', '000 Sort A', '{}', '2020-01-02T00:00:00Z')
                RETURNING source_id
                """, Long.class);
        String query = SOURCE_PATH + "?page_size=2&order_by=name:ASC,createdAt:ASC";
        JsonNode firstPage = successBody(send(request(query))).path("items");
        JsonNode secondPage = successBody(send(request(query + "&page_number=2"))).path("items");
        assertEquals(idList.get(0).longValue(), firstPage.get(0).path("sourceId").asLong());
        assertEquals(idList.get(2).longValue(), firstPage.get(1).path("sourceId").asLong());
        assertEquals(idList.get(1).longValue(), secondPage.get(0).path("sourceId").asLong());

        for (String invalidOrder : List.of("details:ASC", "createdAt", "name:asc", "name:ASC,",
                "name:ASC,name:DESC")) {
            HttpResponse<byte[]> response = send(request(SOURCE_PATH + "?order_by=" + invalidOrder));
            assertEquals(400, response.statusCode());
            JsonNode error = this.mapper.readTree(response.body());
            assertEquals("VALIDATION_FAILED", error.path("error").asText());
            assertEquals(1, error.path("field_errors").size());
            assertTrue(error.path("field_errors").has("order_by"));
        }
    }

    /** 원본이 있는 소스를 만들고 소스 요청 경로를 반환한다. */
    private String createdWithAttachment(String title) throws Exception {
        return SOURCE_PATH + "/" + successBody(upload(UUID.randomUUID(), title, PDF)).get("sourceId").asText();
    }

    /** 소스와 표본 파일을 다중 부분 요청으로 제출한다. */
    private HttpResponse<byte[]> upload(UUID requestId, String title, byte[]... files) throws Exception {
        return upload(requestId.toString(), title, files);
    }

    /** 요청 식별자 문자열을 그대로 전송해 정상 요청과 잘못된 식별자를 검증한다. */
    private HttpResponse<byte[]> upload(String requestId, String title, byte[]... files) throws Exception {
        return multipart(SOURCE_PATH, "POST", requestId, source(title), files);
    }

    /** 등록 또는 수정의 다중 부분 요청을 같은 형식으로 전송한다. */
    private HttpResponse<byte[]> multipart(String path, String method, String requestId, String details,
                                           byte[]... files) throws Exception {
        String boundary = "catchup-test-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"source\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + details + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n"
                + requestId + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (byte[] file : files) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"한글.pdf\"\r\n"
                    + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(file);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(request(path).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
    }

    /** 경험 소스 테스트에 사용할 등록 요청 본문을 만든다. */
    private static String source(String title) {
        return "{\"sourceType\":\"EXPERIENCE\",\"name\":\"소스 " + title + "\",\"title\":\"" + title
                + "\",\"contribution\":\"Example contribution\"}";
    }

    /** 세 유형의 최상위 등록 필드를 저장하고 등록과 같은 이름으로 상세 값을 읽는다. */
    @Test
    public void registersTypedTopLevelFields() throws Exception {
        List<String> requestList = List.of(
                """
                {"organization":"Company","historyType":"WORK","startMonth":"2025-01","ongoing":false,
                 "sourceType":"CAREER_HISTORY","name":"Career display name"}
                """,
                """
                {"sourceType":"EXPERIENCE","name":"Experience display name","title":"Project","contribution":"Work"}
                """,
                """
                {"sourceType":"CERTIFICATION","name":"Certificate display name",
                 "certificateName":"Information processing","issuedOn":"2025-06-13"}
                """);
        List<String> expectedFieldList = List.of("organization", "title", "certificateName");
        List<String> expectedValueList = List.of("Company", "Project", "Information processing");
        for (int index = 0; index < requestList.size(); index++) {
            String body = requestList.get(index);
            HttpResponse<byte[]> registered = index == 2
                    ? multipart(SOURCE_PATH, "POST", UUID.randomUUID().toString(), body, PDF)
                    : send(request(SOURCE_PATH).header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)));
            assertEquals(201, registered.statusCode(), new String(registered.body(), StandardCharsets.UTF_8));
            long sourceId = successBody(registered).path("sourceId").asLong();
            JsonNode detail = successBody(send(request(SOURCE_PATH + "/" + sourceId)));
            assertEquals(expectedValueList.get(index), detail.path(expectedFieldList.get(index)).asText());
            assertEquals(this.mapper.readTree(body).path("name").asText(), detail.path("name").asText());
            assertTrue(detail.path("createdAt").asText().endsWith("Z"));
            Instant.parse(detail.path("createdAt").asText());
            if (index == 0) {
                assertEquals("2025-01", detail.path("startMonth").asText());
                assertEquals("2025-01", this.jdbc.queryForObject(
                        "SELECT details ->> 'startMonth' FROM sources WHERE source_id = ?", String.class, sourceId));
            }
            if (index == 2) {
                assertEquals("2025-06-13", detail.path("issuedOn").asText());
                assertEquals(1, detail.path("attachments").size());
                assertEquals("Information processing", this.jdbc.queryForObject(
                        "SELECT details ->> 'name' FROM sources WHERE source_id = ?", String.class, sourceId));
            }
        }
    }

    /** 신규 세 유형을 두 요청 형식으로 등록하고 저장값, 첨부 참조와 목록 응답을 확인한다. */
    @Test
    public void registersNewSourceTypesWithAndWithoutAttachments() throws Exception {
        String name = "신규 등록 검증 " + UUID.randomUUID();
        List<String> detailList = List.of(
                """
                {"companyName":"Company","jobTitle":"Engineer","content":"Notes",
                 "idealCandidateProfile":"Collaborative","jobDescription":"Develop services"}
                """,
                """
                {"content":"Use short sentences."}
                """,
                """
                {"content":"Reference text"}
                """);
        List<String> typeList = List.of("TARGET_COMPANY", "WRITING_STYLE", "OTHER_TEXT");
        try {
            for (int index = 0; index < typeList.size(); index++) {
                String sourceType = typeList.get(index);
                ObjectNode expectedDetails = (ObjectNode) this.mapper.readTree(detailList.get(index));
                String body = this.mapper.createObjectNode().setAll(expectedDetails)
                        .put("sourceType", sourceType).put("name", "  " + name + "  ").toString();
                HttpResponse<byte[]> json = send(request(SOURCE_PATH).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)));
                assertStoredRegistration(json, sourceType, name, expectedDetails, 0);
                HttpResponse<byte[]> withAttachment = multipart(SOURCE_PATH, "POST", UUID.randomUUID().toString(),
                        body, PDF);
                assertStoredRegistration(withAttachment, sourceType, name, expectedDetails, 1);
            }
            JsonNode items = successBody(send(request(SOURCE_PATH + "?page_size=100"))).path("items");
            List<JsonNode> registeredSources = items.valueStream()
                    .filter(source -> name.equals(source.path("name").asText())).toList();
            assertEquals(6, registeredSources.size());
            for (String sourceType : typeList) {
                List<JsonNode> sourceList = registeredSources.stream()
                        .filter(source -> sourceType.equals(source.path("sourceType").asText())).toList();
                assertEquals(2, sourceList.size());
                assertEquals(List.of(false, true), sourceList.stream()
                        .map(source -> source.path("hasAttachments").asBoolean()).sorted().toList());
            }
        } finally {
            this.jdbc.update("DELETE FROM upload_attempts WHERE source_id IN (SELECT source_id FROM sources WHERE name = ?)", name);
            this.jdbc.update("DELETE FROM attachments WHERE source_id IN (SELECT source_id FROM sources WHERE name = ?)", name);
            this.jdbc.update("DELETE FROM sources WHERE name = ?", name);
        }
    }

    /** 생성 응답의 식별자로 DB 저장값과 상세 API의 공통·유형별 필드 및 첨부를 확인한다. */
    private void assertStoredRegistration(HttpResponse<byte[]> response, String sourceType, String name,
                                          JsonNode expectedDetails, int attachmentCount) throws Exception {
        assertEquals(201, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        JsonNode sourceId = successBody(response).path("sourceId");
        assertTrue(sourceId.isIntegralNumber());
        assertTrue(sourceId.asLong() > 0);
        assertEquals(this.mapper.createObjectNode().put("sourceType", sourceType).put("name", name)
                        .set("details", expectedDetails),
                this.mapper.readTree(this.jdbc.queryForObject("""
                        SELECT jsonb_build_object('sourceType', source_type, 'name', name, 'details', details)::text
                        FROM sources WHERE source_id = ?
                        """, String.class, sourceId.asLong())));
        assertEquals(attachmentCount, this.jdbc.queryForObject(
                "SELECT count(*) FROM attachments WHERE source_id = ?", Integer.class, sourceId.asLong()));

        HttpResponse<byte[]> detailResponse = send(request(SOURCE_PATH + "/" + sourceId.asText()));
        assertEquals(200, detailResponse.statusCode());
        JsonNode detail = successBody(detailResponse);
        assertEquals(sourceId, detail.path("sourceId"));
        assertEquals(sourceType, detail.path("sourceType").asText());
        assertEquals(name, detail.path("name").asText());
        Instant createdAt = Instant.parse(detail.path("createdAt").asText());
        assertEquals(createdAt, Instant.parse(detail.path("updatedAt").asText()));
        assertFalse(detail.has("details"));
        expectedDetails.properties().forEach(field -> assertEquals(field.getValue(), detail.path(field.getKey())));
        JsonNode attachmentList = detail.path("attachments");
        assertTrue(attachmentList.isArray());
        assertEquals(attachmentCount, attachmentList.size());
        if (attachmentCount > 0) {
            JsonNode attachment = attachmentList.get(0);
            assertTrue(attachment.path("attachmentId").isTextual());
            assertEquals("한글.pdf", attachment.path("name").asText());
            assertEquals("application/pdf", attachment.path("contentType").asText());
            assertEquals(PDF.length, attachment.path("size").asLong());
            assertFalse(attachment.has("objectKey"));
        }
    }

    /** 필수 상세값과 공통 이름의 오류는 첨부 여부와 무관하며 기존 파일 형식을 검증한다. */
    @Test
    public void rejectsInvalidNewSourceRegistrations() throws Exception {
        String name = "실패 등록 검증 " + UUID.randomUUID();
        Map<String, String> requiredFields = Map.of(
                "TARGET_COMPANY", "companyName", "WRITING_STYLE", "content", "OTHER_TEXT", "content");
        for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
            String sourceType = entry.getKey();
            String field = entry.getValue();
            assertNewSourceFieldError("""
                    {"sourceType":"%s","name":"%s"}
                    """.formatted(sourceType, name), field, "필수 입력 항목입니다.");
            assertNewSourceFieldError("""
                    {"sourceType":"%s","%s":"유형별 입력"}
                    """.formatted(sourceType, field), "name", "필수 입력 항목입니다.");
            assertNewSourceFieldError("""
                    {"sourceType":"%s","name":"%s","%s":123}
                    """.formatted(sourceType, name, field), field, "입력 형식을 확인해 주세요.");
        }
        HttpResponse<byte[]> invalidFile = multipart(SOURCE_PATH, "POST", UUID.randomUUID().toString(), """
                {"sourceType":"TARGET_COMPANY","name":"%s","companyName":"Company"}
                """.formatted(name), "<html>".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, invalidFile.statusCode());
        JsonNode error = this.mapper.readTree(invalidFile.body());
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertTrue(error.path("field_errors").has("attachments"));
        assertTrue(this.objects.isEmpty());
        assertEquals(0, this.jdbc.queryForObject("SELECT count(*) FROM sources WHERE name = ?", Integer.class, name));
    }

    /** 같은 입력의 JSON과 multipart 응답이 동일한 단일 필드 오류인지 확인한다. */
    private void assertNewSourceFieldError(String body, String field, String message) throws Exception {
        HttpResponse<byte[]> json = send(request(SOURCE_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        HttpResponse<byte[]> withAttachment = multipart(SOURCE_PATH, "POST", UUID.randomUUID().toString(), body, PDF);
        assertEquals(400, json.statusCode());
        assertEquals(400, withAttachment.statusCode());
        JsonNode error = this.mapper.readTree(json.body());
        assertEquals(error, this.mapper.readTree(withAttachment.body()));
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertEquals(this.mapper.createObjectNode().put(field, message), error.path("field_errors"));
    }

    /** 테스트 서버의 지정 경로에 요청을 구성한다. */
    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    /** HTTP 요청을 보내고 응답 본문을 바이트로 반환한다. */
    private HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
