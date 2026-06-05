-- ============================================================
-- V5 — AUDIT COLUMNS + UPDATED_AT TRIGGER
-- Flyway / PostgreSQL
-- Aggiunge created_at e updated_at a marca, modello, motorizzazione
-- e installa la funzione trigger set_updated_at() che aggiorna
-- automaticamente updated_at ad ogni UPDATE, simulando il
-- comportamento MySQL ON UPDATE CURRENT_TIMESTAMP.
-- ============================================================

-- 1) COLONNE AUDIT
-- IF NOT EXISTS evita errori su ambienti già aggiornati parzialmente.

ALTER TABLE marca
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE modello
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE motorizzazione
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 2) FUNZIONE TRIGGER CONDIVISA
-- Unica funzione riusata da tutti i trigger delle tabelle.
-- RETURNS TRIGGER → obbligatorio per BEFORE UPDATE.
-- NEW.updated_at = CURRENT_TIMESTAMP → aggiorna il valore prima del commit.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 3) TRIGGER PER TABELLA marca

DROP TRIGGER IF EXISTS trg_marca_updated_at ON marca;
CREATE TRIGGER trg_marca_updated_at
    BEFORE UPDATE ON marca
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- 4) TRIGGER PER TABELLA modello

DROP TRIGGER IF EXISTS trg_modello_updated_at ON modello;
CREATE TRIGGER trg_modello_updated_at
    BEFORE UPDATE ON modello
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

-- 5) TRIGGER PER TABELLA motorizzazione

DROP TRIGGER IF EXISTS trg_motorizzazione_updated_at ON motorizzazione;
CREATE TRIGGER trg_motorizzazione_updated_at
    BEFORE UPDATE ON motorizzazione
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
