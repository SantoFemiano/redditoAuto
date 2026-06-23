package com.santofem.redditoauto.mapper;

import com.santofem.redditoauto.ai.dto.CarDataDTO;
import com.santofem.redditoauto.controller.dto.MotorizzazioneResponseDTO;
import com.santofem.redditoauto.entity.Modello;
import com.santofem.redditoauto.entity.Motorizzazione;
import com.santofem.redditoauto.entity.enums.TipoCarburante;
import com.santofem.redditoauto.entity.enums.TipoCambio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mapper manuale: CarDataDTO (output AI) → Motorizzazione (entità JPA).
 * Gestisce conversioni di tipo, parsing degli enum e valori null-safe.
 *
 * Non usiamo MapStruct qui per mantenere il controllo esplicito sulla
 * logica di conversione degli enum (es. "Automatico" → AUTOMATICO_TRADIZIONALE).
 */
@Component
@Slf4j
public class CarDataMapper {

    /**
     * Mappa un CarDataDTO estratto dall'AI in una Motorizzazione JPA.
     * Il Modello deve essere già risolto/persistito prima di chiamare questo metodo.
     *
     * @param dto     DTO estratto da Gemini
     * @param modello Entità Modello già persistita
     * @return Motorizzazione pronta per il save (senza id)
     */
    public Motorizzazione toEntity(CarDataDTO dto, Modello modello) {
        return Motorizzazione.builder()
            .modello(modello)
            .nomeMotore(dto.nomeMotore())
            .annoProduzione(dto.annoProduzione())
                .annoFineProduzione(dto.annoFineProduzione())
            .tipoCarburante(parseTipoCarburante(dto.tipoCarburante()))
            .tipoCambio(parseTipoCambio(dto.tipoCambio()))
            .potenzaKw(dto.potenzaKw())
            .potenzaCv(dto.potenzaCv())
            .cilindrataCC(dto.cilindrataCC())
            .consumoMedioLitri100km(toDecimal(dto.consumoMedioLitri100km()))
            .consumoUrbanoLitri100km(toDecimal(dto.consumoUrbanoLitri100km()))
            .consumoExtraurbanoLitri100km(toDecimal(dto.consumoExtraurbanoLitri100km()))
            .autonomiaKmElettrica(dto.autonomiaKmElettrica())
            .misuraPneumaticiAnteriori(dto.misuraPneumaticiAnteriori())
            .misuraPneumaticiPosteriori(dto.misuraPneumaticiPosteriori())
            .runFlat(dto.runFlat() != null ? dto.runFlat() : false)
            .kmDurataPneumatici(dto.kmDurataPneumatici())
            .prezzoListinoEur(toDecimal(dto.prezzoListinoEur()))
            .costoTagliandoBaseEur(toDecimal(dto.costoTagliandoBaseEur()))
            .costoTagliandoMaiorEur(toDecimal(dto.costoTagliandoMaiorEur()))
            .intervalloTagliandoKm(dto.intervalloTagliandoKm())
            .intervalloTagliandoMaiorKm(dto.intervalloTagliandoMaiorKm())
            .gruppoAssicurativo(dto.gruppoAssicurativo())
            .dataEstrazione(LocalDateTime.now())
            .confermatoManualmente(false)
            .build();
    }

