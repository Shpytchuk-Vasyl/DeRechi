package org.shpytchuk.adminapi.controller;

import lombok.AllArgsConstructor;
import org.shpytchuk.adminapi.entity.ContactInfo.SocialMediaEnum;
import org.shpytchuk.adminapi.form.ItemFilter;
import org.shpytchuk.adminapi.repository.ThingCategoryRepository;
import org.shpytchuk.adminapi.security.Scope;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

@Component
@AllArgsConstructor
public class ItemModel {

    private final ThingCategoryRepository categoryRepository;


    public void describe(Model model, Scope scope, String basePath, String titleKey) {
        model.addAttribute("scope", scope.name());
        model.addAttribute("basePath", basePath);
        model.addAttribute("titleKey", titleKey);
    }

    public void forFilter(Model model, ItemFilter filter) {
        model.addAttribute("categories", categoryRepository.findAllByOrderByKeyAsc());
        model.addAttribute("filterQuery", filter.queryString());
    }

    public void forForm(Model model, boolean creating) {
        model.addAttribute("creating", creating);
        model.addAttribute("categories", categoryRepository.findAllByOrderByKeyAsc());
        model.addAttribute("allSocialMedias", SocialMediaEnum.values());
    }
}
