-- V36 - Especes a ENJEU de conservation (#2353, lot 3 de l'EPIC #2348) : les 19 especes dites
-- PRIORITAIRES du Plan National d'Actions en faveur des Chiropteres 2016-2025.
--
-- SOURCE (donnee de reference, meme exigence de tracabilite que le referentiel taxon de V05) :
--   Plan national d'actions en faveur des Chiropteres 2016-2025, Ministere de l'Environnement,
--   de l'Energie et de la Mer. Liste des especes prioritaires publiee par le PNAC :
--   https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/  (especes surlignees en
--   orange : « les especes dont la conservation est consideree comme prioritaire »)
--   Criteres de selection annonces par le plan : directive Habitats-Faune-Flore, accord EUROBATS,
--   Liste rouge nationale des mammiferes de France metropolitaine, et diagnostic des 34 especes
--   du bilan du 2e PNA. Liste relevee et recoupee sur deux sources le 2026-07-28.
--
-- CLASSIFICATION BINAIRE assumee : le plan distingue prioritaire / non prioritaire, sans gradation.
-- On embarque donc ce que la source dit, sans inventer d'echelle (#2353 « un booleen suffit »).
--
-- Table LATERALE de presence (presence de la ligne = espece prioritaire), meme patron que V34
-- passage_opportuniste et V35 site_tiers : `taxon` est lu par toutes les projections et le record
-- `Taxon` est construit en 16 endroits ; on ne lui ajoute pas une 5e composante pour un booleen
-- (EPIC arite #2483). Les ~300 taxons non prioritaires ne coutent aucune ligne.
--
-- Le plan courant s'acheve en 2025 : sa liste SERA remplacee. Une table laterale se remplace d'un
-- DELETE + INSERT dans une migration suivante, sans toucher au referentiel taxonomique lui-meme.
--
-- Suppression du taxon -> suppression en cascade de son marquage.
CREATE TABLE taxon_prioritaire (
  taxon_code TEXT PRIMARY KEY REFERENCES taxon(code) ON DELETE CASCADE
);

-- Jointure par NOM LATIN, et non par code : c'est le nom latin que la source nomme, et le binome
-- est le pivot stable entre referentiels (TAXREF). Le code Tadarida, lui, est une convention de
-- l'outil de detection. Un nom latin absent du referentiel ne marque rien et ne casse rien : le
-- SELECT ne ramene alors aucune ligne (cf. les deux especes non couvertes, plus bas).
INSERT OR IGNORE INTO taxon_prioritaire (taxon_code)
SELECT code FROM taxon WHERE latin_name IN (
  -- Rhinolophidae
  'Rhinolophus hipposideros',    -- Petit rhinolophe
  'Rhinolophus ferrumequinum',   -- Grand rhinolophe
  'Rhinolophus euryale',         -- Rhinolophe euryale
  'Rhinolophus mehelyi',         -- Rhinolophe de Mehely      (ABSENT du referentiel Tadarida)
  -- Miniopteridae
  'Miniopterus schreibersii',    -- Minioptere de Schreibers
  -- Vespertilionidae
  'Myotis dasycneme',            -- Murin des marais
  'Myotis punicus',              -- Murin du Maghreb
  'Myotis capaccinii',           -- Murin de Capaccini
  'Myotis blythii',              -- Petit murin
  'Myotis escalerai',            -- Murin d'Escalera          (ABSENT du referentiel Tadarida)
  'Myotis bechsteinii',          -- Murin de Bechstein
  'Nyctalus lasiopterus',        -- Grande noctule
  'Nyctalus noctula',            -- Noctule commune
  'Nyctalus leisleri',           -- Noctule de Leisler
  'Pipistrellus pipistrellus',   -- Pipistrelle commune
  'Pipistrellus nathusii',       -- Pipistrelle de Nathusius
  'Eptesicus serotinus',         -- Serotine commune
  'Eptesicus nilssonii',         -- Serotine de Nilsson
  'Plecotus macrobullaris'       -- Oreillard montagnard
);

-- DEUX des 19 especes prioritaires n'ont PAS de code dans le referentiel Tadarida embarque (V05) :
--
--   * Rhinolophus mehelyi (Rhinolophe de Mehely) : espece d'extreme rarete en France, absente de
--     la liste d'especes detectables retenue par Tadarida.
--   * Myotis escalerai (Murin d'Escalera) : le referentiel porte 'Myotis sp. A (''southern''
--     Natterer)' (code MyospA), qui recouvre vraisemblablement ce taxon issu de la scission du
--     complexe nattereri. VRAISEMBLABLEMENT, pas certainement : aucune source ne l'affirme, on ne
--     marque donc PAS MyospA. Marquer sur une deduction taxonomique reviendrait a fabriquer de la
--     donnee de reference.
--
-- Resultat attendu : 17 lignes. C'est un fait a constater, pas un defaut a corriger en forcant la
-- correspondance. Le jour ou le referentiel Tadarida portera ces deux taxons, le meme SELECT les
-- marquera sans etre reecrit.
