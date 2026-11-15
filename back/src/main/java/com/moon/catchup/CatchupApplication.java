package com.moon.catchup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 캐쳡 백엔드를 시작하고 첨부 정리 작업의 스케줄링을 활성화한다. */
@SpringBootApplication
@EnableScheduling
public class CatchupApplication {

	/** Spring Boot 애플리케이션을 실행한다. */
	public static void main(String[] args) {
		SpringApplication.run(CatchupApplication.class, args);
	}

}
