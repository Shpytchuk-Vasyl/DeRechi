package org.shpytchuk.clientapi.controller.payment;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.service.payment.FourthwallOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@RestController
@AllArgsConstructor
@Slf4j
public class FourthwallWebhookController {

    public static final String PATH = "/api/client/webhooks/fourthwall";

    private final JsonMapper jsonMapper;
    private final FourthwallProperties properties;
    private final FourthwallOrderService orderService;

    @PostMapping(PATH)
    public ResponseEntity<Void> receive(
            @RequestBody byte[] body,
            @RequestHeader(value = FourthwallProperties.SIGNATURE_HEADER, required = false) String signature) {
        if (!properties.accepts(body, signature)) {
            log.warn("Rejected a Fourthwall webhook with a missing or wrong signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            log.warn("Unreadable Fourthwall webhook: {}", e.getOriginalMessage());
            return ResponseEntity.badRequest().build();
        }

        Optional<String> type = FourthwallOrderPlaced.type(root);
        if (type.isEmpty()) {
            log.warn("Fourthwall webhook without a type");
            return ResponseEntity.badRequest().build();
        }
        if (!FourthwallOrderPlaced.TYPE.equals(type.get())) {
            log.info("Ignoring Fourthwall webhook {}", type.get());
            return ResponseEntity.ok().build();
        }

        Optional<FourthwallOrderPlaced> order = FourthwallOrderPlaced.parse(root);
        if (order.isEmpty()) {
            log.warn("Incomplete Fourthwall ORDER_PLACED webhook: {}", root.path("id").asString(""));
            return ResponseEntity.badRequest().build();
        }

        log.info("Accepted Fourthwall webhook {}", order.get());
        orderService.receive(order.get());
        return ResponseEntity.ok().build();
    }
}
