-- V26 - Validation d'expert : l'avis du validateur et le fil de discussion (#1417, EPIC #1154).
--
-- La plateforme distingue TROIS avis sur une même détection :
--
--   * Tadarida PROPOSE      -> taxon_tadarida / prob_tadarida          (V01)
--   * l'observateur CORRIGE -> taxon_observer / observer_certainty     (V01, V21)
--   * le validateur TRANCHE -> taxon_validator / validator_certainty   (ici)
--
-- Le troisieme arrivait deja dans GET /participations/{id}/donnees : le parseur le jetait (spike de
-- #724). L'application affichait donc la correction de l'observateur comme si c'etait le dernier mot,
-- alors qu'un expert du MNHN avait pu la reviser sans qu'on le voie jamais.
--
-- taxon_validator est une FK vers taxon(code), comme les trois autres colonnes de taxon : le code du
-- validateur hors referentiel est auto-enregistre en souche a l'import (meme tolerance que le taxon
-- observateur), plutot que d'etre jete en silence.
--
-- validator_certainty partage le domaine ferme de observer_certainty (SUR | PROBABLE | POSSIBLE,
-- contrat #1203) : cote serveur, c'est la meme enumeration pour les deux roles.
--
-- LECTURE SEULE : PATCH /donnees/{id}/observations/{index} refuse (403) qu'un jeton de role
-- Observateur pose validateur_taxon. Ces deux colonnes sont donc toujours un REFLET du serveur, jamais
-- une saisie locale, et sont rafraichies a chaque import.
ALTER TABLE observation ADD COLUMN taxon_validator TEXT REFERENCES taxon(code);
ALTER TABLE observation ADD COLUMN validator_certainty TEXT;

-- Fil de discussion d'une observation (sous-document `messages` du serveur, meme charge utile).
--
-- Table separee car c'est un 1-N : un fil porte plusieurs messages. Rattachee a l'observation par sa
-- cle technique, avec CASCADE : un re-import remplace le jeu de resultats et ses observations, le fil
-- suit (il vient frais du serveur, comme l'ancrage plateforme).
--
-- rank_in_thread fige l'ordre du serveur (l'ajout se fait par $push : l'ordre du tableau EST l'ordre
-- chronologique) sans dependre de l'ordre d'insertion ni de l'auto-increment.
--
-- author_platform_id est un objectid d'`utilisateurs`, PAS un nom : le serveur ne donne rien d'autre.
-- L'application le compare a l'identifiant de son propre profil pour distinguer « vous » d'« un
-- validateur », sans appel supplementaire.
--
-- posted_at est stocke en ISO-8601 (instant UTC), normalise depuis le RFC 1123 du serveur ; NULL si le
-- serveur ne l'a pas donne ou si le format etait illisible (un fil a moitie date reste lisible).
CREATE TABLE observation_message (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  observation_id     INTEGER NOT NULL REFERENCES observation(id) ON DELETE CASCADE,
  rank_in_thread     INTEGER NOT NULL,
  author_platform_id TEXT,
  body               TEXT NOT NULL,
  posted_at          TEXT
);

CREATE INDEX idx_observation_message_fil ON observation_message(observation_id, rank_in_thread);
