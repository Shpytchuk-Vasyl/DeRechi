package org.shpytchuk.worker.support;

import org.shpytchuk.worker.config.ClaimsProperties;
import org.shpytchuk.worker.entity.found.FoundItemClaim;
import org.shpytchuk.worker.entity.lost.LostItemClaim;
import org.shpytchuk.worker.entity.detail.ContactInfo;
import org.shpytchuk.worker.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.entity.detail.Place;
import org.shpytchuk.worker.entity.matching.Claim;
import org.shpytchuk.worker.entity.thing.Thing;
import org.shpytchuk.worker.entity.thing.ThingCategory;
import org.shpytchuk.worker.event.ItemCreatedEvent;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class Fixtures {

    public static final ClaimsProperties PROPERTIES = new ClaimsProperties(
            "worker.claims", "derechi.notifications", "http://localhost:3000/",
            Duration.ofMinutes(10), Duration.ofDays(1), Duration.ofDays(1), Duration.ofDays(7), Duration.ofDays(365));


    private Fixtures() {
    }

    /** Same settings as spring.messages; without the system-locale switch "en" would land on the machine's language. */
    public static MessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(true);
        return source;
    }

    public static LostItemClaim lostClaim(String authorPhone, SocialMediaEnum... claimantMessengers) {
        LostItem item = new LostItem();
        item.setId(1L);
        item.setTitle("Чорний рюкзак");
        item.setInfo(contact(authorPhone, "owner@example.com"));

        LostItemClaim claim = new LostItemClaim();
        claim.setId(42L);
        claim.setItem(item);
        claim.setContactInfo(contact("+380509876543", "finder@example.com", claimantMessengers));
        claim.setToken("6f1c2a52-0d7e-4c1e-9a43-6f0d4f3a9b11");
        claim.setCreatedAt(Instant.parse("2026-09-01T10:00:00Z"));
        return claim;
    }

    public static FoundItemClaim foundClaim(String authorPhone) {
        FoundItem item = new FoundItem();
        item.setId(2L);
        item.setTitle("Black backpack");
        item.setInfo(contact(authorPhone, "finder@example.com"));

        FoundItemClaim claim = new FoundItemClaim();
        claim.setId(43L);
        claim.setItem(item);
        claim.setContactInfo(contact("+12125550123", "owner@example.com"));
        claim.setToken("0b9a3c1d-5e2f-4a6b-8c7d-9e0f1a2b3c4d");
        claim.setCreatedAt(Instant.parse("2026-09-01T10:00:00Z"));
        return claim;
    }

    public static <T extends Thing> T item(T item, String title, String description, LocalDate date,
                                           ThingCategory category, Place place, ContactInfo info) {
        item.setTitle(title);
        item.setDescription(description);
        item.setDate(date);
        item.setCurrency("UAH");
        item.setCategory(category);
        item.setPlace(place);
        item.setInfo(info);
        return item;
    }

    public static <C extends Claim> C claim(C claim, Thing item, ContactInfo claimant, Instant createdAt) {
        switch (claim) {
            case LostItemClaim lost -> lost.setItem((LostItem) item);
            case FoundItemClaim found -> found.setItem((FoundItem) item);
            default -> throw new IllegalArgumentException("Unknown claim type " + claim.getClass());
        }
        claim.setContactInfo(claimant);
        claim.setToken(UUID.randomUUID().toString());
        claim.setCreatedAt(createdAt);
        return claim;
    }

    public static ItemCreatedEvent created(Long id, String title, LocalDate date, Long categoryId, double lat, double lon) {
        ItemCreatedEvent event = new ItemCreatedEvent();
        event.setId(id);
        event.setTitle(title);
        event.setDate(date);
        event.setCategory(categoryId);
        event.setLat(lat);
        event.setLon(lon);
        return event;
    }

    public static ContactInfo contact(String phone, String email, SocialMediaEnum... socialMedias) {
        ContactInfo contact = new ContactInfo();
        contact.setPhone(phone);
        contact.setEmail(email);
        contact.setSocialMedias(socialMedias);
        return contact;
    }
}
