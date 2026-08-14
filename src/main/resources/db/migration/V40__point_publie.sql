-- V40 - Points d'ecoute POUSSES vers Vigie-Chiro (#3458).
--
-- Un point saisi dans Companion n'existe pas sur la plateforme tant qu'on ne l'y a pas publie
-- (PUT /sites/{id}/localites). Sans memoire de cette publication, l'ecran ne saurait jamais ou on en
-- est : il reproposerait le geste indefiniment, et republier rendrait « deja present » sans rien
-- ecrire - benin, mais l'utilisateur reclique pour rien et n'apprend rien.
--
-- ATTENTION - on ne REUTILISE PAS le drapeau `synchronise` (V.. , #1738), et c'est deliberé. Il veut
-- dire « RAPATRIE de la plateforme », et la fiche site s'en sert pour MASQUER les points rapatries
-- inutilises. Un point qu'on vient de creer pour s'en servir disparaitrait donc de la fiche au moment
-- meme ou on le publie, et sa provenance serait fausse. Deux faits distincts, deux endroits.
--
-- Table LATERALE de presence (presence de la ligne = point publie), meme patron que V35 site_tiers,
-- V34 passage_opportuniste et V10 passage_equipment : le record `PointDEcoute` porte deja sept
-- composantes, on ne lui en ajoute pas une huitieme pour un booleen (EPIC arite #2483). Le cas
-- courant - un point rapatrie, donc jamais publie par nous - ne coute aucune ligne.
--
-- Suppression du point -> suppression en cascade de son marquage.
CREATE TABLE point_publie (
  point_id INTEGER PRIMARY KEY REFERENCES listening_point(id) ON DELETE CASCADE
);
