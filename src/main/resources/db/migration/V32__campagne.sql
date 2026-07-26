-- V32 - Campagne de suivi (#2355, EPIC #2349) : regroupe des passages relevant du meme suivi.
--
-- Objet FACULTATIF : un observateur peut suivre ses points sans jamais creer de campagne. Cette
-- migration ne cree que la table ; le rattachement d'un passage a une campagne (colonne nullable
-- campaign_id sur passage) arrive avec son consommateur (V33, lot 1 PR 2). Aucune notion
-- d'utilisateur : l'application est mono-poste, sans partage de campagne entre plusieurs postes.
CREATE TABLE campagne (
    id      INTEGER PRIMARY KEY AUTOINCREMENT,
    name    TEXT NOT NULL,     -- nom de la campagne (ex. Suivi ENS 2026)
    year    INTEGER NOT NULL,  -- annee de la campagne
    comment TEXT               -- commentaire libre (optionnel)
);
