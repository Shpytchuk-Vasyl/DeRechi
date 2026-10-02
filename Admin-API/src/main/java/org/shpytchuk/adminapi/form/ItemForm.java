package org.shpytchuk.adminapi.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.shpytchuk.adminapi.entity.detail.ContactInfo.SocialMediaEnum;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ItemForm {

    private Long id;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 250)
    private String description;

    @Size(max = 200)
    private String image;

    @NotNull
    @PastOrPresent
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date;

    @PositiveOrZero
    private Integer compensation;

    @Size(min = 3, max = 3)
    private String currency;

    @NotNull
    private Long categoryId;

    @NotBlank
    @Size(max = 255)
    private String placeId;

    @NotBlank
    @Size(max = 100)
    private String placeName;

    @NotBlank
    @Size(min = 2, max = 2)
    private String countryCode;

    @NotNull
    private Double lat;

    @NotNull
    private Double lon;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "{validation.phone}")
    private String phone;

    @NotBlank
    @Email
    @Size(max = 50)
    private String email;

    private List<SocialMediaEnum> socialMedias = new ArrayList<>();

    public void setCurrency(String currency) {
        this.currency = currency == null || currency.isBlank() ? null : currency;
    }
}
