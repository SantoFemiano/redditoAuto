package com.santofem.redditoauto.scraper;

/**
 * Risultato dello scraping con metadati sull'anno effettivamente trovato.
 *
 * Distingue tra:
 * - annoRichiesto: l'anno che l'utente ha cercato
 * - annoEffettivo: l'anno della scheda tecnica trovata (può differire se
 *   la generazione richiesta non esiste e si è usato il fallback)
 * - testo: il testo della scheda tecnica (null se scraping fallito)
 */
public record ScraperResult(
        String testo,
        int annoRichiesto,
        int annoEffettivo,
        int annoFrom,
        int annoTo,
        boolean annoFallback
) {

    /** Factory per risultato con anno corrispondente esatto. */
    public static ScraperResult found(String testo, int anno, int from, int to) {
        return new ScraperResult(testo, anno, anno, from, to, false);
    }

    /** Factory per risultato con fallback su anno diverso. */
    public static ScraperResult foundWithFallback(String testo, int annoRichiesto, int annoEffettivo, int from, int to) {
        return new ScraperResult(testo, annoRichiesto, annoEffettivo, from, to, true);
    }

    /** Factory per scraping fallito (nessun testo trovato). */
    public static ScraperResult empty(int annoRichiesto) {
        return new ScraperResult(null, annoRichiesto, annoRichiesto, 0, 0, false);
    }

    public boolean hasText() {
        return testo != null && !testo.isBlank();
    }

    /** Messaggio di warning da esporre all'utente quando annoFallback=true. */
    public String buildWarningAnno() {
        if (!annoFallback) return null;
        return "Dati non disponibili per il " + annoRichiesto +
               ". Mostro la versione " + annoEffettivo + ".";
    }
}
