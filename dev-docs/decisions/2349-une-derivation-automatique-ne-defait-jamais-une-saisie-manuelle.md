# ADR 2349 - Une dérivation automatique ne défait jamais une saisie manuelle

- **Statut** : Accepté - 2026-07-27
- **Chantier** : EPIC #2349 (du passage à la saison), décidé dans #2525
- **Vérification** : certaine - `RapprochementNuitsOpportunistesTest#ne_demarque_jamais_une_saisie_manuelle`

## Contexte

Le chantier #2525 a donné **trois voies** pour déclarer qu'une participation est *opportuniste* (réalisée sur le carré d'un tiers, donc exemptée des règles R3/R4) : une case à l'import, une case dans la modale « Modifier le passage », et une **dérivation automatique** depuis l'API, le champ `site.observateur` comparé au profil connecté.

La dérivation devait être **rétroactive** : à chaque synchronisation, les nuits déjà enregistrées sur un carré de tiers sont marquées, sans que l'observateur ait à les cocher une par une.

Rétroactif suggère « aligner l'état sur la vérité du serveur », donc marquer **et démarquer**. C'est ce qui paraissait cohérent, et c'est un piège.

Les deux voies manuelles existent précisément pour les cas que l'API **ne peut pas** trancher : une participation **non connectée**, dont le site local n'est lié à aucun site distant. Ces sites-là n'apparaissent **jamais** dans `site_tiers`, non parce qu'ils appartiennent à l'observateur, mais parce que la plateforme n'en sait rien. Un démarquage « tout ce qui n'est pas dans `site_tiers` » aurait donc effacé, à chaque synchronisation, exactement les saisies que ces voies servaient à recueillir.

Le défaut aurait été silencieux : aucune erreur, aucun test rouge, juste des cases qui se décochent toutes seules entre deux sessions.

## Décision

**Un marquage dérivé d'une source externe ne retire jamais un marquage que l'utilisateur a pu poser à la main.** La dérivation est à **sens unique** : elle ajoute, elle ne retranche pas.

Concrètement, `RapprochementNuitsOpportunistes` (phase `DEPENDANTE`) marque les nuits des carrés de tiers et **ne démarque rien**, jamais.

## Conséquences

**En bien.** Les saisies manuelles survivent à toute synchronisation. La dérivation reste un **confort** (elle épargne des clics) et non une autorité qui écrase l'observateur.

**En moins bien, et assumé.** Un carré qui vous est **transféré** garde ses anciennes nuits marquées opportunistes. C'est défendable au fond : ces nuits **ont bien été réalisées** alors que le carré appartenait à quelqu'un d'autre ; les démarquer réécrirait l'histoire. Mais c'est un choix, pas une évidence.

**Ce que cela coûterait de faire autrement.** Un démarquage automatique sûr exigerait de distinguer l'**origine** de chaque marquage (dérivé ou manuel), une colonne de plus dans la table latérale, et une règle de préséance à tenir. Non fait : le besoin n'est pas démontré.

## Alternatives écartées

**Démarquer tout ce qui n'est pas dans `site_tiers`.** Écartée : efface les saisies des participations non connectées, en silence. C'est le scénario qui a motivé cette ADR.

**Démarquer uniquement les carrés vus par l'API et redevenus vôtres.** Plus fin, et sans doute correct : mais il faut savoir distinguer « l'API dit que ce carré est à moi » de « l'API ne connaît pas ce carré ». Reporté faute de besoin réel, et parce que la distinction demande l'origine du marquage (voir ci-dessus).

**Ne rien dériver du tout, tout laisser manuel.** Écartée : l'information existe côté plateforme, et la re-saisir à la main sur un historique entier est exactement le travail que le chantier voulait supprimer.

## Journal

- 2026-07-27 : Rédigée à la clôture (passe 3) du chantier #2349. La règle a été posée en implémentant #2525 et documentée dans le code de `RapprochementNuitsOpportunistes` avant d'être promue en ADR.
