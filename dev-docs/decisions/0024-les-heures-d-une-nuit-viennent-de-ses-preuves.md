# ADR 0024 — Les heures d'une nuit viennent de ses preuves ; de l'utilisateur seulement à défaut

- **Statut** : Accepté — 2026-07-18
- **Chantier** : EPIC #1863, suites #1860 / #1878 / #1885 / #1892
- **Vérification** : humaine — que les heures d'une nuit viennent de ses preuves, de l'utilisateur seulement à défaut, est une règle de calcul métier

## Contexte

Un observateur a signalé que trois de ses nuits affichaient `04/07/2026 15:00 → 05/07/2026 15:00` sur le formulaire web, alors que ses fichiers couvraient 21 h - 6 h. Sa remarque décisive : *« la seule correcte est celle que je n'ai pas touchée »*.

La cause s'est révélée être un **cliquet**, et non un décalage. `ParticipationOrpheline.horodatage` appelait `toLocalDateTime()` sur une borne datée d'un décalage, ce qui **jette l'offset au lieu de convertir** : `19:00+00:00` devenait `19:00` local au lieu de `21:00`. L'envoi retraduisant ensuite l'heure locale en UTC, **chaque cycle *reconstruire → envoyer* retranchait deux heures**. Quatre allers-retours ont fait descendre 21:00 à 13:00 puis 15:00 ; une fois début et fin confondus, la règle « la nuit franchit minuit » ajoutait un jour, d'où la fenêtre de 24 heures. La météo a suivi (Open-Meteo est interrogé sur la fenêtre du passage), d'où 35 °C relevés à 6 h du matin.

Corriger la conversion referme le cliquet. Mais cela ne répare **aucune** des nuits déjà abîmées, et ne protège de rien d'analogue : l'application continuerait à traiter comme vérité une valeur **déclarée**, alors qu'elle détient souvent mieux.

Car la machine sait. Les noms de fichiers originaux portent l'horodatage de capture (`_20260704_210000`), les séquences portent le leur. Une nuit qui a des enregistrements **prouve** ses bornes ; les colonnes `start_time` / `end_time`, elles, ne portent qu'une déclaration, susceptible d'avoir dérivé.

## Décision

**Les heures d'une nuit viennent de ce que ses enregistrements prouvent. À défaut de preuve, et seulement à défaut, elles viennent de l'utilisateur.**

Trois conséquences directes.

**1. L'envoi réaligne.** Avant de pousser une participation, les heures sont confrontées à la fenêtre observée (`FenetreObserveeNuit`) ; si les preuves contredisent la déclaration, ce sont les preuves qui partent, et la correction est **persistée**. Sans persistance on aurait déplacé l'incohérence au lieu de la résoudre : l'écran continuerait d'afficher le faux pendant que la plateforme afficherait le juste. La nuit se répare donc **d'elle-même** à chaque aller-retour, au lieu de dépendre du fait qu'aucune conversion n'ait jamais fauté.

**2. Le réalignement se dit.** Il modifie des données de l'utilisateur : le taire reviendrait à corriger sa nuit dans son dos, et à le priver du moyen de contester la correction si elle est fausse. Le compte rendu porte l'**avant et l'après**, pas seulement la nouvelle valeur.

**3. La saisie n'existe que sans preuve.** Une nuit **rapatriée en squelette** (ni fichier ni séquence) n'a rien pour s'attester : jusque-là, ses heures fausses l'étaient à vie, faute de la moindre voie de correction. Elles se saisissent donc. À l'inverse, dès que la nuit se prouve, les champs sont **en lecture seule** : accepter une saisie serait la trahir, puisque le premier envoi la remplacerait.

Une fin **antérieure** au début reste normale (une nuit franchit minuit). Une fin **égale** au début est refusée : elle ne délimite rien, et l'envoi y verrait une nuit de 24 heures - le symptôme exact de #1860.

## Conséquences

- La règle vaut pour les **deux surfaces** : la modale grise les champs en affichant le motif, `statut-passage` annonce `[attestées par les enregistrements]` ou `[déclarées, modifiables]`, et `metadonnees-passage --heure-debut` refuse en le disant. Un contrôle inerte sans explication laisse croire à une panne (patron « Fiche de l'espèce », #789).
- `FenetreObserveeNuit` rend **vide** en dessous de deux horodatages : un enregistrement isolé ne délimite pas une nuit, et en tirer une fenêtre produirait une nuit de 24 h à l'envoi - reproduire le défaut qu'on corrige.
- Les nuits abîmées **avant** ce correctif ne se réparent qu'en repassant dessus. C'est ce qui a rendu `metadonnees-passage --tout` nécessaire (#1861) : sans lot, un correctif d'aller-retour n'atteint jamais les nuits qui en ont besoin.
- La boucle *écriture → plateforme → lecture* est désormais **gardée par un test** qui traverse les deux moitiés de production, éprouvé par mutation ; et par une **sonde live** d'aller-retour ([ADR 0020](0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md), #1862).

## Alternatives écartées

- **Corriger la conversion et s'arrêter là.** C'était la réponse au symptôme. Elle laisse les nuits abîmées abîmées, et fait reposer la justesse sur le fait qu'aucune future conversion ne fautera. La règle d'autorité, elle, rend la cohérence **structurelle** : même une conversion fautive serait rattrapée au prochain envoi.
- **Rendre les heures toujours saisissables.** Simple, et faux : sur une nuit attestée, la saisie serait écrasée au premier envoi. Offrir un champ dont on sait qu'il ne sera pas honoré est pire que ne rien offrir.
- **Réaligner en silence.** Moins de bruit, mais l'utilisateur ne peut plus distinguer sa donnée de la nôtre, ni contester une correction fausse.
- **Déduire la fenêtre d'un enregistrement unique.** Tentant pour couvrir plus de nuits ; produit une fenêtre dégénérée que l'envoi transforme en nuit de 24 h.
- **Recalculer les heures à l'import plutôt qu'à l'envoi.** Ne répare que les nuits importées ensuite, c'est-à-dire pas celles qui posaient problème (rapatriées, sans fichier).
