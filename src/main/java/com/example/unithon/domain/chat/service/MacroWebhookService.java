package com.example.unithon.domain.chat.service;

import com.example.unithon.domain.chat.dto.MacroOrderData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class MacroWebhookService {

    private final String macroWebhookUrl;
    private final String macroWebhookToken;
    private final RestTemplate restTemplate;

    public MacroWebhookService(
        @Value("${macro.webhook.url:http://localhost:9999/api/orders}") String macroWebhookUrl,
        @Value("${macro.webhook.token:}") String macroWebhookToken,
        RestTemplateBuilder restTemplateBuilder
    ) {
        this(restTemplateBuilder.build(), macroWebhookUrl, macroWebhookToken);
    }

    MacroWebhookService(
        RestTemplate restTemplate,
        String macroWebhookUrl,
        String macroWebhookToken
    ) {
        this.restTemplate = restTemplate;
        this.macroWebhookUrl = macroWebhookUrl;
        this.macroWebhookToken = macroWebhookToken;
    }

    public void sendOrderToMacro(MacroOrderData orderData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (macroWebhookToken != null && !macroWebhookToken.isBlank()) {
                headers.set("X-Macro-Token", macroWebhookToken);
            }
            
            HttpEntity<MacroOrderData> request = new HttpEntity<>(orderData, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                macroWebhookUrl, 
                request, 
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("매크로팀 주문 전송 성공 [{}]: {}",
                    orderData.getSessionId(), response.getStatusCode());
            } else {
                log.warn("매크로팀 주문 전송 실패 [{}]: {}",
                    orderData.getSessionId(), response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("매크로팀 주문 전송 오류 [{}]: {}",
                orderData.getSessionId(), e.getMessage(), e);
        }
    }
}
