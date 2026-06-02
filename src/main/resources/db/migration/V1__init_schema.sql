-- ============================================================
-- RedditoAuto - Migration V1: Schema iniziale
-- ============================================================

-- ========================
-- MARCA
-- ========================
CREATE TABLE IF NOT EXISTS marca (
    id   BIGSERIAL    PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE
);

-- ========================
-- MODELLO
-- ========================
CREATE TABLE IF NOT EXISTS modello (
    id           BIGSERIAL    PRIMARY KEY,
    marca_id     BIGINT       NOT NULL REFERENCES marca(id) ON DELETE CASCADE,
    nome         VARCHAR(100) NOT NULL,
    anno_inizio  INTEGER,
    anno_fine    INTEGER,
    CONSTRAINT uq_modello UNIQUE (marca_id, nome, anno_inizio, anno_fine)
);

CREATE INDEX IF NOT EXISTS idx_modello_marca ON modello(marca_id);

-- ========================
-- MOTORIZZAZIONE
-- ========================
CREATE TABLE IF NOT EXISTS motorizzazione (
    id                              BIGSERIAL       PRIMARY KEY,
    modello_id                      BIGINT          NOT NULL REFERENCES modello(id) ON DELETE CASCADE,

    -- Identificazione motore
    nome_motore                     VARCHAR(150)    NOT NULL,
    anno_produzione                 INTEGER         NOT NULL,
    tipo_carburante                 VARCHAR(20)     NOT NULL,
    tipo_cambio                     VARCHAR(30),

    -- Dati tecnici
    potenza_kw                      INTEGER         NOT NULL,
    potenza_cv                      INTEGER,
    cilindrata_cc                   INTEGER,

    -- Consumi
    consumo_medio_litri_100km       NUMERIC(5,2),
    consumo_urbano_litri_100km      NUMERIC(5,2),
    consumo_extraurbano_litri_100km NUMERIC(5,2),
    autonomia_km_elettrica          INTEGER,

    -- Pneumatici
    misura_pneumatici_anteriori     VARCHAR(30),
    misura_pneumatici_posteriori    VARCHAR(30),
    run_flat                        BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Prezzo
    prezzo_listino_eur              NUMERIC(12,2),

    -- Tagliandi
    costo_tagliando_base_eur        NUMERIC(8,2),
    costo_tagliando_maior_eur       NUMERIC(8,2),
    intervallo_tagliando_km         INTEGER,
    intervallo_tagliando_maior_km   INTEGER,

    -- Assicurazione
    gruppo_assicurativo             INTEGER,

    -- Metadata
    fonte_dati                      VARCHAR(500),
    data_estrazione                 TIMESTAMP,
    confermato_manualmente          BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_motorizzazione UNIQUE (modello_id, nome_motore, anno_produzione)
);

CREATE INDEX IF NOT EXISTS idx_motorizzazione_modello ON motorizzazione(modello_id);
CREATE INDEX IF NOT EXISTS idx_motorizzazione_carburante ON motorizzazione(tipo_carburante);
