<!--
  Gabarit de PR. La section « chemins sensibles » vient de l'ADR 2753 : elle ne bloque rien
  techniquement, elle se lit au moment où l'on agit. Cocher « sans objet » est une réponse valide.
-->

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
