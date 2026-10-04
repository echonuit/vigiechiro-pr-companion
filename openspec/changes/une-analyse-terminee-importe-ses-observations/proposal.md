## Why

Après la recette réelle de #5597, le porteur a relevé un détour : une fois la participation lancée, on suit l'analyse Tadarida sur la carte « Traitement Vigie-Chiro » de l'écran de lot. Quand « Actualiser » rend « Analyse terminée », la carte dit seulement que les observations sont prêtes à être importées. Pour les avoir, il faut quitter l'écran, ouvrir « Sons & validation » et demander « Importer depuis Vigie-Chiro… » au menu ☰. Il le formule ainsi : « ce n'est pas naturel de devoir rentrer dans la vue observations et de demander le téléchargement par le menu hamburger » (#5784).

L'observateur vient de demander où en est le traitement. La réponse « c'est terminé » appelle une seule suite, et l'application la connaît.

## What Changes

- Quand un relevé demandé par « Actualiser » rend une analyse **terminée**, les observations de la nuit sont importées dans le même geste, d'office (décision du porteur, 3 octobre 2026).
- La carte dit ce qui a été importé, sous l'état du traitement.
- Une nuit qui a déjà ses observations n'est **pas** réimportée : la carte le dit, et renvoie à « Sons & validation » pour qui veut les remplacer.
- Un import qui échoue se dit sur la carte, sans masquer que l'analyse, elle, est terminée.
- En ligne de commande, `etat-traitement-vigiechiro --importer` fait le même geste. Sans l'option, la commande reste en lecture seule (décision du porteur, 3 octobre 2026).
- L'application ne sonde toujours pas le serveur : rien ne s'importe sans un relevé demandé.

## Capabilities

### New Capabilities
- `lot/suivi-du-traitement` : savoir où en est l'analyse d'une nuit déposée, et recevoir ses observations dès que le relevé la dit terminée, à l'écran comme en ligne de commande.

### Modified Capabilities

Aucune. `lot/lancement-de-la-participation`, décrite en delta par `apres-le-depot-un-seul-geste-et-son-resultat`, porte la demande d'analyse et son résultat immédiat ; ce changement commence au relevé qui suit, et ne touche à aucune de ses exigences.

## Impact

- Écran de lot : `Lot.fxml` (une ligne de plus dans la carte du traitement), `SuiviTraitementUI`, `LotController` pour le câblage.
- ViewModel : `TraitementViewModel` (import après un relevé terminé, compte rendu, échec).
- Socle : le port `ImportObservations` de `commun` gagne une lecture, « la nuit a-t-elle déjà ses observations ? », que `validation` implémente. L'import lui-même (`ImportVigieChiro`) et l'écran « Sons & validation » ne changent pas.
- Ligne de commande : `EtatTraitementVigieChiro` (option `--importer`), et la façon dont `StrategieExecutionCli` décide du verrou du dossier de travail pour une commande en lecture seule sauf option.
- Captures du lot, `docs/ecrans/lot.md`, `dev-docs/cli.md`, recette S4.
- Ne change pas : les identifiants et libellés que lit le scénario filmé S4-47 (`#btnDeposer`, `#lblRetourLancement`, `#zoneTraitement`, `#lblEtatTraitement`, « Analyse planifiée »), ni ce que la carte montre à l'ouverture de l'écran.
- Hors de ce changement : le remplacement d'observations déjà importées, l'action groupée `ImportResultatsGroupe`, et tout sondage automatique.
