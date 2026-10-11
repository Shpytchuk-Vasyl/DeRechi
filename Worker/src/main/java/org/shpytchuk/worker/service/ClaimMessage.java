package org.shpytchuk.worker.service;

import org.jspecify.annotations.Nullable;
import org.shpytchuk.worker.entity.detail.ContactInfo;
import org.shpytchuk.worker.entity.matching.Claim;

import java.util.function.Function;


enum ClaimMessage {

    CREATED(ClaimNotifier.CREATED_ROUTING_KEY, Recipient.AUTHOR, kind -> "claim." + kind.segment(), null),
    AUTHOR_REMINDER(ClaimNotifier.REMINDER_ROUTING_KEY, Recipient.AUTHOR, "claim.reminder.author", "author-reminder"),
    CLAIMANT_REMINDER(ClaimNotifier.REMINDER_ROUTING_KEY, Recipient.CLAIMANT, "claim.reminder.claimant", "claimant-reminder"),
    UNLOCKED(ClaimNotifier.UNLOCKED_ROUTING_KEY, Recipient.CLAIMANT, "claim.unlocked", "unlocked");

    private final String routingKey;
    private final Recipient recipient;
    private final Function<ItemKind, String> textKey;
    private final @Nullable String deduplicationSuffix;

    ClaimMessage(String routingKey, Recipient recipient, Function<ItemKind, String> textKey,
                 @Nullable String deduplicationSuffix) {
        this.routingKey = routingKey;
        this.recipient = recipient;
        this.textKey = textKey;
        this.deduplicationSuffix = deduplicationSuffix;
    }

    ClaimMessage(String routingKey, Recipient recipient, String textKey, String deduplicationSuffix) {
        this(routingKey, recipient, kind -> textKey, deduplicationSuffix);
    }

    String routingKey() {
        return routingKey;
    }

    Recipient recipient() {
        return recipient;
    }

    String textKey(ItemKind kind) {
        return textKey.apply(kind);
    }

    String deduplicationKey(ItemKind kind, Claim claim) {
        String key = "claim:%s:%d".formatted(kind.segment(), claim.getId());
        return deduplicationSuffix == null ? key : key + ":" + deduplicationSuffix;
    }

    enum Recipient {

        AUTHOR {
            @Override
            ContactInfo of(Claim claim) {
                return claim.notice().getInfo();
            }
        },
        CLAIMANT {
            @Override
            ContactInfo of(Claim claim) {
                return claim.getContactInfo();
            }
        };

        abstract ContactInfo of(Claim claim);
    }
}
