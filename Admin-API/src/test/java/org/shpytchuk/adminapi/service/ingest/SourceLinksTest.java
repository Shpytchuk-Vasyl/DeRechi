package org.shpytchuk.adminapi.service.ingest;

import org.junit.jupiter.api.Test;
import org.shpytchuk.adminapi.entity.ingest.IngestPost;
import org.shpytchuk.adminapi.repository.ingest.IngestPostRepository;
import org.shpytchuk.adminapi.security.Scope;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceLinksTest {

    private final IngestPostRepository repository = mock(IngestPostRepository.class);
    private final SourceLinks links = new SourceLinks(repository);

    @Test
    void buildsTelegramAndFacebookLinksInOneQuery() {
        when(repository.findByItemKindAndItemIdInOrderByIdAsc(eq("LOST"), any())).thenReturn(List.of(
                post("telegram", "-1001234567890", Long.toString(42L << 20), 1L),
                post("facebook", "find.rivne", "9876543210", 2L)));

        Map<Long, String> result = links.of(Scope.LOST_ITEM, List.of(1L, 2L));

        assertThat(result).containsEntry(1L, "https://t.me/c/1234567890/42")
                .containsEntry(2L, "https://www.facebook.com/groups/find.rivne/permalink/9876543210/");
    }

    @Test
    void laterPostOfTheSameItemWins() {
        when(repository.findByItemKindAndItemIdInOrderByIdAsc(eq("FOUND"), any())).thenReturn(List.of(
                post("facebook", "old", "1", 5L), post("facebook", "new", "2", 5L)));

        assertThat(links.of(Scope.FOUND_ITEM, List.of(5L)).get(5L)).contains("/groups/new/");
    }

    @Test
    void skipsUnknownSourcesAndUnsafeChannels() {
        when(repository.findByItemKindAndItemIdInOrderByIdAsc(eq("LOST"), any())).thenReturn(List.of(
                post("site", "x", "1", 1L),
                post("facebook", "a/b?c", "1", 2L),
                post("telegram", "-5", "1", 3L)));

        assertThat(links.of(Scope.LOST_ITEM, List.of(1L, 2L, 3L))).isEmpty();
    }

    @Test
    void historyScopesAndEmptyPagesDoNotQuery() {
        assertThat(links.of(Scope.LOST_ITEM_HISTORY, List.of(1L))).isEmpty();
        assertThat(links.of(Scope.LOST_ITEM, List.of())).isEmpty();
        verify(repository, never()).findByItemKindAndItemIdInOrderByIdAsc(any(), any());
    }

    private static IngestPost post(String source, String channel, String externalId, Long itemId) {
        IngestPost post = new IngestPost();
        post.setSource(source);
        post.setChannel(channel);
        post.setExternalId(externalId);
        post.setItemId(itemId);
        return post;
    }
}
