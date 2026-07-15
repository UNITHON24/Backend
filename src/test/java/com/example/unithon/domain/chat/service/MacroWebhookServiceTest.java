package com.example.unithon.domain.chat.service;

import com.example.unithon.domain.chat.dto.MacroOrderData;
import com.example.unithon.domain.chat.dto.MacroOrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MacroWebhookServiceTest {

    @Test
    void sendsInstallationTokenToMacroOrderHub() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        String url = "http://localhost:9999/api/orders";
        String token = "installation-token-with-at-least-32-characters";
        MacroWebhookService service = new MacroWebhookService(restTemplate, url, token);
        MacroOrderData order = new MacroOrderData(
            "session-1",
            List.of(new MacroOrderItem(
                "americano", "아메리카노", "ICE", "REGULAR", 1, 4500, 4500
            )),
            4500,
            "2026-07-15T12:00:00Z"
        );

        server.expect(requestTo(url))
            .andExpect(header("X-Macro-Token", token))
            .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        service.sendOrderToMacro(order);

        server.verify();
    }
}
