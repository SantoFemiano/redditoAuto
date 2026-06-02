package com.santofem.redditoauto.mapper;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.entity.Marca;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.entity.enums.TipoCambio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CarDataMapper — Unit Tests")
class CarDataMapperTest {

    private final CarDataMapper mapper = new CarDataMapper();

    private final Modello modelloFake = Modello.builder()
        .id(1L)
        .marca(Marca.builder().id(1L).nome("Volkswagen").build())
        .nome("Golf")
        .build();

    @Test
    @DisplayName("Mapping completo: tutti i campi popolati correttamente")
    void mapping_completo() {
        CarDataDTO dto = new CarDataDTO(
            "Volkswagen", "Golf", "2.0 TDI 150 CV DSG", 2022,
            "DIESEL", "DCT",
            110, 150, 1968,
            5.8, 7.2, 4.5, null,
            "205/55 R16", null, false,
            35000.0, 250.0, 700.0, 15000, 60000, 8
        );

        Motorizzazione m = mapper.toEntity(dto, modelloFake);

        assertThat(m.getNomeMotore()).isEqualTo("2.0 TDI 150 CV DSG");
        assertThat(m.getTipoCarburante()).isEqualTo(TipoCarburante.DIESEL);
        assertThat(m.getTipoCambio()).isEqualTo(TipoCambio.DCT);
        assertThat(m.getPotenzaKw()).isEqualTo(110);
        assertThat(m.getConsumoMedioLitri100km()).isEqualByComparingTo(new BigDecimal("5.8"));
        assertThat(m.getPrezzoListinoEur()).isEqualByComparingTo(new BigDecimal("35000.0"));
        assertThat(m.getRunFlat()).isFalse();
        assertThat(m.getDataEstrazione()).isNotNull();
        assertThat(m.getConfermatoManualmente()).isFalse();
    }

    @Test
    @DisplayName("Mapping con campi null: nessuna NullPointerException")
    void mapping_campi_null() {
        CarDataDTO dto = new CarDataDTO(
            "BMW", "Serie 3", "320d", 2021,
            "DIESEL", null,
            110, null, null,
            null, null, null, null,
            null, null, null,
            null, null, null, null, null, null
        );

        assertThatCode(() -> mapper.toEntity(dto, modelloFake))
            .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "TipoCarburante: '{0}' → {1}")
    @CsvSource({
        "DIESEL,    DIESEL",
        "diesel,    DIESEL",
        "Benzina,   BENZINA",
        "PETROL,    BENZINA",
        "GASOLINE,  BENZINA",
        "BEV,       ELETTRICO",
        "Elettrico, ELETTRICO",
        "PHEV,      IBRIDO_PLUGIN",
        "PLUGIN,    IBRIDO_PLUGIN",
        "HEV,       IBRIDO_BENZINA",
        "GPL,       GPL",
        "LPG,       GPL",
        "CNG,       METANO"
    })
    @DisplayName("Parsing TipoCarburante — varianti testuali")
    void parsing_carburante(String input, TipoCarburante expected) {
        CarDataDTO dto = new CarDataDTO(
            "X", "Y", "Z", 2022, input, null,
            100, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
        Motorizzazione m = mapper.toEntity(dto, modelloFake);
        assertThat(m.getTipoCarburante()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "TipoCambio: '{0}' → {1}")
    @CsvSource({
        "MANUALE,              MANUALE",
        "Manual,               MANUALE",
        "MT,                   MANUALE",
        "DSG,                  DCT",
        "PDK,                  DCT",
        "EDC,                  DCT",
        "CVT,                  CVT",
        "Automatico,           AUTOMATICO_TRADIZIONALE",
        "AUTOMATICO_TRADIZIONALE, AUTOMATICO_TRADIZIONALE"
    })
    @DisplayName("Parsing TipoCambio — varianti testuali")
    void parsing_cambio(String input, TipoCambio expected) {
        CarDataDTO dto = new CarDataDTO(
            "X", "Y", "Z", 2022, "BENZINA", input,
            100, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
        Motorizzazione m = mapper.toEntity(dto, modelloFake);
        assertThat(m.getTipoCambio()).isEqualTo(expected);
    }

    // helper per assertThatCode
    private static org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable>
            assertThatCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.assertThatCode(callable);
    }
}
