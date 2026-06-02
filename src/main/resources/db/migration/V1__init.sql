-- =============================================================
-- RedditoAuto - V1 Schema iniziale
-- =============================================================
-- Gerarchia: marca (1) -> modello (N) -> motorizzazione (N)
-- =============================================================

-- -------------------------------------------------------------
-- MARCA
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marca (
    id   BIGSERIAL    PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE
);

COMMENT ON TABLE  marca      IS 'Casa automobilistica, es. Volkswagen, BMW';
COMMENT ON COLUMN marca.nome IS 'Nome univoco della casa, es. Volkswagen';

-- -------------------------------------------------------------
-- MODELLO
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS modello (
    id           BIGSERIAL    PRIMARY KEY,
    marca_id     BIGINT       NOT NULL REFERENCES marca(id) ON DELETE CASCADE,
    nome         VARCHAR(100) NOT NULL,
    anno_inizio  INT,
    anno_fine    INT,          -- NULL = ancora in produzione
    CONSTRAINT uq_modello UNIQUE (marca_id, nome, anno_inizio, anno_fine)
);

COMMENT ON TABLE  modello             IS 'Modello di auto, es. Golf (mk8 2020-), Serie 3';
COMMENT ON COLUMN modello.anno_inizio IS 'Anno inizio generazione, es. 2020';
COMMENT ON COLUMN modello.anno_fine   IS 'Anno fine produzione, NULL se ancora in listino';

CREATE INDEX IF NOT EXISTS idx_modello_marca ON modello(marca_id);

-- -------------------------------------------------------------
-- MOTORIZZAZIONE
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS motorizzazione (
    id                              BIGSERIAL       PRIMARY KEY,
    modello_id                      BIGINT          NOT NULL REFERENCES modello(id) ON DELETE CASCADE,

    -- Identificazione motore
    nome_motore                     VARCHAR(150)    NOT NULL,
    anno_produzione                 INT             NOT NULL,
    tipo_carburante                 VARCHAR(30)     NOT NULL
        CHECK (tipo_carburante IN (
            'BENZINA','DIESEL','GPL','METANO',
            'IBRIDO_BENZINA','IBRIDO_DIESEL','IBRIDO_PLUGIN',
            'ELETTRICO','IDROGENO'
        )),
    tipo_cambio                     VARCHAR(30)
        CHECK (tipo_cambio IN (
            'MANUALE','AUTOMATICO_TRADIZIONALE','DCT','CVT','SINGOLA_MARCIA'
        )),

    -- Dati tecnici
    potenza_kw                      INT             NOT NULL,
    potenza_cv                      INT,
    cilindrata_cc                   INT,

    -- Consumi
    consumo_medio_litri_100km       NUMERIC(5,2),
    consumo_urbano_litri_100km      NUMERIC(5,2),
    consumo_extraurbano_litri_100km NUMERIC(5,2),
    autonomia_km_elettrica          INT,             -- Solo EV/PHEV

    -- Pneumatici
    misura_pneumatici_anteriori     VARCHAR(30),     -- es. '205/55 R16'
    misura_pneumatici_posteriori    VARCHAR(30),
    run_flat                        BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Prezzo
    prezzo_listino_eur              NUMERIC(12,2),

    -- Tagliandi
    costo_tagliando_base_eur        NUMERIC(8,2),
    costo_tagliando_maior_eur       NUMERIC(8,2),
    intervallo_tagliando_km         INT,
    intervallo_tagliando_maior_km   INT,

    -- Assicurazione
    gruppo_assicurativo             INT CHECK (gruppo_assicurativo BETWEEN 1 AND 20),

    -- Metadata AI extraction
    fonte_dati                      VARCHAR(500),
    data_estrazione                 TIMESTAMP,
    confermato_manualmente          BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_motorizzazione UNIQUE (modello_id, nome_motore, anno_produzione)
);

COMMENT ON TABLE  motorizzazione                       IS 'Variante motore specifica, es. Golf 2.0 TDI 150CV DSG 2022';
COMMENT ON COLUMN motorizzazione.potenza_kw            IS 'Potenza in kW - necessaria per calcolo bollo ACI';
COMMENT ON COLUMN motorizzazione.consumo_medio_litri_100km IS 'Consumo ciclo combinato WLTP in l/100km (o kWh/100km per EV)';
COMMENT ON COLUMN motorizzazione.fonte_dati            IS 'URL o fonte testuale usata dall AI extractor';
COMMENT ON COLUMN motorizzazione.confermato_manualmente IS 'Flag di revisione umana dei dati estratti dall AI';

CREATE INDEX IF NOT EXISTS idx_motorizzazione_modello ON motorizzazione(modello_id);
CREATE INDEX IF NOT EXISTS idx_motorizzazione_carburante ON motorizzazione(tipo_carburante);
