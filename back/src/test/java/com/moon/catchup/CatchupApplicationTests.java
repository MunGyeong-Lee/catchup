package com.moon.catchup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** 캐쳡 백엔드의 기본 Spring Boot 구성이 시작되는지 검증한다. */
@SpringBootTest
public class CatchupApplicationTests {

	@Autowired
	private JsonMapper jsonMapper;

	/** 애플리케이션 컨텍스트가 초기화되는지 확인한다. */
	@Test
	public void contextLoads() {
	}

	/** Spring Boot가 구성한 JSON 매퍼에 전역 바인딩과 날짜 출력 설정을 적용한다. */
	@Test
	public void configuresJacksonMapper() {
		assertThat(this.jsonMapper.isEnabled(MapperFeature.USE_GETTERS_AS_SETTERS)).isTrue();
		assertThat(this.jsonMapper.isEnabled(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)).isTrue();
		assertThat(this.jsonMapper.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)).isFalse();
		assertThat(this.jsonMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
		assertThat(this.jsonMapper.writeValueAsString(LocalDate.of(2026, 11, 28))).isEqualTo("\"2026-11-28\"");
	}

}
