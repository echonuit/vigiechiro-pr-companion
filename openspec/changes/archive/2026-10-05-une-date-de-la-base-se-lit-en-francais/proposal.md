## Why

La date de dépôt d'un passage s'écrit en base par `horloge.maintenant().toString()` : un instant local, par
exemple `2026-06-21T08:00:15.123456`. Les deux lecteurs qui la mettent en forme, `Horodatage.dateSeule` et
`ColonneDate.analyser`, n'acceptaient qu'une date seule. Leurs tests leur en donnaient une, forme que la base ne
porte jamais pour un dépôt.

Conséquences, confirmées par un test écrit avec la forme réelle (#5761) :

- l'écran de lot et la commande `deposer` recopiaient la valeur brute, « Passage déposé le 2026-06-21T08:00. » ;
- `statut-passage`, que l'issue croyait corrigée depuis #3990, la recopiait aussi : son lecteur échouait en
  silence et rendait la chaîne telle quelle ;
- la colonne « Déposé le » de la fiche d'un site et de la vue multisite lisait la date comme absente, et
  affichait sa marque d'absence pour un passage réellement déposé.

Autre cause, même symptôme : la colonne « Nuit du » de l'écran d'import affichait `LocalDate#toString`,
« 2026-07-03 », sous un avertissement qui dit « nuit du 03/07/2026 ».

## What Changes

- Une date que la base porte, seule ou avec son heure, se lit « 21/06/2026 » partout où elle s'affiche.
- La colonne « Déposé le » affiche la date d'un passage déposé.
- La colonne « Nuit du » de l'import se lit en français et se trie en date.
- Les sorties `--json` gardent la valeur ISO : c'est un contrat de script.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `lot/parcours-du-depot` : une exigence sur la date de dépôt.
- `importation/import-d-une-carte` : une exigence sur la date d'une nuit dans la table.

## Impact

- `commun/model/Horodatage` (le lecteur commun), `commun/view/ColonneDate`.
- `lot/viewmodel/FormatsLot`, `cli/commande/Deposer`, `importation/view/TableNuits`.
- Les colonnes de date de la fiche d'un site, de la vue multisite et de la table audio lisent le même lecteur.
- Les aperçus de l'écran de lot et de l'import qui montrent ces dates.
