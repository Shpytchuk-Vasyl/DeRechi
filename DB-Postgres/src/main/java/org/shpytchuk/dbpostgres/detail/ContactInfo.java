package org.shpytchuk.dbpostgres.detail;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
public class ContactInfo {

    public enum SocialMediaEnum {
        TELEGRAM,
        VIBER,
        WHATSAPP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pattern(
            regexp = "^\\+[1-9]\\d{7,14}$"
    )
    @Column(nullable = false, length = 16)
    private String phone;

    @Email
    @Size(max = 50)
    @Column(length = 50)
    private String email;


    @Enumerated(EnumType.ORDINAL)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column()
    private SocialMediaEnum[] socialMedias;

}