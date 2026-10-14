package org.shpytchuk.adminapi.model;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.config.property.CountriesProperties;
import org.shpytchuk.adminapi.controller.UploadController;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.form.ItemForm;
import org.shpytchuk.adminapi.repository.thing.ThingCategoryRepository;
import org.shpytchuk.adminapi.security.Scope;
import org.shpytchuk.adminapi.service.ItemKind;
import org.shpytchuk.adminapi.service.ingest.SourceLinks;
import org.shpytchuk.adminapi.view.GoogleMaps;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
@AllArgsConstructor
public class ItemModel {

    private static final String SOURCES = "sources";

    private final ThingCategoryRepository categoryRepository;
    private final GoogleMaps maps;
    private final CountriesProperties countries;
    private final SourceLinks sourceLinks;


    public void describe(Model model, ItemKind kind) {
        model.addAttribute("scope", kind.scope().name());
        model.addAttribute("basePath", kind.basePath());
        model.addAttribute("titleKey", kind.titleKey());
    }

    @SuppressWarnings("unchecked")
    public void forSources(Model model, Scope scope, Collection<Long> itemIds) {
        Map<String, String> sources = (Map<String, String>) model.asMap()
                .computeIfAbsent(SOURCES, key -> new HashMap<String, String>());
        sourceLinks.of(scope, itemIds).forEach((id, url) -> sources.put(sourceKey(scope, id), url));
    }

    public static String sourceKey(Scope scope, Long itemId) {
        return scope.name() + ":" + itemId;
    }

    public void forFilter(Model model, ItemFilter filter) {
        model.addAttribute("categories", categoryRepository.findAllByOrderByKeyAsc());
        model.addAttribute("filterQuery", filter.queryString());
    }

    public void forForm(Model model, boolean creating) {
        model.addAttribute("creating", creating);
        model.addAttribute("categories", categoryRepository.findAllByOrderByKeyAsc());
        model.addAttribute("allSocialMedias", SocialMediaEnum.values());
        model.addAttribute("countries", countries.supported());
        model.addAttribute("currencies", countries.currencies());
        model.addAttribute("maps", maps);
        model.addAttribute("uploadPath", UploadController.BASE_PATH);
    }

    public ItemForm blankForm() {
        ItemForm form = new ItemForm();
        form.setDate(LocalDate.now());
        form.setCountryCode(countries.fallback());
        return form;
    }
}
