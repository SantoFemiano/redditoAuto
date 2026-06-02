package com.santofem.redditoauto.entity;

import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.entity.enums.TipoCambio;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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
    private String nomeMotore; // es. "2.0 TDI 150 CV DSG"

    @Column(name = "anno_produzione", nullable = false)
    private Integer annoProduzione;

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
    private Integer potenzaKw; // Necessario per calcolo bollo ACI

    @Column(name = "potenza_cv")
    private Integer potenzaCv;

    @Column(name = "cilindrata_cc")
    private Integer cilindrataCC;

    // -------------------------
    // CONSUMI
    // -------------------------

    @Column(name = "consumo_medio_litri_100km", precision = 5, scale = 2)
    private BigDecimal consumoMedioLitri100km;

    @Column(name = "consumo_urbano_litri_100km", precision = 5, scale = 2)
    private BigDecimal consumoUrbanoLitri100km;

    @Column(name = "consumo_extraurbano_litri_100km", precision = 5, scale = 2)
    private BigDecimal consumoExtraurbanoLitri100km;

    @Column(name = "autonomia_km_elettrica")
    private Integer autonomiaKmElettrica; // Solo EV/PHEV

    // -------------------------
    // PNEUMATICI
    // -------------------------

    @Column(name = "misura_pneumatici_anteriori", length = 30)
    private String misuraPneumaticiAnteriori; // es. "205/55 R16"

    @Column(name = "misura_pneumatici_posteriori", length = 30)
    private String misuraPneumaticiPosteriori;

    @Builder.Default
    @Column(name = "run_flat", nullable = false)
    private Boolean runFlat = false;

    // -------------------------
    // PREZZO LISTINO
    // -------------------------

    @Column(name = "prezzo_listino_eur", precision = 12, scale = 2)
    private BigDecimal prezzoListinoEur;

    // -------------------------
    // COSTI TAGLIANDI
    // -------------------------

    @Column(name = "costo_tagliando_base_eur", precision = 8, scale = 2)
    private BigDecimal costoTagliandoBaseEur; // Tagliando ordinario

    @Column(name = "costo_tagliando_maior_eur", precision = 8, scale = 2)
    private BigDecimal costoTagliandoMaiorEur; // Tagliando maggiore (cinghia, ecc.)

    @Column(name = "intervallo_tagliando_km")
    private Integer intervalloTagliandoKm; // es. 15000, 20000, 30000

    @Column(name = "intervallo_tagliando_maior_km")
    private Integer intervalloTagliandoMaiorKm;

    // -------------------------
    // ASSICURAZIONE
    // -------------------------

    @Column(name = "gruppo_assicurativo")
    private Integer gruppoAssicurativo; // Classe rischio 1-20

    // -------------------------
    // METADATA
    // -------------------------

    @Column(name = "fonte_dati", length = 500)
    private String fonteDati; // URL/fonte usata dall'AI

    @Column(name = "data_estrazione")
    private LocalDateTime dataEstrazione;

    @Builder.Default
    @Column(name = "confermato_manualmente")
    private Boolean confermatoManualmente = false;
}
