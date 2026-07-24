-- V31 - Retrait des colonnes mortes archived_at / originals_purged_at (#2429, suites EPIC #2258).
--
-- Le chantier #2258 a fait de « archive » et « purge » des ETATS OBSERVES, pas des gestes DECLARES :
-- le code qui lisait/ecrivait ces marqueurs a ete retire (#2284 pour archived_at, #2301 pour
-- originals_purged_at, cf. ADR 0048 et patterns.md § Etat observe). La disponibilite reelle de
-- l'audio est desormais calculee sur disque, l'audit signale tout ecart comme un etat, sans marqueur.
--
-- Les colonnes ajoutees par V24 (archived_at) et V25 (originals_purged_at) ne sont donc plus ni lues
-- ni ecrites : ce sont des colonnes mortes. On les retire. Un grep applicatif confirme zero
-- lecture/ecriture, et aucun index, vue ni trigger n'en depend (V24/V25 se limitaient a un ADD COLUMN
-- sans contrainte) : ALTER TABLE DROP COLUMN s'applique donc directement.
--
-- SQLite >= 3.35 (driver 3.53) supporte DROP COLUMN sur une colonne simple. Migration FORWARD : V24 et
-- V25 restent inchangees (les bases existantes les ont deja jouees), V31 defait leur ajout une fois.
--
-- NB : aucun point-virgule dans ces commentaires. Le decoupeur de MigrationSchema coupe les
-- instructions sur CHAQUE point-virgule, commentaires compris - un « ; » ici casse la migration.
ALTER TABLE recording_session DROP COLUMN archived_at;
ALTER TABLE recording_session DROP COLUMN originals_purged_at;