    /**
     * Mappa una Motorizzazione JPA nel DTO di risposta REST.
     */
    public MotorizzazioneResponseDTO toResponseDTO(Motorizzazione m) {
        return MotorizzazioneResponseDTO.builder()
            .id(m.getId())
            .marca(m.getModello().getMarca().getNome())
            .modello(m.getModello().getNome())
            .nomeMotore(m.getNomeMotore())
            .annoProduzione(m.getAnnoProduzione())
                    .annoFineProduzione(m.getAnnoFineProduzione())
            .tipoCarburante(m.getTipoCarburante() != null ? m.getTipoCarburante().name() : null)
            .tipoCambio(m.getTipoCambio() != null ? m.getTipoCambio().name() : null)
            .potenzaKw(m.getPotenzaKw() != null ? m.getPotenzaKw().doubleValue() : null)
            .potenzaCv(m.getPotenzaCv())
            .cilindrataCC(m.getCilindrataCC())
            .consumoMedioLitri100km(m.getConsumoMedioLitri100km())
            .consumoUrbanoLitri100km(toDouble(m.getConsumoUrbanoLitri100km()))
            .consumoExtraurbanoLitri100km(toDouble(m.getConsumoExtraurbanoLitri100km()))
            .autonomiaKmElettrica(m.getAutonomiaKmElettrica() != null
                    ? m.getAutonomiaKmElettrica().doubleValue() : null)
            .misuraPneumaticiAnteriori(m.getMisuraPneumaticiAnteriori())
            .misuraPneumaticiPosteriori(m.getMisuraPneumaticiPosteriori())
            .runFlat(m.getRunFlat())
            .kmDurataPneumatici(m.getKmDurataPneumatici())
            .prezzoListinoEur(toDouble(m.getPrezzoListinoEur()))
            .costoTagliandoBaseEur(toDouble(m.getCostoTagliandoBaseEur()))
            .costoTagliandoMaiorEur(toDouble(m.getCostoTagliandoMaiorEur()))
            .intervalloTagliandoKm(m.getIntervalloTagliandoKm())
            .intervalloTagliandoMaiorKm(m.getIntervalloTagliandoMaiorKm())
            .gruppoAssicurativo(m.getGruppoAssicurativo())
            .fonteDati(m.getFonteDati())
            .dataEstrazione(m.getDataEstrazione())
            .confermatoManualmente(m.getConfermatoManualmente())
            .build();
    }

    // -----------------------------------------------
    // CONVERSIONI PRIVATE
    // -----------------------------------------------

    /**
     * Parsing TipoCarburante con fallback tolerante.
     * Gestisce sia i valori enum esatti che varianti testuali comuni.
     */
    private TipoCarburante parseTipoCarburante(String raw) {
        if (raw == null) return null;
        String upper = raw.toUpperCase().trim().replace(" ", "_").replace("-", "_");
        try {
            return TipoCarburante.valueOf(upper);
        } catch (IllegalArgumentException e) {
            if (upper.contains("DIESEL"))  return TipoCarburante.DIESEL;
            if (upper.contains("BENZINA") || upper.contains("PETROL") || upper.contains("GASOLINE"))
                return TipoCarburante.BENZINA;
            if (upper.contains("ELETTR") || upper.contains("ELECTR") || upper.contains("BEV"))
                return TipoCarburante.ELETTRICO;
            if (upper.contains("PLUG") || upper.contains("PHEV"))
                return TipoCarburante.IBRIDO_PLUGIN;
            if (upper.contains("IBRIDO") || upper.contains("HYBRID") || upper.contains("HEV"))
                return TipoCarburante.IBRIDO_BENZINA;
            if (upper.contains("GPL") || upper.contains("LPG"))
                return TipoCarburante.GPL;
            if (upper.contains("METANO") || upper.contains("CNG"))
                return TipoCarburante.METANO;
            log.warn("TipoCarburante non riconosciuto: '{}' → null", raw);
            return null;
        }
    }

    /**
     * Parsing TipoCambio con fallback tolerante.
     */
    private TipoCambio parseTipoCambio(String raw) {
        if (raw == null) return null;
        String upper = raw.toUpperCase().trim().replace(" ", "_").replace("-", "_");
        try {
            return TipoCambio.valueOf(upper);
        } catch (IllegalArgumentException e) {
            if (upper.contains("MANUALE") || upper.contains("MANUAL"))
                return TipoCambio.MANUALE;
            if (upper.contains("DCT") || upper.contains("DSG") || upper.contains("PDK")
                    || upper.contains("S_TRONIC") || upper.contains("DOPPIA"))
                return TipoCambio.DCT;
            if (upper.contains("CVT") || upper.contains("VARIABILE"))
                return TipoCambio.CVT;
            if (upper.contains("SINGOLA") || upper.contains("ELECTRIC") || upper.contains("1_MARCIA"))
                return TipoCambio.SINGOLA_MARCIA;
            if (upper.contains("AUTO") || upper.contains("TORQUE") || upper.contains("TRADIZIONALE"))
                return TipoCambio.AUTOMATICO_TRADIZIONALE;
            log.warn("TipoCambio non riconosciuto: '{}' → null", raw);
            return null;
        }
    }

    /** Converte Double nullable in BigDecimal, null-safe. Evita NullPointerException su BigDecimal.valueOf(null). */
    private BigDecimal toDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    /** Converte BigDecimal nullable in Double, null-safe. */
    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
