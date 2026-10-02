package org.shpytchuk.clientapi.controller.payment;

import org.junit.jupiter.api.Test;
import org.shpytchuk.clientapi.config.FourthwallProperties;
import org.shpytchuk.clientapi.controller.AbstractGraphQlTests;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;
import org.shpytchuk.clientapi.entity.payment.FourthwallOrder;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.event.ClaimEvent;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.repository.payment.FourthwallOrderRepository;
import org.shpytchuk.clientapi.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FourthwallWebhookControllerTests extends AbstractGraphQlTests {

    private static final String EXCHANGE = "derechi.items";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FourthwallProperties properties;

    @Autowired
    private FourthwallOrderRepository orderRepository;

    @Autowired
    private LostItemRepository lostItemRepository;

    @Autowired
    private FoundItemRepository foundItemRepository;

    @Autowired
    private LostItemClaimRepository lostClaimRepository;

    @Autowired
    private FoundItemClaimRepository foundClaimRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ContactInfoRepository contactInfoRepository;

    @Test
    void paysTheLostClaimWhoseVariantWasBought() throws Exception {
        LostItemClaim claim = lostClaim("prod-1", "var-1");

        send(orderPlaced("ord-1", "COMPLETED", "var-1", false)).andExpect(status().isOk());

        assertThat(lostClaimRepository.findById(claim.getId()).orElseThrow().getPaidAt()).isNotNull();
        assertThat(orderRepository.findByLostItemClaimIdOrderByIdAsc(claim.getId()))
                .singleElement()
                .satisfies(order -> {
                    assertThat(order.getOrderId()).isEqualTo("ord-1");
                    assertThat(order.getFriendlyId()).isEqualTo("DERECHI-1001");
                    assertThat(order.getStatus()).isEqualTo("COMPLETED");
                    assertThat(order.getEmail()).isEqualTo("payer@example.com");
                    assertThat(order.getUsername()).isEqualTo("Jo");
                    assertThat(order.getAmount()).isEqualByComparingTo("1.08");
                    assertThat(order.getCurrency()).isEqualTo("USD");
                    assertThat(order.isTestMode()).isFalse();
                    assertThat(order.getPlacedAt()).isEqualTo(Instant.parse("2026-10-03T10:00:00Z"));
                    assertThat(order.getReceivedAt()).isNotNull();
                    assertThat(order.getFoundItemClaim()).isNull();
                });
        verify(rabbitTemplate).convertAndSend(EXCHANGE, "item.lost.paid", new ClaimEvent(claim.getId()));
    }

    @Test
    void paysTheFoundClaimWhoseProductWasBought() throws Exception {
        FoundItemClaim claim = foundClaim("prod-2", "var-2");

        send(orderPlaced("ord-2", "CONFIRMED", "var-2", false)).andExpect(status().isOk());

        assertThat(foundClaimRepository.findById(claim.getId()).orElseThrow().getPaidAt()).isNotNull();
        assertThat(orderRepository.findByFoundItemClaimIdOrderByIdAsc(claim.getId())).hasSize(1);
        verify(rabbitTemplate).convertAndSend(EXCHANGE, "item.found.paid", new ClaimEvent(claim.getId()));
    }

    @Test
    void rejectsAMissingOrWrongSignatureAndStoresNothing() throws Exception {
        lostClaim("prod-1", "var-1");
        String body = orderPlaced("ord-1", "COMPLETED", "var-1", false);

        mockMvc.perform(post(FourthwallWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(FourthwallWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(FourthwallProperties.SIGNATURE_HEADER, properties.sign("other".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());

        assertThat(orderRepository.count()).isZero();
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void storesAnOrderThatMatchesNoClaimAndAnswersOk() throws Exception {
        lostClaim("prod-1", "var-1");

        send(orderPlaced("ord-9", "COMPLETED", "var-unknown", true)).andExpect(status().isOk());

        assertThat(orderRepository.findByOrderId("ord-9")).hasValueSatisfying(order -> {
            assertThat(order.getLostItemClaim()).isNull();
            assertThat(order.getFoundItemClaim()).isNull();
            assertThat(order.isTestMode()).isTrue();
        });
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void treatsARedeliveredOrderAsAlreadyHandled() throws Exception {
        LostItemClaim claim = lostClaim("prod-1", "var-1");
        String body = orderPlaced("ord-1", "COMPLETED", "var-1", false);

        send(body).andExpect(status().isOk());
        send(body).andExpect(status().isOk());

        assertThat(orderRepository.count()).isEqualTo(1);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(EXCHANGE), eq("item.lost.paid"), any(ClaimEvent.class));
        assertThat(lostClaimRepository.findById(claim.getId()).orElseThrow().getPaidAt()).isNotNull();
    }

    @Test
    void aSecondOrderForAPaidClaimIsStoredButUnlocksNothingAgain() throws Exception {
        LostItemClaim claim = lostClaim("prod-1", "var-1");

        send(orderPlaced("ord-1", "COMPLETED", "var-1", false)).andExpect(status().isOk());
        send(orderPlaced("ord-2", "COMPLETED", "var-1", false)).andExpect(status().isOk());

        assertThat(orderRepository.findByLostItemClaimIdOrderByIdAsc(claim.getId())).hasSize(2);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(EXCHANGE), eq("item.lost.paid"), any(ClaimEvent.class));
    }

    @Test
    void ignoresOtherEventTypesAndRejectsUnreadableBodies() throws Exception {
        send("{\"type\":\"DONATION\",\"data\":{\"id\":\"d-1\"}}").andExpect(status().isOk());
        send("{\"data\":{\"id\":\"d-1\"}}").andExpect(status().isBadRequest());
        send("{\"type\":\"ORDER_PLACED\",\"data\":{\"id\":\"ord-1\"}}").andExpect(status().isBadRequest());
        send("not json").andExpect(status().isBadRequest());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void aCancelledOrderIsStoredWithoutMatching() throws Exception {
        LostItemClaim claim = lostClaim("prod-1", "var-1");

        send(orderPlaced("ord-1", "CANCELLED", "var-1", false)).andExpect(status().isOk());

        assertThat(lostClaimRepository.findById(claim.getId()).orElseThrow().getPaidAt()).isNull();
        assertThat(orderRepository.findByOrderId("ord-1")).hasValueSatisfying(order -> assertThat(order.getLostItemClaim()).isNull());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void deletingTheClaimKeepsTheOrderWithoutALink() throws Exception {
        LostItemClaim claim = lostClaim("prod-1", "var-1");
        send(orderPlaced("ord-1", "COMPLETED", "var-1", false)).andExpect(status().isOk());

        lostClaimRepository.deleteById(claim.getId());

        FourthwallOrder order = orderRepository.findByOrderId("ord-1").orElseThrow();
        assertThat(order.getLostItemClaim()).isNull();
    }

    private ResultActions send(String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post(FourthwallWebhookController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(FourthwallProperties.SIGNATURE_HEADER, properties.sign(bytes))
                .content(bytes));
    }

    private static String orderPlaced(String orderId, String status, String variantId, boolean testMode) {
        return """
                {
                  "testMode": %s,
                  "id": "evt-%s",
                  "webhookId": "wh-1",
                  "shopId": "sh-1",
                  "type": "ORDER_PLACED",
                  "apiVersion": "V1",
                  "createdAt": "2026-10-03T10:00:05Z",
                  "data": {
                    "id": "%s",
                    "friendlyId": "DERECHI-1001",
                    "status": "%s",
                    "email": "payer@example.com",
                    "username": "Jo",
                    "amounts": {"total": {"value": 1.08, "currency": "USD"}},
                    "offers": [{"id": "prod-x", "name": "Author's phone number", "variant": {"id": "%s", "quantity": 1}}],
                    "createdAt": "2026-10-03T10:00:00Z"
                  }
                }
                """.formatted(testMode, orderId, orderId, status, variantId);
    }

    private LostItemClaim lostClaim(String productId, String variantId) {
        LostItem item = lostItemRepository.save(item(new LostItem(), "ChIJrynok"));
        ContactInfo claimant = contactInfoRepository.save(Fixtures.contact("+48501234567", "claimant@example.com"));
        LostItemClaim claim = Fixtures.claim(new LostItemClaim(), item, claimant, Instant.now());
        claim.setPaymentProductId(productId);
        claim.setPaymentVariantId(variantId);
        return lostClaimRepository.save(claim);
    }

    private FoundItemClaim foundClaim(String productId, String variantId) {
        FoundItem item = foundItemRepository.save(item(new FoundItem(), "ChIJopera"));
        ContactInfo claimant = contactInfoRepository.save(Fixtures.contact("+48501234567", "claimant@example.com"));
        FoundItemClaim claim = Fixtures.claim(new FoundItemClaim(), item, claimant, Instant.now());
        claim.setPaymentProductId(productId);
        claim.setPaymentVariantId(variantId);
        return foundClaimRepository.save(claim);
    }

    private <T extends Thing> T item(T item, String placeId) {
        Place place = placeRepository.save(Fixtures.place(placeId, "Площа Ринок", 49.8419, 24.0315));
        ContactInfo author = contactInfoRepository.save(Fixtures.contact());
        return Fixtures.item(item, "Ключі " + UUID.randomUUID(), LocalDate.now(), documents, place, author);
    }
}
