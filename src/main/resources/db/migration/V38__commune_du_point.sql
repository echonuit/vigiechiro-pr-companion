-- V38 - Commune d'un point d'ecoute (#2791) : derivee UNE FOIS de ses coordonnees GPS (API Geo,
-- point-dans-polygone officiel) puis persistee, pour donner un nom de lieu opposable aux filtres
-- (« Aix-en-Provence » doit se trouver meme si le site s'appelle « Jardin de Serge »).
--
-- La commune s'attache au POINT, jamais au carre : rien n'empeche un carre de 2 km d'etre a cheval
-- sur plusieurs communes (ni meme sur deux departements). Departement et region ne sont PAS stockes :
-- ils se derivent du code INSEE via la table partagee RegionsFrancaises (ADR 2791, alignee ADR 2351).
--
-- Table LATERALE de valeurs, meme patron que V35 site_tiers et V10 passage_equipment : le record
-- `PointDEcoute` est construit en ~25 endroits en main, on ne lui ajoute pas deux composantes pour
-- un fait derive et recalculable (EPIC arite #2483). L'ABSENCE de ligne dit « commune non resolue »
-- (point sans GPS, hors ligne a la creation, point en mer) : c'est le rattrapage qui la comblera.
--
-- Suppression du point -> suppression en cascade de sa commune.
CREATE TABLE point_commune (
  point_id INTEGER PRIMARY KEY REFERENCES listening_point(id) ON DELETE CASCADE,
  commune_name TEXT NOT NULL,
  commune_insee TEXT NOT NULL
);
