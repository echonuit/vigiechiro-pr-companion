-- V37 - Statut « Recupere » (#2581) : distinguer une nuit RAPATRIEE de Vigie-Chiro d'une nuit que
-- NOUS avons deposee.
--
-- Depuis #2557, la synchronisation rapatrie les nuits que la plateforme connait : elles arrivent avec
-- leurs observations et leur rattachement, mais SANS leur audio. Elles naissaient jusqu'ici avec le
-- statut « Depose », ce qui est exact - la participation existe bien la-bas - mais ce statut recouvrait
-- alors DEUX situations dont les gardes, les gestes et l'affichage ne sont pas les memes.
--
-- LE CRITERE EST OBSERVE, PAS DECLARE. Aucune colonne ne le porte, conformement a l'ADR 0048 (c'est
-- cette meme regle qui a fait retirer `archived_at` en V31). Une nuit est recuperee quand elle reunit
-- deux faits que la base porte deja :
--
--   1. elle est RATTACHEE A UNE PARTICIPATION (vigiechiro_link) : elle vient de la plateforme, elle
--      n'a pas ete importee ici ;
--   2. AUCUN de ses originaux ne porte de frequence d'echantillonnage : aucun WAV n'a jamais ete pose,
--      ce sont des emplacements vides, pas des fichiers.
--
-- Une nuit importee puis deposee reunit le premier fait, JAMAIS le second. C'est ce qui fait tenir la
-- distinction sans colonne supplementaire, et c'est exactement le critere de
-- commun/model/dao/NuitRecupereeDao : la migration le rejoue, elle n'en invente pas un second. Deux
-- criteres qui divergeraient rendraient la base incoherente avec le code qui la lit.
--
-- Reversible : repasser ces lignes a 'Depose' restaure l'etat anterieur, puisque rien d'autre ne change.
UPDATE passage
SET workflow_status = 'Récupéré'
WHERE workflow_status = 'Déposé'
  AND EXISTS (
    SELECT 1 FROM vigiechiro_link vl
    WHERE vl.entite = 'passage' AND vl.ref_locale = CAST(passage.id AS TEXT))
  AND NOT EXISTS (
    SELECT 1 FROM recording_session rs
    JOIN original_recording orig ON orig.session_id = rs.id
    WHERE rs.passage_id = passage.id AND orig.sample_rate_hz IS NOT NULL);
