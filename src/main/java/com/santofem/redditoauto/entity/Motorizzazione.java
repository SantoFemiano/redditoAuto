package com.santofem.redditoauto.entity;

import com.santofem.redditoauto.entity.enums.TipoCambio;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
    name = "motorizzazione",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"modello_id", "nome_motore", "anno_produzione"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "modello")
public class Motorizzazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "modello_id", nullable = false)
    private Modello modello;

    // -------------------------
    // IDENTIFICAZIONE MOTORE
    // -------------------------

    @Column(name = "nome_motore", nullable = false, length = 150)
    private String nomeMotore;

    @Column(name = "anno_produzione", nullable = false)
    private Integer annoProduzione;

    @Column(name="anno_fine_produzione",nullable = true)
    private Integer annoFineProduzione;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_carburante", nullable = false, length = 30)
    private TipoCarburante tipoCarburante;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_cambio", length = 30)
    private TipoCambio tipoCambio;

    // -------------------------
    // DATI TECNICI
    // -------------------------

    @Column(name = "potenza_kw", nullable = false)
    private Integer potenzaKw;

    @Column(name = "potenza_cv")
    private Integer potenzaCv;

    @Column(name = "cilindrata_cc")
    private Integer cilindrataCC;

    // -------------------------
    // CONSUMI — tutti BigDecimal per precisione nei calcoli finanziari
    // -------------------------

    @Column(name = "consumo_medio_litri_100km", precision = 5, scale = 2)
    private BigDecimal consumoMedioLitri100km;

    @Column(name = "consumo_urbano_litri_100km", precision = 5, scale = 2)
    private BigDecimal consumoUrbanoLitri100km;

    @Column(name = "consumo_extraurbano_litri_100km", precision = 5, scale = 2)
    private BigDecimal consumoExtraurbanoLitri100km;

    @Column(name = "autonomia_km_elettrica")
    private Integer autonomiaKmElettrica;

    // -------------------------
    // PNEUMATICI
    // -------------------------

    @Column(name = "misura_pneumatici_anteriori", length = 30)
    private String misuraPneumaticiAnteriori;

    @Column(name = "misura_pneumatici_posteriori", length = 30)
    private String misuraPneumaticiPosteriori;

    @Builder.Default
    @Column(name = "run_flat", nullable = false)
    private Boolean runFlat = false;

    @Column(name = "km_durata_pneumatici")
    private Integer kmDurataPneumatici;

    // -------------------------
    // PREZZO LISTINO — BigDecimal per precisione monetaria
    // -------------------------

    @Column(name = "prezzo_listino_eur", precision = 12, scale = 2)
    private BigDecimal prezzoListinoEur;

    // -------------------------
    // COSTI TAGLIANDI — BigDecimal per precisione monetaria
    // -------------------------

    @Column(name = "costo_tagliando_base_eur", precision = 8, scale = 2)
    private BigDecimal costoTagliandoBaseEur;

    @Column(name = "costo_tagliando_maior_eur", precision = 8, scale = 2)
    private BigDecimal costoTagliandoMaiorEur;

    @Column(name = "intervallo_tagliando_km")
    private Integer intervalloTagliandoKm;

    @Column(name = "intervallo_tagliando_maior_km")
    private Integer intervalloTagliandoMaiorKm;

    // -------------------------
    // ASSICURAZIONE
    // -------------------------

    @Column(name = "gruppo_assicurativo")
    private Integer gruppoAssicurativo;

    // -------------------------
    // METADATA
    // -------------------------

    @Column(name = "fonte_dati", length = 500)
    private String fonteDati;

    @Column(name = "data_estrazione")
    private LocalDateTime dataEstrazione;

    @Builder.Default
    @Column(name = "confermato_manualmente")
    private Boolean confermatoManualmente = false;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Motorizzazione that = (Motorizzazione) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
