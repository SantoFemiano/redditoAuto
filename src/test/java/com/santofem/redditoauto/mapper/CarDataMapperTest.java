package com.santofem.redditoauto.mapper;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.entity.enums.TipoCambio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CarDataMapper — Unit Tests")
class CarDataMapperTest {

    private CarDataMapper mapper;
    private Modello modelloGolf;

    @BeforeEach
    void setUp() {
        mapper = new CarDataMapper();

        Marca vw = Marca.builder().id(1L).nome("Volkswagen").build();
        modelloGolf = Modello.builder()
            .id(1L)
            .marca(vw)
            .nome("Golf")
            .annoInizio(2020)
            .build();
    }

    // -----------------------------------------------
    // toEntity — happy path
    // -----------------------------------------------

    @Test
    @DisplayName("toEntity — tutti i campi mappati correttamente")
    void toEntity_happyPath() {
        CarDataDTO dto = new CarDataDTO(
            "Volkswagen", "Golf", "2.0 TDI 150 CV DSG",
            2022, "DIESEL", "DCT",
            110, 150, 1968,
            5.8, 7.2, 4.5, null,
            "205/55 R16", null, false,
            35000.0, 250.0, 700.0, 15000, 60000, 8
        );

        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);

        assertThat(entity.getModello()).isEqualTo(modelloGolf);
        assertThat(entity.getNomeMotore()).isEqualTo("2.0 TDI 150 CV DSG");
        assertThat(entity.getAnnoProduzione()).isEqualTo(2022);
        assertThat(entity.getTipoCarburante()).isEqualTo(TipoCarburante.DIESEL);
        assertThat(entity.getTipoCambio()).isEqualTo(TipoCambio.DCT);
        assertThat(entity.getPotenzaKw()).isEqualTo(110);
        assertThat(entity.getPotenzaCv()).isEqualTo(150);
        assertThat(entity.getCilindrataCC()).isEqualTo(1968);
        assertThat(entity.getConsumoMedioLitri100km())
            .isEqualByComparingTo(new BigDecimal("5.8"));
        assertThat(entity.getPrezzoListinoEur())
            .isEqualByComparingTo(new BigDecimal("35000.0"));
        assertThat(entity.getCostoTagliandoBaseEur())
            .isEqualByComparingTo(new BigDecimal("250.0"));
        assertThat(entity.getGruppoAssicurativo()).isEqualTo(8);
        assertThat(entity.getRunFlat()).isFalse();
        assertThat(entity.getConfermatoManualmente()).isFalse();
        assertThat(entity.getDataEstrazione()).isNotNull();
        // id non deve essere settato: lo genera il DB
        assertThat(entity.getId()).isNull();
    }

    @Test
    @DisplayName("toEntity — runFlat null nel DTO → false nell'entità (null-safe)")
    void toEntity_runFlatNull_defaultsFalse() {
        CarDataDTO dto = new CarDataDTO(
            "Toyota", "Yaris", "1.5 Hybrid",
            2023, "IBRIDO_BENZINA", "CVT",
            85, 116, 1490,
            4.0, null, null, null,
            "185/65 R15", null,
            null, // runFlat null
            22000.0, null, null, null, null, 5
        );

        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);
        assertThat(entity.getRunFlat()).isFalse();
    }

    @Test
    @DisplayName("toEntity — tutti i campi nullable a null rimangono null")
    void toEntity_nullableFieldsPreservedNull() {
        CarDataDTO dto = new CarDataDTO(
            "Fiat", "Panda", "1.2 Fire",
            2019, "BENZINA", "MANUALE",
            51, 69, 1242,
            null, null, null, null,  // consumi null
            null, null, false,
            null, null, null, null, null, null  // costi e gruppo null
        );

        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);

        assertThat(entity.getConsumoMedioLitri100km()).isNull();
        assertThat(entity.getPrezzoListinoEur()).isNull();
        assertThat(entity.getCostoTagliandoBaseEur()).isNull();
        assertThat(entity.getGruppoAssicurativo()).isNull();
    }

    // -----------------------------------------------
    // Parsing enum — fuzzy matching
    // -----------------------------------------------

    @Test
    @DisplayName("Enum fuzzy — 'DSG' → DCT")
    void enumFuzzy_DSG_mapsToDCT() {
        CarDataDTO dto = buildMinimalDto("DIESEL", "DSG");
        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);
        assertThat(entity.getTipoCambio()).isEqualTo(TipoCambio.DCT);
    }

    @Test
    @DisplayName("Enum fuzzy — 'PDK' → DCT")
    void enumFuzzy_PDK_mapsToDCT() {
        CarDataDTO dto = buildMinimalDto("BENZINA", "PDK");
        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);
        assertThat(entity.getTipoCambio()).isEqualTo(TipoCambio.DCT);
    }

    @Test
    @DisplayName("Enum fuzzy — 'PETROL' → BENZINA")
    void enumFuzzy_PETROL_mapsToBenzina() {
        CarDataDTO dto = buildMinimalDto("PETROL", "MANUALE");
        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);
        assertThat(entity.getTipoCarburante()).isEqualTo(TipoCarburante.BENZINA);
    }

    @Test
    @DisplayName("Enum fuzzy — 'PHEV' → IBRIDO_PLUGIN")
    void enumFuzzy_PHEV_mapsToIbridoPlugin() {
        CarDataDTO dto = buildMinimalDto("PHEV", "CVT");
        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);
        assertThat(entity.getTipoCarburante()).isEqualTo(TipoCarburante.IBRIDO_PLUGIN);
    }

    @Test
    @DisplayName("Enum fuzzy — 'BEV' → ELETTRICO")
    void enumFuzzy_BEV_mapsToElettrico() {
        CarDataDTO dto = buildMinimalDto("BEV", "SINGOLA_MARCIA");
        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);
        assertThat(entity.getTipoCarburante()).isEqualTo(TipoCarburante.ELETTRICO);
    }

    @Test
    @DisplayName("Enum fuzzy — valore sconosciuto → null (no eccezione)")
    void enumFuzzy_unknown_returnsNull() {
        CarDataDTO dto = buildMinimalDto("VAPORE", "FRIZIONE_CENTRIFUGA");
        Motorizzazione entity = mapper.toEntity(dto, modelloGolf);
        assertThat(entity.getTipoCarburante()).isNull();
        assertThat(entity.getTipoCambio()).isNull();
    }

    // -----------------------------------------------
    // toResponseDTO
    // -----------------------------------------------

    @Test
    @DisplayName("toResponseDTO — tutti i campi principali mappati")
    void toResponseDTO_happyPath() {
        Motorizzazione m = Motorizzazione.builder()
            .id(42L)
            .modello(modelloGolf)
            .nomeMotore("1.4 TSI 150 CV")
            .annoProduzione(2021)
            .tipoCarburante(TipoCarburante.BENZINA)
            .tipoCambio(TipoCambio.DCT)
            .potenzaKw(110)
            .potenzaCv(150)
            .consumoMedioLitri100km(new BigDecimal("6.2"))
            .prezzoListinoEur(new BigDecimal("28000"))
            .gruppoAssicurativo(7)
            .runFlat(false)
            .confermatoManualmente(false)
            .build();

        MotorizzazioneResponseDTO dto = mapper.toResponseDTO(m);

        assertThat(dto.getId()).isEqualTo(42L);
        assertThat(dto.getMarca()).isEqualTo("Volkswagen");
        assertThat(dto.getModello()).isEqualTo("Golf");
        assertThat(dto.getNomeMotore()).isEqualTo("1.4 TSI 150 CV");
        assertThat(dto.getTipoCarburante()).isEqualTo("BENZINA");
        assertThat(dto.getTipoCambio()).isEqualTo("DCT");
        assertThat(dto.getPotenzaKw()).isEqualTo(110);
        assertThat(dto.getConsumoMedioLitri100km())
            .isEqualByComparingTo(new BigDecimal("6.2"));
        assertThat(dto.getGruppoAssicurativo()).isEqualTo(7);
    }

    // -----------------------------------------------
    // CarDataDTO.isValid()
    // -----------------------------------------------

    @Test
    @DisplayName("isValid() — true quando i campi obbligatori sono presenti")
    void isValid_true() {
        CarDataDTO dto = buildMinimalDto("DIESEL", "DCT");
        assertThat(dto.isValid()).isTrue();
    }

    @Test
    @DisplayName("isValid() — false se manca la marca")
    void isValid_false_marcaNull() {
        CarDataDTO dto = new CarDataDTO(
            null, "Golf", "2.0 TDI", 2022,
            "DIESEL", null, 110, null, null,
            null, null, null, null, null, null,
            null, null, null, null, null, null, null
        );
        assertThat(dto.isValid()).isFalse();
    }

    @Test
    @DisplayName("isValid() — false se manca potenzaKw")
    void isValid_false_potenzaKwNull() {
        CarDataDTO dto = new CarDataDTO(
            "VW", "Golf", "2.0 TDI", 2022,
            "DIESEL", null,
            null, // potenzaKw null
            null, null,
            null, null, null, null, null, null,
            null, null, null, null, null, null, null
        );
        assertThat(dto.isValid()).isFalse();
    }

    // -----------------------------------------------
    // HELPER
    // -----------------------------------------------

    private CarDataDTO buildMinimalDto(String tipoCarburante, String tipoCambio) {
        return new CarDataDTO(
            "Volkswagen", "Golf", "2.0 TDI", 2022,
            tipoCarburante, tipoCambio,
            110, 150, 1968,
            5.8, null, null, null,
            "205/55 R16", null, false,
            35000.0, null, null, null, null, null
        );
    }
}
