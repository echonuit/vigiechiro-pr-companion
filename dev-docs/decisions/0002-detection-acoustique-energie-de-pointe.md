# ADR 0002 — Détection acoustique par énergie de pointe, pas par moyenne globale

- **Statut** : Accepté — 2026-07-17
- **Chantier** : EPIC #1653 (réactiver un passage reconstruit depuis les bruts)
- **Vérification** : humaine — le choix de l'énergie de pointe plutôt que la moyenne globale est un algorithme de détection, vérifié par ses tests, pas par un motif

## Contexte

La cascade de vérification (#1309) confirme qu'une tranche contient bien le **cri attendu** en mesurant l'énergie acoustique dans la bande de fréquence de l'espèce, sur la fenêtre temporelle `[début, fin]` de l'observation. `AnalyseAcoustique` **moyennait** cette énergie sur toute la fenêtre.

Or un cri de chiroptère dure quelques **millisecondes**, tandis que la fenêtre d'une observation couvre souvent **plusieurs secondes**. Moyenner **dilue** le cri dans le silence qui l'entoure : l'énergie moyenne tombe sous le seuil, et un cri pourtant bien présent est déclaré absent. C'est ce qui, sur le cas réel du chantier #1653, faisait chuter la concordance à **1 séquence sur 134** - un faux négatif quasi systématique.

## Décision

Mesurer l'énergie **de pointe** sur une **fenêtre glissante courte** (~10 ms, l'ordre de grandeur d'un cri), et non la moyenne sur toute la fenêtre : une tranche « concorde » si l'énergie **maximale** d'une sous-fenêtre courte dépasse le seuil.

## Conséquences

- La concordance acoustique du cas réel passe de **1/134 à 121/134**.
- Le gain **profite à toute la cascade**, pas seulement à l'hydratation des passages reconstruits ([ADR 0001](0001-reactivation-passage-reconstruit-identite-structurelle.md)) : toute vérification qui s'appuie sur la présence des cris devient fiable.
- Coût : un balayage glissant au lieu d'une seule moyenne. Négligeable à l'échelle d'une tranche de 5 s.

## Alternatives écartées

- **Abaisser le seuil de la moyenne globale.** Compenserait la dilution en admettant aussi le bruit de fond : on troquerait des faux négatifs contre des faux positifs.
- **Élargir la bande de fréquence.** Ne traite pas la cause (la dimension **temporelle** de la dilution) et dégraderait la spécificité.
