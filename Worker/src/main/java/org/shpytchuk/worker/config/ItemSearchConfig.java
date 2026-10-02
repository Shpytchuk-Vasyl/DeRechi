package org.shpytchuk.worker.config;

import org.shpytchuk.worker.entity.found.FoundItem;
import org.shpytchuk.worker.entity.lost.LostItem;
import org.shpytchuk.worker.language.LanguageResolver;
import org.shpytchuk.worker.repository.found.FoundItemRepository;
import org.shpytchuk.worker.repository.lost.LostItemRepository;
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
