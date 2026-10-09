package org.shpytchuk.worker.entity.detail;

import jakarta.persistence.*;
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
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phone;

    @Column
    private String email;

    @Enumerated(EnumType.ORDINAL)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column
    private SocialMediaEnum[] socialMedias;

}
