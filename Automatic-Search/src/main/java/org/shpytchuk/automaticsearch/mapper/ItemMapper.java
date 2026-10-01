package org.shpytchuk.automaticsearch.mapper;

import org.shpytchuk.automaticsearch.entity.Thing;

public final class ItemMapper {

    private ItemMapper() {
    }

    public static <T extends Thing> T copy(Thing from, T to) {
        to.setTitle(from.getTitle());
        to.setDescription(from.getDescription());
        to.setImage(from.getImage());
        to.setDate(from.getDate());
        to.setCompensation(from.getCompensation());
        to.setCurrency(from.getCurrency());
        to.setInfo(from.getInfo());
        to.setPlace(from.getPlace());
        to.setCategory(from.getCategory());
        return to;
    }
}
