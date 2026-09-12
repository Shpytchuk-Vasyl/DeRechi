package org.shpytchuk.dbpostgres.core;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ContactInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pattern(
            regexp = "^\\+[1-9]\\d{7,14}$"
    )
    @Column(nullable = false, length = 16)
    private String phone;

    @Email
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String email;


    @Enumerated(EnumType.ORDINAL)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column()
    private SocialMediaEnum[] socialMedias;

}
