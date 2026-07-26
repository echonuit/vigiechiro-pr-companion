-- V34 - Participations opportunistes (#2525) : marquer un passage realise sur le carre d'un tiers.
--
-- Un passage opportuniste est identique en donnees a un passage normal, mais EXEMPTE de R3
-- (fenetre calendaire) et de R4 (intervalle conseille) : on enregistre quand l'occasion se
-- presente, sans contrainte de date ni de frequence.
--
-- Table LATERALE (presence de la ligne = opportuniste), a l'image de V10 passage_equipment :
-- on evite d'alourdir l'entite centrale `passage` (construite en ~60 endroits) d'une 16e colonne,
-- et cela va dans le sens de l'EPIC #2483 (reduire l'arite des constructions). Le cas normal ne
-- coute aucune ligne. Suppression du passage -> suppression en cascade de son marquage.
CREATE TABLE passage_opportuniste (
  passage_id INTEGER PRIMARY KEY REFERENCES passage(id) ON DELETE CASCADE
);
