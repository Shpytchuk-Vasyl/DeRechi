package org.shpytchuk.clientapi.entity.detail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    public enum SocialMediaEnum {
        TELEGRAM,
        VIBER,
        WHATSAPP,
        FACEBOOK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 16)
    private String phone;

    @Column
    private String email;

    @Column(length = 50)
    private String nickName;

    @Column(length = 255)
    private String profileUrl;

    @Enumerated(EnumType.ORDINAL)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column
    private SocialMediaEnum[] socialMedias;
}
