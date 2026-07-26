-- V33 - Rattachement d'un passage a une campagne (#2355, lot 1) : colonne nullable campaign_id.
--
-- FACULTATIF et non destructif : la colonne nait nulle (aucun passage rattache au depart).
-- ON DELETE SET NULL : supprimer une campagne DETACHE ses passages (leur campaign_id repasse a null),
-- sans jamais effacer de passage. L'index prepare le futur regroupement par campagne (Carte & passages).
ALTER TABLE passage ADD COLUMN campaign_id INTEGER REFERENCES campagne(id) ON DELETE SET NULL;
CREATE INDEX idx_passage_campaign ON passage(campaign_id);
