## Context

`PublicationDepuisLaFiche` porte ce que la fiche sait de la publication d'un point : quatre empêchements,
la mémoire des points publiés, le déclenchement. Sa javadoc écarte un garde sur le verrouillage du carré,
et `dev-docs/api-vigiechiro.md` dit pourquoi. La marque `site_tiers` est une table de présence, posée et
retirée par `ImportSiteDistant.importerOuLier`, que les deux chemins de liaison traversent
(`RapprochementSites`, `RapatriementCarre`).

## Decisions

**La marque existante, pas une lecture neuve.** La mention lit `SiteTiersDao.estTiers`. Écarté :
interroger la plateforme au moment du geste, qui ajouterait une requête à un affichage et rendrait la
mention muette hors connexion.

**Une méthode à part, hors des empêchements.** `mentionDuTiers` ne passe pas par `empechement`. Une
mention rangée parmi les motifs de gris finirait par griser.

**Le mot de l'écran.** « Carré d'un tiers » est déjà le libellé de la case « Participation
opportuniste ». La phrase ne nomme personne : la marque ne garde ni le nom ni l'identifiant du
propriétaire.

**Une information, pas un avertissement.** Style de description sur la carte, style d'indication dans
la modale, sans icône ni couleur d'alerte, comme l'étiquette de proximité (ADR 5839).

**Seulement là où le geste est offert.** Une carte dont le point est publié ou vient de la plateforme,
et la modale en édition, ne portent pas la mention : elle accompagne un geste, elle ne décrit pas le
carré.

**Absente, elle ne réserve aucune place.** Sur la carte le nœud n'est pas créé ; dans la modale il est
masqué et non géré. Les clips filmés de la fiche, joués sur un carré sans marque, ne bougent pas.

## Risks / Trade-offs

L'absence de mention ne veut pas dire « le vôtre ». La marque ne présume jamais un tiers : sans profil
lisible ou sans propriétaire dans la réponse, elle se tait. La page utilisateur le dit.

La marque date de la dernière synchronisation. Un carré qui a changé de main depuis garde l'ancienne
lecture jusqu'à la prochaine.
