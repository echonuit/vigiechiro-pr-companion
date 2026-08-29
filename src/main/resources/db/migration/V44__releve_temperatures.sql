-- V44 - Le releve garde les temperatures, sans quoi il bloque tout envoi (#4768).
--
-- V43 ne stockait du bloc meteo que le vent et la couverture. Or `MeteoDepot` en porte QUATRE
-- composants : les deux temperatures partent vers la plateforme depuis #1844, et la fiche web les
-- affiche.
--
-- Consequence, activee par la garde de #4707 : le releve relu portait toujours des temperatures
-- nulles, la comparaison les voyait divergentes a chaque fois, et rendait ModifieEntreTemps. Sur une
-- nuit dont la participation en porte une, AUCUN envoi ne pouvait plus partir, et l'utilisateur
-- lisait un refus de concurrence sans conflit.
--
-- La lecon tient en une phrase : un releve qui sert de BASE doit porter tout ce que la comparaison
-- regarde. En stocker une partie ne rend pas la garde moins precise, elle la rend toujours vraie.
--
-- V43 n'est pas modifiee : un script publie ne se modifie plus, et les bases deja migrees porteraient
-- une empreinte differente.
ALTER TABLE participation_relevee ADD COLUMN meteo_temperature_debut INTEGER;
ALTER TABLE participation_relevee ADD COLUMN meteo_temperature_fin INTEGER;
