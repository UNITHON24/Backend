package com.example.unithon.domain.chat.service;

import com.example.unithon.domain.chat.dto.MacroOrderData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class MacroWebhookService {

    @Value("${macro.webhook.url:http://localhost:9999/api/orders}")
    private String macroWebhookUrl;

    private final RestTemplate restTemplate;

    public MacroWebhookService() {
        this.restTemplate = new RestTemplate();
    }

    public void sendOrderToMacro(MacroOrderData orderData) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
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