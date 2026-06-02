-- V3: Pulizia record spazzatura inseriti prima del fix dei placeholder Gemini.
-- Rimuove motorizzazioni, modelli e marche con nome={marca}, {modello}, {motore}
-- che sono stati inseriti erroneamente nelle run precedenti al fix.

-- 1. Elimina motorizzazioni con nome_motore placeholder
DELETE FROM motorizzazione
WHERE nome_motore LIKE '{%}'
   OR nome_motore = 'null'
   OR modello_id IN (
       SELECT m.id FROM modello m
       WHERE m.nome LIKE '{%}' OR m.nome = 'null'
   );

-- 2. Elimina modelli placeholder (ora senza motorizzazioni)
DELETE FROM modello
WHERE nome LIKE '{%}'
   OR nome = 'null';

-- 3. Elimina marche placeholder rimaste senza modelli
DELETE FROM marca
WHERE nome LIKE '{%}'
   OR nome = 'null'
   OR id NOT IN (SELECT DISTINCT marca_id FROM modello);
