package org.shpytchuk.worker.config;

import org.shpytchuk.worker.entity.core.thing.FoundItem;
import org.shpytchuk.worker.entity.core.thing.LostItem;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.repository.FoundItemRepository;
import org.shpytchuk.worker.repository.LostItemRepository;
import org.shpytchuk.worker.service.ItemService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ItemSearchConfig {

    @Bean
    public ItemService<LostItem> lostItemSearch(LostItemRepository repository,
                                                LanguageResolver languageResolver) {
        return new ItemService<>(repository, languageResolver);
    }

    @Bean
    public ItemService<FoundItem> foundItemSearch(FoundItemRepository repository,
                                                  LanguageResolver languageResolver) {
        return new ItemService<>(repository, languageResolver);
    }
}
