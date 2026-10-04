## Context

`ServiceLot.sourceDepotParDefaut` décide de ce qui part : le mode d'un dépôt entamé, sinon le réglage (#5677). L'écran, lui, n'en sait rien. `LotViewModel` connaît seulement `depotAutomatiqueDisponible` (l'application peut téléverser), et `EtapesDepot` calcule toujours quatre étapes. Les titres « 2. », « 3. » et « 4. » sont écrits en dur dans `Lot.fxml`, `EtapeTeleversement.fxml` et `EtapeDeposerUI`. `CompteRenduChiffreDepot` écrit « archive(s) » dans une dizaine de phrases, alors que `TableSuiviDepot` nomme déjà « Archive » ou « Séquence » d'après le type de l'unité.

Cette carte a été retouchée par #5782, #5788 et #5799. Le scénario filmé S4-47 la lit par ses identifiants, sur une nuit dont une archive est semée déposée.

## Goals / Non-Goals

**Goals:**
- En forme WAV et connecté, l'écran n'offre ni ne nomme rien qui tienne aux archives.
- En forme ZIP et hors connexion, l'écran reste celui d'aujourd'hui.
- Une seule règle décide de la forme, partagée avec ce qui part réellement.

**Non-Goals:**
- Changer le moteur de dépôt, la ligne de commande, ou lever le blocage d'une relance.
- Offrir le choix de la forme par nuit (#2029).
- Offrir un dépôt manuel de séquences WAV.
- Renommer un identifiant `fx:id`.

## Decisions

**D1. La forme se demande à `ServiceLot`.** `ServiceLot.formeDuDepot(idPassage)` rend le mode d'un dépôt entamé, sinon le réglage : c'est la règle de `sourceDepotParDefaut`, qui s'appuie désormais sur elle. Écarté : lire le réglage dans le ViewModel, qui referait la règle de #5677 à un second endroit et la raterait pour un dépôt entamé.

**D2. Le fil d'étapes est la source, et la vue le lit à un seul endroit.** `SuiviEtapesLot` demande la forme à chaque recalcul et décide : l'étape des archives est offerte en forme ZIP, ou quand le dépôt automatique est indisponible. Le fil d'étapes compte alors trois puces ou quatre. La vue en dérive tout le reste par `EtapeDesArchives.offerte` : la carte de l'étape, les éléments de dépôt manuel, les numéros et les infobulles. Écarté en cours de réalisation : une propriété de plus sur `LotViewModel`, qui lui faisait franchir le plafond `GodClass` du portail qualité (ADR 4682, cliquet à zéro).

**D3. `EtapesDepot` calcule trois ou quatre étapes.** Il reçoit `etapeArchivesOfferte` et omet « Générer les archives » quand elle est fausse ; les rangs se décalent. Les titres des cartes sont liés à la présence de l'étape des archives, au lieu d'être écrits dans le FXML.

**D4. Le dépôt manuel suit l'étape des archives.** Connecté en forme WAV, l'étape de téléversement perd la mention du dépôt manuel, le chemin du dossier, « Copier » et « Ouvrir le dossier (dépôt manuel) » : ils pointent vers des archives que rien ne produit. Qui veut déposer des archives à la main choisit le ZIP dans les réglages, et l'étape revient.

**D5. Le nom de l'unité vient du plan.** Une petite énumération dans `lot.viewmodel` (séquence, archive, fichier) se déduit des types des unités du plan ; `CompteRenduChiffreDepot` et les phrases de `FormatsLot` la reçoivent. Sans plan, elle se déduit de la forme du dépôt.

**D6. Un contenu refusé en WAV ne nomme aucun geste.** Pour des archives, « régénérez les archives » est un geste vérifié (#3946). Pour des séquences, aucun geste de l'écran ne change leur contenu : la phrase renvoie à la table, où la cause est détaillée. C'est l'ADR 3854 : ne nommer que ce qui s'applique.

**D7. Les textes**, à valider par le porteur avant le code. En forme ZIP et hors connexion, tous les textes actuels restent.

| Où, en forme WAV et connecté | Texte |
|---|---|
| Titres | « 1. Vérifier et préparer le dépôt », « 2. Téléverser sur Vigie-Chiro », « 3. Lancer la participation » (ou « 3. Marquer le passage déposé ») |
| Fil d'étapes | « 1 · Préparer », « 2 · Téléverser », « 3 · Marquer déposé » |
| Étape de téléversement, consigne | « Téléversez la nuit directement sur Vigie-Chiro : les séquences transformées partent une à une, au format attendu par la plateforme. » |
| Infobulle du bouton « Téléverser » grisé | « Téléversement possible une fois le dépôt préparé (statut « Prêt à déposer »), et hors envoi en cours. » |
| Infobulle d'« Annuler » | « Termine les séquences en cours d'envoi, puis s'arrête. Le passage reste « Dépôt en cours » : reprendre le dépôt ne renverra que les fichiers manquants. » |
| Compte rendu | les phrases actuelles, « séquence(s) » remplaçant « archive(s) » : « Devenir des 412 séquences du plan », « 3 séquence(s) ne sont pas en ligne : « Reprendre le dépôt » ne renverra que celles-là. », « Toutes les séquences de la nuit sont sur Vigie-Chiro. Il reste à lancer la participation pour que la plateforme les analyse. » |
| Compte rendu, contenu refusé | « 2 séquence(s) ont été refusées par Vigie-Chiro : les renvoyer telles quelles serait refusé de même. Le détail par séquence est dans la table. » |
| Infobulle du bouton de lancement grisé après analyse | « Cette nuit a déjà été analysée par Vigie-Chiro. La relancer effacerait ses observations côté serveur avant de les recalculer. Importez-les plutôt dans « Sons & validation ». Pour forcer malgré tout, après un échec, par exemple : lancer-traitement-vigiechiro --forcer. » |

## Risks / Trade-offs

- **Des tests lisent les titres numérotés** (« 4. Lancer la participation », #5676) → ils montent l'écran avec un service bouchon ; la forme par défaut d'un bouchon muet sera tenue pour le ZIP dans ces bancs, et un banc neuf porte la forme WAV. Le compte de tests touchés se mesure avant de coder.
- **S4-47 n'est joué par aucune CI de demande** → sa nuit est en forme ZIP, son écran ne change pas ; la session qui le porte le rejoue sur la branche avant la fusion.
- **L'étape disparaît quand on change le réglage, écran ouvert** → la forme se relit au rafraîchissement, pas en direct. Un observateur qui change le réglage rouvre l'écran.
- **`lot/lancement-de-la-participation` écrit « 4. » dans un delta non archivé** → l'archivage de #5596 devra reporter la règle de numérotation ; noté dans la proposition.
- **Trois demandes ont déjà retouché cette carte** → les captures du lot se rendent dans les deux formes, et se relisent une par une.

## Open Questions

- Les textes de D7 sont à valider.
- D4 retire le dépôt manuel de l'étape de téléversement en forme WAV connecté. C'est la conséquence de la disparition de l'étape des archives, mais le porteur ne l'a pas dite : à confirmer.
