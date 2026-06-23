package com.santofem.redditoauto.scraper;

import lombok.Builder;
import lombok.Getter;

/**
 * Risultato arricchito dello scraping multi-sito.
 *
 * Raccoglie:
 * - Il testo tecnico grezzo da passare all'AI extractor
 * - Il prezzo trovato (listino o mercato usato), se disponibile
 * - Hint sull'identità del veicolo (marca, modello, anno) inferiti dalla pagina
 * - Nome del sito sorgente
 * - Flag di successo
 */
@Getter
@Builder
public class MultiSiteScraperResult {

    /** Testo tecnico grezzo (scheda tecnica, consumi, dati motore, ecc.) */
    private final String testo;

    /** Prezzo trovato in euro (listino o mercato). Null se non rilevato. */
    private final Double prezzoEur;

    /** Tipo di prezzo: LISTINO, USATO_MERCATO, SCONOSCIUTO */
    private final TipoPrezzo tipoPrezzo;

    /** Marca inferita dalla pagina (hint per l'AI). Null se non rilevata. */
    private final String marcaHint;

    /** Modello inferito dalla pagina (hint per l'AI). Null se non rilevato. */
    private final String modelloHint;

    /**
     * Anno inferito dalla pagina (hint per l'AI). 0 se non rilevato.
     * @deprecated Usa {@link #annoInizioModello} per maggiore precisione.
     */
    private final int annoHint;

    /** Anno inizio produzione del MODELLO (dal breadcrumb, es. "Juke II (2019 - present)"). 0 se non rilevato. */
    private final int annoInizioModello;

    /** Anno fine produzione del MODELLO (dal breadcrumb). 0 se non rilevato (= ancora in produzione). */
    private final int annoFineModello;

    /** Nome del sito sorgente (es. "auto-data.net", "autoscout24.it") */
    private final String siteNome;

    /** URL originale scrapato */
    private final String url;

    public boolean hasTesto() {
        return testo != null && !testo.isBlank();
    }

    public boolean hasPrezzo() {
        return prezzoEur != null && prezzoEur > 0;
    }

    public enum TipoPrezzo {
        LISTINO,
        USATO_MERCATO,
        SCONOSCIUTO
    }

    /** Factory per risultato fallito */
    public static MultiSiteScraperResult empty(String siteNome, String url) {
        return MultiSiteScraperResult.builder()
                .siteNome(siteNome)
                .url(url)
                .build();
    }
}