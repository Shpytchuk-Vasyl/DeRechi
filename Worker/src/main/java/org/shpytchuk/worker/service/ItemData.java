package org.shpytchuk.worker.service;

import java.time.LocalDate;

public interface ItemData {

    Long getId();

    LocalDate getDate();

    Long getCategory();

    Double getLat();

    Double getLon();

    String getTitle();

    String getDescription();
}
