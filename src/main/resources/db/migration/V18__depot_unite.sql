-- V18 - Suivi du depot VigieChiro par unite televersee (#981, EPIC #941 phase 3).
--
-- Un depot interrompu (coupure reseau, fermeture de l'application) doit etre REPRENABLE sans
-- re-televerser ce qui est deja en ligne : on persiste l'avancement unite par unite (une archive ZIP
-- ou une sequence WAV), au fil de l'eau. L'etat local est la source primaire d'idempotence (reponse
-- 5.3 du brief M-Lot) ; une reconciliation serveur pourra s'y ajouter plus tard. Le statut du passage
-- en decoule (#980/#982) : toutes les unites 'depose' <=> passage « Depose » ; sinon « Depot en
-- cours » des la premiere unite entamee.
--
-- L'unicite (passage_id, identifiant_unite) rend la pose du plan idempotente : re-entamer un depot
-- retrouve les memes lignes et ne re-televerse que les statuts 'a_deposer' / 'echec' / 'en_cours'
-- (une unite laissee 'en_cours' par une interruption est a re-tenter, son televersement n'a jamais
-- ete confirme).
CREATE TABLE depot_unite (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    passage_id         INTEGER NOT NULL REFERENCES passage(id) ON DELETE CASCADE,
    identifiant_unite  TEXT    NOT NULL,  -- nom du fichier televerse (Car...-1.zip, sequence .wav)
    type               TEXT    NOT NULL,  -- 'zip' | 'wav'
    statut             TEXT    NOT NULL,  -- 'a_deposer' | 'en_cours' | 'depose' | 'echec'
    fichier_id_distant TEXT,              -- objectid du fichier cree cote plateforme (une fois connu)
    message_erreur     TEXT,              -- raison du dernier echec (statut 'echec')
    maj_le             TEXT    NOT NULL,  -- horodatage ISO de la derniere mise a jour
    UNIQUE (passage_id, identifiant_unite)
);

-- Lectures nominales : les unites d'un passage (rehydratation M-Lot) et les restantes par statut.
CREATE INDEX idx_depot_unite_passage ON depot_unite (passage_id, statut);
