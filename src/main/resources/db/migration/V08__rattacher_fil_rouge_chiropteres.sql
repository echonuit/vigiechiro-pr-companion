-- ============================================================================
-- Normalisation du « taxon parent » des 4 espèces fil rouge (vues centrées observation).
-- V02 a semé Pippip/Nyclei/Tadten/Rhihip sous leur GENRE (Pipistrellus, Nyctalus, Tadarida,
-- Rhinolophus), alors que le référentiel officiel (V05) et le portail VigieChiro rattachent toutes
-- les chauves-souris à la CATÉGORIE « Chiroptères ». Sans cette reprise, la colonne « Taxon parent »
-- des vues d'observation afficherait un genre pour ces 4 espèces et « Chiroptères » pour les ~34
-- autres chiroptères du référentiel : incohérence visible pour l'utilisateur. On les re-pointe donc
-- vers « Chiroptères » pour une liste homogène.
--
-- Les groupes de genre (Pipistrellus…) restent en base (encore référencés par des tests, sans coût)
-- mais deviennent vestigiaux. Idempotent : ré-exécuter l'UPDATE ne change rien.
-- ============================================================================

-- Défensif (comme V06) : garantir la présence de la catégorie cible avant de la référencer, pour que
-- la sous-requête ci-dessous ne renvoie jamais NULL (group_id est NOT NULL).
CREATE UNIQUE INDEX IF NOT EXISTS ux_taxonomic_group_name ON taxonomic_group(name);
INSERT OR IGNORE INTO taxonomic_group (level, name) VALUES ('Catégorie', 'Chiroptères');

UPDATE taxon
   SET group_id = (SELECT id FROM taxonomic_group WHERE name = 'Chiroptères')
 WHERE code IN ('Pippip', 'Nyclei', 'Tadten', 'Rhihip');
