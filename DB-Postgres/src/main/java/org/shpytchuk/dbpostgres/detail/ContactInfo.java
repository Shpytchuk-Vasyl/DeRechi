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
        WHATSAPP,
        FACEBOOK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Pattern(
            regexp = "^\\+[1-9]\\d{7,14}$"
    )
    @Column(length = 16)
    private String phone;

    @Email
    @Size(max = 50)
    @Column(length = 50)
    private String email;

    @Size(max = 50)
    @Column(length = 50)
    private String nickName;

    @Size(max = 255)
    @Column(length = 255)
    private String profileUrl;


    @Enumerated(EnumType.ORDINAL)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column()
    private SocialMediaEnum[] socialMedias;

}