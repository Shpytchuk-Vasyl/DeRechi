package org.shpytchuk.adminapi.service.ingest;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.entity.ingest.IngestPost;
import org.shpytchuk.adminapi.repository.ingest.IngestPostRepository;
import org.shpytchuk.adminapi.security.Scope;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@AllArgsConstructor
public class SourceLinks {

    private static final Map<Scope, String> KINDS = Map.of(
            Scope.LOST_ITEM, "LOST",
            Scope.FOUND_ITEM, "FOUND");
    private static final Pattern TELEGRAM_SUPERGROUP = Pattern.compile("-100(\\d+)");
    private static final Pattern FACEBOOK_GROUP = Pattern.compile("[\\w.-]+");
    private static final int TELEGRAM_MESSAGE_SHIFT = 20;

    private final IngestPostRepository repository;

    public Map<Long, String> of(Scope scope, Collection<Long> itemIds) {
        String kind = KINDS.get(scope);
        if (kind == null || itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> links = new HashMap<>();
        for (IngestPost post : repository.findByItemKindAndItemIdInOrderByIdAsc(kind, itemIds)) {
            String url = url(post);
            if (url != null) {
                links.put(post.getItemId(), url);
            }
        }
        return links;
    }

    private static String url(IngestPost post) {
        return switch (post.getSource()) {
            case "telegram" -> telegram(post);
            case "facebook" -> facebook(post);
            default -> null;
        };
    }

    private static String telegram(IngestPost post) {
        Matcher chat = TELEGRAM_SUPERGROUP.matcher(post.getChannel());
        if (!chat.matches() || !post.getExternalId().matches("\\d+")) {
            return null;
        }
        long message = Long.parseLong(post.getExternalId()) >> TELEGRAM_MESSAGE_SHIFT;
        return "https://t.me/c/%s/%d".formatted(chat.group(1), message);
    }

    private static String facebook(IngestPost post) {
        if (!FACEBOOK_GROUP.matcher(post.getChannel()).matches() || !post.getExternalId().matches("\\d+")) {
            return null;
        }
        return "https://www.facebook.com/groups/%s/permalink/%s/".formatted(post.getChannel(), post.getExternalId());
    }
}
