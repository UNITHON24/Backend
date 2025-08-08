package com.example.unithon.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnvironmentConfig {

    @PostConstruct
    public void loadEnvironmentVariables() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            
            // 환경변수가 시스템에 없는 경우에만 .env 파일에서 로드
            dotenv.entries().forEach(entry -> {
                if (System.getenv(entry.getKey()) == null) {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            });
        } catch (Exception e) {
            // .env 파일이 없어도 애플리케이션이 정상 동작하도록 함
            System.out.println("No .env file found or error loading it. Using system environment variables.");
        }
    }
} 