-- V35 - Carres appartenant a un TIERS (#2525) : derivation automatique depuis l'API VigieChiro.
--
-- Le champ `observateur` d'un site distant (GET /moi/participations -> site embarque) porte le
-- proprietaire du carre. Compare a l'id du profil connecte, il dit si le carre est celui d'un autre
-- observateur : les nuits qu'on y realise sont alors des participations OPPORTUNISTES (exemptees de
-- R3/R4), et le carre sort du solde de saison (qui ne parle que de ses propres carres).
--
-- Table LATERALE de presence (presence de la ligne = carre d'un tiers), meme patron que V34
-- passage_opportuniste et V10 passage_equipment : le record `Site` est construit en ~81 endroits
-- (15 main + 66 test), on ne lui ajoute pas une 8e composante pour un booleen (EPIC arite #2483).
-- Le cas courant (son propre carre) ne coute aucune ligne.
--
-- Suppression du site -> suppression en cascade de son marquage.
CREATE TABLE site_tiers (
  site_id INTEGER PRIMARY KEY REFERENCES monitoring_site(id) ON DELETE CASCADE
);
