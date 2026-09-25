## Context

Voir `proposal.md` pour le périmètre. `ConstructeurLienEspece` et `PortailVigieChiro` servent déjà l'interface graphique ; les commandes reçoivent ces services par l'injecteur applicatif.

## Goals / Non-Goals

**Goals:** conserver les mêmes URL et réglages sur les deux surfaces.

**Non-Goals:** ouvrir un navigateur, tester la disponibilité du site distant ou créer un rattachement.

## Decisions

Deux commandes nommées désignent les deux ressources. Une commande générique avec une option de type ajouterait des combinaisons d'arguments incompatibles. L'URL seule sur stdout se réutilise dans un script ; un compte rendu supplémentaire n'y apporte rien.

Le code Tadarida est résolu dans le référentiel local, puis converti en espèce identifiée pour le constructeur de lien existant. Le passage utilise la table des liens via le portail existant. Recopier les URL dans la CLI ferait diverger les surfaces lors d'un changement de fournisseur.

## Risks / Trade-offs

- Un site distant peut déplacer sa fiche : les commandes garantissent la construction du lien, pas sa disponibilité réseau.
- Un passage non rattaché ne permet pas de déduire une participation : le refus garde cette absence visible.
