-- V22 - Dernier etat connu du traitement serveur d'une participation (#1262, EPIC #1259).
--
-- « Depose » n'est pas la fin : apres le depot et le compute (#1237), le serveur analyse la nuit avec
-- Tadarida, et les observations ne sont recuperables qu'une fois cette analyse FINIE. L'etat de ce
-- calcul appartient au SERVEUR : on ne fait que l'observer, et on n'y touche jamais. Ce n'est donc pas
-- un statut du workflow local (le passage reste « Depose », qui dit « ma part est faite ») : c'est un
-- RELEVE, horodate, de ce que la plateforme disait la derniere fois qu'on le lui a demande.
--
-- Pourquoi le persister plutot que le relire a chaque fois : pour l'afficher hors connexion et a la
-- reouverture de l'application (sans quoi l'ecran reste muet tant qu'on n'a pas rafraichi), et pour
-- servir de socle aux futures vues « resultats a importer ». La verite reste cote serveur : ce cache
-- ne fait autorite sur rien, il se contente de se souvenir.
--
-- Une nuit = une participation : la cle est le passage. Le releve est ecrase a chaque rafraichissement
-- (ON CONFLICT) - on garde le dernier etat connu, pas un historique.
--
-- Les colonnes de dates sont TOUTES optionnelles, car le serveur REMPLACE le bloc traitement a chaque
-- etape au lieu de le completer : la date de planification disparait des que le calcul demarre, et la
-- date de fin n'arrive qu'a la toute fin (y compris en cas d'echec).
--
-- NB : aucun point-virgule dans ces commentaires. Le decoupeur de MigrationSchema coupe les
-- instructions sur CHAQUE point-virgule, commentaires compris - un « ; » ici casse la migration.
CREATE TABLE participation_traitement (
    passage_id         INTEGER PRIMARY KEY REFERENCES passage(id) ON DELETE CASCADE,
    participation_id   TEXT    NOT NULL,  -- objectid de la participation relevee (tracabilite)
    etat               TEXT,              -- PLANIFIE, EN_COURS, FINI, ERREUR ou RETRY
                                          -- NULL = jamais calculee, ou etat inconnu de cette version
                                          -- (lecture tolerante : le serveur peut en introduire un)
    date_planification TEXT,              -- mise en file d'attente (ISO)
    date_debut         TEXT,              -- prise en charge par un worker (ISO)
    date_fin           TEXT,              -- fin de l'analyse (ISO), posee aussi en cas d'echec
    message            TEXT,              -- trace serveur, sur ERREUR et RETRY
    retry              INTEGER,           -- essais consommes cote serveur
    releve_le          TEXT    NOT NULL   -- horodatage ISO de NOTRE lecture (dernier etat connu le...)
);
