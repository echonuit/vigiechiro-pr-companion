-- ============================================================================
-- Matériel du micro déployé pour un passage (métadonnées demandées au dépôt VigieChiro).
--
-- Position (sol / canopée), hauteur de fixation et type de micro sont des informations propres
-- au **déploiement d'une nuit** : elles sont donc rattachées au passage (décision de l'EPIC #543).
-- Plutôt que d'alourdir l'entité centrale `passage` (construite en ~60 endroits), on les isole
-- dans une table **1:1** — `passage_equipment` — à clé primaire = FK vers `passage(id)` : le
-- record `Passage` et son DAO restent inchangés. Toutes les colonnes sont **optionnelles**
-- (un relevé partiel est normal ; le n° de série du détecteur vit déjà dans `recorder`).
--
-- `mic_position` stocke le nom d'énum (`SOL` / `CANOPEE`) ; lecture tolérante côté DAO (valeur
-- inconnue → null). Suppression du passage → suppression en cascade de son matériel.
-- ============================================================================

CREATE TABLE passage_equipment (
  passage_id   INTEGER PRIMARY KEY REFERENCES passage(id) ON DELETE CASCADE,
  mic_position TEXT,
  mic_height_m REAL,
  mic_type     TEXT
);
