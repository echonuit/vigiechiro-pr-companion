-- V13 - Observation manuelle : autoriser une observation SANS identification Tadarida.
--
-- Une séquence « non identifiée » (présente sur disque mais absente du CSV Tadarida) peut désormais être
-- validée à la main : on crée alors une observation portant le taxon de l'observateur, mais SANS taxon
-- Tadarida ni jeu de résultats Tadarida. Les deux verrous historiques (`taxon_tadarida NOT NULL` et
-- `results_id NOT NULL`) sont donc levés.
--
-- SQLite ne sait pas retirer un NOT NULL par ALTER : on reconstruit la table `observation` à l'identique
-- (schéma V01 + renommage V07 `median_freq_khz`), en rendant `taxon_tadarida` et `results_id` nullables,
-- puis on recopie les données et on recrée les index (V01 + V03). Aucune table ne référence `observation`
-- (pas de FK entrante) : la reconstruction est sûre.

CREATE TABLE observation_v13 (
  id                   INTEGER PRIMARY KEY AUTOINCREMENT,
  sequence_id          INTEGER NOT NULL REFERENCES listening_sequence(id) ON DELETE CASCADE,
  start_time_s         REAL,
  end_time_s           REAL,
  median_freq_khz      INTEGER,
  taxon_tadarida       TEXT REFERENCES taxon(code),
  prob_tadarida        REAL,
  taxon_other_tadarida TEXT REFERENCES taxon(code),
  taxon_observer       TEXT REFERENCES taxon(code),
  prob_observer        REAL,
  user_comment         TEXT,
  is_reference         INTEGER NOT NULL DEFAULT 0,
  validation_mode      TEXT,
  results_id           INTEGER REFERENCES identification_results(id) ON DELETE CASCADE
);

INSERT INTO observation_v13
  (id, sequence_id, start_time_s, end_time_s, median_freq_khz, taxon_tadarida, prob_tadarida,
   taxon_other_tadarida, taxon_observer, prob_observer, user_comment, is_reference, validation_mode, results_id)
SELECT
   id, sequence_id, start_time_s, end_time_s, median_freq_khz, taxon_tadarida, prob_tadarida,
   taxon_other_tadarida, taxon_observer, prob_observer, user_comment, is_reference, validation_mode, results_id
FROM observation;

DROP TABLE observation;

ALTER TABLE observation_v13 RENAME TO observation;

CREATE INDEX idx_obs_sequence ON observation(sequence_id);
CREATE INDEX idx_obs_taxon    ON observation(taxon_tadarida);
CREATE INDEX idx_obs_results  ON observation(results_id);
