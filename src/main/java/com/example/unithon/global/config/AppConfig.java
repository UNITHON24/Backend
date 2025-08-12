package com.example.unithon.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync // 비동기 기능 활성화
public class AppConfig {

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // 기본 스레드 수
        executor.setMaxPoolSize(10);     // 최대 스레드 수
        executor.setQueueCapacity(25);   // 대기 큐 크기
        executor.setThreadNamePrefix("tts-executor-"); // 스레드 이름 접두사
        executor.initialize();
        return executor;
    }
}
