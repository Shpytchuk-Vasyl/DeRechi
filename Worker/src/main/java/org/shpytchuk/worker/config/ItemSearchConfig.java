package org.shpytchuk.worker.config;

import jakarta.persistence.EntityManager;
import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.handler.ItemMatchHandler;
import org.shpytchuk.worker.service.ItemKind;
import org.shpytchuk.worker.service.ItemKinds;
import org.shpytchuk.worker.service.ItemService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ItemSearchConfig {

    @Bean
    public ItemService<LostItem> lostItemSearch(EntityManager entityManager) {
        return new ItemService<>(entityManager, LostItem.class);
    }

    @Bean
    public ItemService<FoundItem> foundItemSearch(EntityManager entityManager) {
        return new ItemService<>(entityManager, FoundItem.class);
    }

    @Bean
    public ItemMatchHandler<FoundItem> lostItemCreatedHandler(ItemKinds kinds, ItemService<FoundItem> foundItemSearch) {
        return new ItemMatchHandler<>(kinds.of(ItemKind.LOST), foundItemSearch);
    }

    @Bean
    public ItemMatchHandler<LostItem> foundItemCreatedHandler(ItemKinds kinds, ItemService<LostItem> lostItemSearch) {
        return new ItemMatchHandler<>(kinds.of(ItemKind.FOUND), lostItemSearch);
    }
}
