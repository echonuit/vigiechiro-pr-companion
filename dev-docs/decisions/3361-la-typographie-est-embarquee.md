# ADR 3361 - La typographie est **embarquée**, pas empruntée à la machine

- **Statut** : Accepté - 2026-08-05
- **Chantier** : #3361, suite de la clôture du chantier #3151
- **Vérification** : certaine - `TypographieTest#les_fichiers_sont_embarques`

## Contexte

`base.css` demandait `"Segoe UI", "Roboto", "Helvetica Neue", sans-serif`.

**Aucune de ces trois polices n'est installée** sur un poste de développement Linux (`fc-list` en trouve
zéro) ni sur `ubuntu-latest`, dont le workflow n'en installe aucune. Les deux environnements retombaient
donc sur le **même** dernier recours, `sans-serif` - mais cet alias est résolu par le système : Noto Sans
sur un poste, une police plus large sur le runner.

Les trois noms cités ne servaient **jamais**, chez personne. Le produit héritait de la typographie de la
machine qui l'exécute.

## Ce que ça coûtait

**Visible** : le garde de troncature des captures échouait en CI sur des libellés qui tenaient en local,
à 13 et 28 px près. Trois allers-retours sur la seule clôture de #3151. Pire, un rejeu local était vert
**pour de mauvaises raisons** : il mesurait avec les polices du poste, pas avec celles qui font foi.

**Invisible, et c'est le vrai problème** : deux utilisateurs sur deux systèmes voyaient des rendus
différents. Un libellé qui tient chez l'un peut tronquer chez l'autre, et **aucun garde ne le voit** -
celui des captures ne tourne qu'en CI, sur une seule machine.

## Décision

**Noto Sans Regular et Bold sont embarquées dans le jar** et chargées au démarrage
([Typographie#installer]), puis citées **en tête** de `base.css`.

- **Noto Sans** plutôt que DejaVu : c'est déjà ce que résout `sans-serif` sur un poste Linux courant, sa
  licence **SIL OFL 1.1** se compose avec la GPLv3 du produit, et les deux graisses pèsent **1 Mo**
  contre 1,4 Mo. Les CSS ne demandent que ces deux graisses (65 `-fx-font-weight: bold`, aucune autre) ;
- les trois noms d'origine **restent** derrière, comme filet si le chargement échoue ;
- l'installation est **best-effort** : une police introuvable ne fait pas échouer le démarrage. Le
  produit retombe alors sur le comportement d'avant - dégradé, jamais bloquant. Un écran qui refuse de
  s'ouvrir serait un remède pire que le mal.

## Deux points d'entrée, et pas un

L'application charge `base.css` par `MainView.fxml` ; les **41 outils de capture** montent leurs scènes
sans passer par le chrome. `installer()` est donc appelée depuis `App#start` **et** depuis
`ApercuFx#enregistrerPng`, et elle est **idempotente** pour que l'ordre n'ait pas d'importance.

## Ce que ça ne fait pas

Ça ne supprime pas le garde de troncature : un libellé peut toujours être trop long pour son champ. Ça
rend son **verdict reproductible** - ce qui tronque en CI tronque désormais en local, et inversement.

## Le garde qui aurait menti

La première version de `TypographieTest` vérifiait que `Font.getFamilies()` contient « Noto Sans » après
installation. **Elle restait verte en retirant tout le chargement** : la machine de développement a cette
police en système. Le test aurait certifié un embarquement qui ne se faisait pas - précisément le défaut
qu'il devait prévenir, puisque `Font.loadFont` rend `null` sans lever et qu'`installer()` avale l'échec.

La vérification porte donc d'abord sur ce qui est **déterministe partout** : les fichiers sont-ils
présents et non tronqués dans les ressources. C'est là qu'est le risque réel - un changement de
packaging, un filtrage de ressources, un `.gitattributes` qui abîme un binaire.

## Alternative écartée

**Installer une police dans le job CI** (`apt-get install fonts-roboto`). Aurait supprimé les
allers-retours, mais laissé le produit dépendre de la machine de chaque utilisateur : elle traite le
symptôme et laisse la cause.
