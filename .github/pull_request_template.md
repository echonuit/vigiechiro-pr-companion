<!--
  Gabarit de PR. La section « chemins sensibles » vient de l'ADR 2753 : elle ne bloque rien
  techniquement, elle se lit au moment où l'on agit. Cocher « sans objet » est une réponse valide.
-->

## L'issue que cette PR ferme

<!--
  `Closes #N` (ou `Fixes`, ou `Resolves`). Le mot-clé reste ANGLAIS, seul GitHub le lit, et il n'a
  pas d'équivalent français : « Ferme #N » ne ferme rien. La PR fusionne, le train est vert, et
  l'issue reste ouverte sans que rien ne le signale. Vécu sur #4335.

  Quand la PR renvoie à une issue SANS la clore, par exemple un lot dans un EPIC : écrire
  « Rattaché à #N », qui ne prétend rien.
-->

Closes #

## Ce que fait cette PR

<!-- Une phrase dans les termes du problème, pas de la solution. -->

## Vérification

<!-- Ce qui a été mesuré, et comment on saurait que c'est cassé. -->

## Chemins sensibles

Ces trois chemins n'ont **pas de seconde relecture humaine** (ADR 2753) : la case remplace la revue.

- [ ] **sans objet** : cette PR ne touche aucun des chemins ci-dessous
- [ ] `db/migration/**` : la migration a été **rejouée sur une base réelle**, pas seulement compilée
- [ ] `ServiceSauvegarde` / restauration : un **aller-retour complet** a été vérifié
- [ ] `.github/workflows/**` : l'étape modifiée a été **exercée**, et pas seulement sur le chemin
      qu'une PR emprunte - une étape sous `if: push` ou `schedule` peut être fusionnée cassée
