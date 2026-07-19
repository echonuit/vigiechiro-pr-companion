-- V30 - Empreinte du lot source au moment ou le plan de depot est pose (#1993, EPIC #1991).
--
-- Le plan de depot (table depot_unite, V18) nomme les archives AVANT qu'elles existent : la
-- partition des sequences en archives est deterministe (PlanificateurArchives, glouton a ordre
-- preserve), donc l'archive N peut etre regeneree a l'identique tant que la liste source n'a pas
-- change. C'est ce qui rendra possible de liberer une archive des qu'elle est en ligne (#1995) et
-- de la reproduire a la reprise (#1994).
--
-- Mais ce determinisme ne tient QUE a liste source inchangee. Si des sequences sont ajoutees,
-- retirees ou re-transformees entre deux reprises, la partition se decale : l'archive N porterait
-- le meme nom pour un contenu different, et serait re-televersee par-dessus celle deja en ligne
-- sans que rien ne le signale. On persiste donc une empreinte de la liste source (noms et tailles,
-- dans l'ordre) au moment ou le plan est pose ; la reprise la recalcule et compare.
--
-- Une ligne par passage : le plan de depot est un fait du passage, pas de chaque unite.
CREATE TABLE depot_plan (
    passage_id INTEGER PRIMARY KEY REFERENCES passage(id) ON DELETE CASCADE,
    empreinte  TEXT NOT NULL,  -- empreinte de la liste source ordonnee (voir EmpreinteLot)
    pose_le    TEXT NOT NULL   -- horodatage ISO de la pose du plan
);
