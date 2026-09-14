package org.shpytchuk.automaticsearch.config;

import org.shpytchuk.automaticsearch.entity.FoundItem;
import org.shpytchuk.automaticsearch.entity.LostItem;
import org.shpytchuk.automaticsearch.language.LanguageResolver;
import org.shpytchuk.automaticsearch.repository.FoundItemRepository;
import org.shpytchuk.automaticsearch.repository.LostItemRepository;
import org.shpytchuk.automaticsearch.service.ItemService;
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
