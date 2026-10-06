## Why

Depuis les lots 31 et 38 de #5596, une date ou une heure de nuit montrée à l'utilisateur se lit en français, et
#5969 l'a écrit en exigences. Deux endroits y échappent encore, chacun pour une raison connue :

- `statut-passage` recopie les heures de la base, « 20:25:00 → 07:47:00 », alors que la fiche d'un passage et la
  Qualification disent « 20:25 -> 07:47 » (#5974, trouvé en écrivant les exigences de #5969) ;
- le détail d'un résultat de la recherche globale affiche « 2026-06-21 », et le lot 38 ne l'a pas changé parce que
  la recherche ne trouve une nuit que sous cette forme : afficher l'une et chercher l'autre ferait taper à
  l'utilisateur ce qu'il lit sans rien trouver (#5949).

Le porteur a demandé le 6 octobre 2026 de traiter les deux (#5996).

## What Changes

- La ligne « Nuit » de `statut-passage` dit les heures sans leurs secondes. Sa sortie `--json` ne change pas.
- Le détail d'un résultat de recherche, de passage comme d'espèce, dit la date en français.
- La recherche trouve une nuit par la forme française de sa date, et continue de la trouver par la forme ISO.

## Capabilities

### New Capabilities

- `recherche/recherche-globale` : ce que le détail d'un résultat dit d'une nuit, et sous quelles formes une date
  se cherche.

### Modified Capabilities

- `passage/fiche-d-un-passage` : la commande `statut-passage` dit la plage horaire comme la fiche.

## Impact

`StatutPassage`, `ServiceRechercheGlobale` et leurs tests. Aucune donnée ne change : la base garde ses formes.
