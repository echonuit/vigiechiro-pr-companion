-- V14 - Observation douteuse : drapeau « à repasser » posé par l'observateur (#160).
--
-- Distinct de « À revoir » (statut NON_TOUCHEE = pas encore vue) : une observation « douteuse » a été
-- écoutée mais laisse un doute, et l'observateur veut y revenir plus tard. Miroir exact du drapeau
-- `is_reference` (booléen 0/1, défaut 0). SQLite sait ajouter une colonne NOT NULL avec DEFAULT sans
-- reconstruire la table (contrairement au retrait d'un NOT NULL en V13).
ALTER TABLE observation ADD COLUMN is_doubtful INTEGER NOT NULL DEFAULT 0;
