package org.shpytchuk.clientapi.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.shpytchuk.clientapi.entity.detail.ContactInfo;
import org.shpytchuk.clientapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.clientapi.entity.found.FoundItem;
import org.shpytchuk.clientapi.entity.found.FoundItemClaim;
import org.shpytchuk.clientapi.entity.lost.LostItem;
import org.shpytchuk.clientapi.entity.lost.LostItemClaim;
import org.shpytchuk.clientapi.entity.detail.Place;
import org.shpytchuk.clientapi.entity.thing.Thing;
import org.shpytchuk.clientapi.event.ClaimEvent;
import org.shpytchuk.clientapi.repository.detail.ContactInfoRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemClaimRepository;
import org.shpytchuk.clientapi.repository.found.FoundItemRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemClaimRepository;
import org.shpytchuk.clientapi.repository.lost.LostItemRepository;
import org.shpytchuk.clientapi.repository.detail.PlaceRepository;
import org.shpytchuk.clientapi.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ClaimControllerTests extends AbstractGraphQlTests {

    private static final String EXCHANGE = "derechi.items";
    private static final String PHONE = "+48501234567";
    private static final String EMAIL = "claimant@example.com";

    private static final String CLAIM_LOST = """
            mutation Claim($id: ID!, $contact: ContactInfoInput!) {
              claimLostItem(id: $id, contact: $contact) { id repeated }
            }
            """;

    private static final String CLAIM_FOUND = """
            mutation Claim($id: ID!, $contact: ContactInfoInput!) {
              claimFoundItem(id: $id, contact: $contact) { id repeated }
            }
            """;

    private static final String CONFIRM = """
            mutation Confirm($token: String!) {
              confirmReturn(token: $token)
            }
            """;

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
    void claimsALostItemAndStoresTheClaimantContactsSeparately() {
        LostItem item = lostItem("ChIJrynok");

        Long claimId = claimLost(item.getId(), claimant(PHONE, EMAIL))
                .path("claimLostItem.repeated").entity(Boolean.class).isEqualTo(false)
                .path("claimLostItem.id").entity(Long.class).get();

        LostItemClaim stored = lostClaimRepository.findFirstByItemIdAndContactInfoPhoneOrderByIdAsc(item.getId(), PHONE).orElseThrow();
        assertThat(stored.getId()).isEqualTo(claimId);
        assertThat(stored.getToken()).hasSize(36);
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getConfirmedAt()).isNull();
        assertThat(stored.getAuthorRemindedAt()).isNull();
        assertThat(stored.getClaimantRemindedAt()).isNull();

        ContactInfo contact = stored.getContactInfo();
        assertThat(contact.getId()).isNotEqualTo(item.getInfo().getId());
        assertThat(contact.getPhone()).isEqualTo(PHONE);
        assertThat(contact.getEmail()).isEqualTo(EMAIL);
        assertThat(contact.getSocialMedias()).containsExactly(SocialMediaEnum.VIBER, SocialMediaEnum.WHATSAPP);
    }

    @Test
    void claimsAFoundItem() {
        FoundItem item = foundItem("ChIJrynok");

        Long claimId = claimFound(item.getId(), claimant(PHONE, EMAIL))
                .path("claimFoundItem.repeated").entity(Boolean.class).isEqualTo(false)
                .path("claimFoundItem.id").entity(Long.class).get();

        FoundItemClaim stored = foundClaimRepository.findFirstByItemIdAndContactInfoEmailIgnoreCaseOrderByIdAsc(item.getId(), EMAIL).orElseThrow();
        assertThat(stored.getId()).isEqualTo(claimId);
        assertThat(stored.getContactInfo().getPhone()).isEqualTo(PHONE);
        assertThat(lostClaimRepository.findAll()).isEmpty();
    }

    @Test
    void publishesTheLostClaimedEventWithTheClaimId() {
        Long claimId = claimLostId(lostItem("ChIJrynok").getId(), claimant(PHONE, EMAIL));

        assertThat(published("item.lost.claimed").id()).isEqualTo(claimId);
    }

    @Test
    void publishesTheFoundClaimedEventWithTheClaimId() {
        Long claimId = claimFound(foundItem("ChIJrynok").getId(), claimant(PHONE, EMAIL))
                .path("claimFoundItem.id").entity(Long.class).get();

        assertThat(published("item.found.claimed").id()).isEqualTo(claimId);
    }

    @Test
    void answersARepeatByPhoneWithTheExistingClaimAndSendsNothing() {
        Long itemId = lostItem("ChIJrynok").getId();
        Long first = claimLostId(itemId, claimant(PHONE, EMAIL));

        claimLost(itemId, claimant(PHONE, "someone.else@example.com"))
                .path("claimLostItem.repeated").entity(Boolean.class).isEqualTo(true)
                .path("claimLostItem.id").entity(Long.class).isEqualTo(first);

        assertThat(lostClaimRepository.count()).isEqualTo(1);
        assertThat(contactInfoRepository.count()).isEqualTo(2);
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(EXCHANGE), eq("item.lost.claimed"), any(ClaimEvent.class));
    }

    @Test
    void answersARepeatByEmailWithTheExistingClaimAndSendsNothing() {
        Long itemId = foundItem("ChIJrynok").getId();
        Long first = claimFound(itemId, claimant(PHONE, EMAIL))
                .path("claimFoundItem.id").entity(Long.class).get();

        claimFound(itemId, claimant("+380501112233", EMAIL))
                .path("claimFoundItem.repeated").entity(Boolean.class).isEqualTo(true)
                .path("claimFoundItem.id").entity(Long.class).isEqualTo(first);

        assertThat(foundClaimRepository.count()).isEqualTo(1);
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(EXCHANGE), eq("item.found.claimed"), any(ClaimEvent.class));
    }

    @Test
    void comparesTheEmailOfARepeatCaseInsensitively() {
        Long itemId = lostItem("ChIJrynok").getId();
        Long first = claimLostId(itemId, claimant(PHONE, EMAIL));

        claimLost(itemId, claimant("+380501112233", "Claimant@Example.com"))
                .path("claimLostItem.repeated").entity(Boolean.class).isEqualTo(true)
                .path("claimLostItem.id").entity(Long.class).isEqualTo(first);
    }

    @Test
    void doesNotTreatAClaimOnAnotherItemAsARepeat() {
        Long keys = lostItem("ChIJrynok").getId();
        Long wallet = lostItem("ChIJopera").getId();

        Long first = claimLostId(keys, claimant(PHONE, EMAIL));
        Long second = claimLost(wallet, claimant(PHONE, EMAIL))
                .path("claimLostItem.repeated").entity(Boolean.class).isEqualTo(false)
                .path("claimLostItem.id").entity(Long.class).get();

        assertThat(second).isNotEqualTo(first);
        verify(rabbitTemplate, times(2))
                .convertAndSend(eq(EXCHANGE), eq("item.lost.claimed"), any(ClaimEvent.class));
    }

    @Test
    void reportsAnUnknownLostItemAsNotFound() {
        expectError(claimLost(-1L, claimant(PHONE, EMAIL)), ErrorType.NOT_FOUND, "LostItem.id: -1");
    }

    @Test
    void reportsAnUnknownFoundItemAsNotFound() {
        expectError(claimFound(-1L, claimant(PHONE, EMAIL)), ErrorType.NOT_FOUND, "FoundItem.id: -1");
    }

    @Test
    void rejectsAPhoneThatIsNotE164AndStoresNothing() {
        Long itemId = lostItem("ChIJrynok").getId();

        expectError(claimLost(itemId, claimant("0671234567", EMAIL)), ErrorType.BAD_REQUEST, "phone");

        assertThat(lostClaimRepository.findAll()).isEmpty();
        assertThat(contactInfoRepository.count()).isEqualTo(1);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void rejectsAnInvalidEmail() {
        Long itemId = foundItem("ChIJrynok").getId();

        expectError(claimFound(itemId, claimant(PHONE, "not-an-email")), ErrorType.BAD_REQUEST, "email");

        assertThat(foundClaimRepository.findAll()).isEmpty();
    }

    @Test
    void confirmsTheReturnOfALostItem() {
        Long claimId = claimLostId(lostItem("ChIJrynok").getId(), claimant(PHONE, EMAIL));
        String token = lostClaimRepository.findById(claimId).orElseThrow().getToken();

        confirm(token).path("confirmReturn").entity(Boolean.class).isEqualTo(true);

        assertThat(lostClaimRepository.findById(claimId).orElseThrow().getConfirmedAt()).isNotNull();
        assertThat(published("item.lost.returned").id()).isEqualTo(claimId);
    }

    @Test
    void confirmsTheReturnOfAFoundItem() {
        Long claimId = claimFound(foundItem("ChIJrynok").getId(), claimant(PHONE, EMAIL))
                .path("claimFoundItem.id").entity(Long.class).get();
        String token = foundClaimRepository.findById(claimId).orElseThrow().getToken();

        confirm(token).path("confirmReturn").entity(Boolean.class).isEqualTo(true);

        assertThat(foundClaimRepository.findById(claimId).orElseThrow().getConfirmedAt()).isNotNull();
        assertThat(published("item.found.returned").id()).isEqualTo(claimId);
    }

    @Test
    void confirmingTwiceKeepsTheFirstStampAndPublishesOnce() {
        Long claimId = claimLostId(lostItem("ChIJrynok").getId(), claimant(PHONE, EMAIL));
        String token = lostClaimRepository.findById(claimId).orElseThrow().getToken();

        confirm(token).path("confirmReturn").entity(Boolean.class).isEqualTo(true);
        Instant firstStamp = lostClaimRepository.findById(claimId).orElseThrow().getConfirmedAt();
        confirm(token).path("confirmReturn").entity(Boolean.class).isEqualTo(true);

        assertThat(lostClaimRepository.findById(claimId).orElseThrow().getConfirmedAt()).isEqualTo(firstStamp);
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(EXCHANGE), eq("item.lost.returned"), any(ClaimEvent.class));
    }

    @Test
    void reportsAnUnknownTokenAsNotFound() {
        expectError(confirm("00000000-0000-0000-0000-000000000000"), ErrorType.NOT_FOUND, "Claim.id");

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    private ClaimEvent published(String routingKey) {
        ArgumentCaptor<ClaimEvent> captor = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(routingKey), captor.capture());
        return captor.getValue();
    }

    private static void expectError(GraphQlTester.Response response, ErrorType type, String message) {
        response.errors().satisfy(errors -> {
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getErrorType()).isEqualTo(type);
            assertThat(errors.getFirst().getMessage()).contains(message);
        });
    }

    private Long claimLostId(Long itemId, Map<String, Object> contact) {
        return claimLost(itemId, contact).path("claimLostItem.id").entity(Long.class).get();
    }

    private GraphQlTester.Response claimLost(Long itemId, Map<String, Object> contact) {
        return tester.document(CLAIM_LOST).variable("id", itemId).variable("contact", contact).execute();
    }

    private GraphQlTester.Response claimFound(Long itemId, Map<String, Object> contact) {
        return tester.document(CLAIM_FOUND).variable("id", itemId).variable("contact", contact).execute();
    }

    private GraphQlTester.Response confirm(String token) {
        return tester.document(CONFIRM).variable("token", token).execute();
    }

    private static Map<String, Object> claimant(String phone, String email) {
        return Map.of(
                "phone", phone,
                "email", email,
                "socialMedias", List.of("VIBER", "WHATSAPP"));
    }

    private LostItem lostItem(String placeId) {
        return lostItemRepository.save(item(new LostItem(), placeId));
    }

    private FoundItem foundItem(String placeId) {
        return foundItemRepository.save(item(new FoundItem(), placeId));
    }

    private <T extends Thing> T item(T item, String placeId) {
        Place place = placeRepository.save(Fixtures.place(placeId, "Площа Ринок", 49.8419, 24.0315));
        ContactInfo author = contactInfoRepository.save(Fixtures.contact());
        return Fixtures.item(item, "Ключі", LocalDate.now(), documents, place, author);
    }
}
