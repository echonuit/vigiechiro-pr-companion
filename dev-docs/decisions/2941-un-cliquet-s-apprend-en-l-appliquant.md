# ADR 2941 - Un cliquet s'apprend en l'appliquant (amende 2843)

- **Statut** : Accepté - 2026-07-30
- **Chantier** : #2941, clôture du chantier #2365
- **Vérification** : certaine - `scripts/adr/verifie_scripts.py`

## Contexte

L'[ADR 2843](2843-typographie-cliquet-plutot-que-nettoyage.md) a posé le mécanisme : une convention typographique se tient par un cliquet, pas par un nettoyage d'un seul tenant. Le chantier #2365 l'a ensuite appliquée sur **douze tranches**, jusqu'au plancher.

Appliquer une règle n'est pas la même chose que la poser. Chacune des cinq décisions ci-dessous a été prise **en cours de route**, et chacune a été déclenchée par un incident précis. Elles ont d'abord été écrites dans le corps de 2843, ce qui était une erreur de forme (#2941) : une ADR est immuable une fois acceptée, et le dépôt a son patron pour l'amender.

2843 garde son corps consolidé, qui décrit le mécanisme **tel qu'il fonctionne** et que les contributeurs lisent. Cette ADR-ci porte la **chronologie et les incidents**, c'est-à-dire ce qu'un lecteur ne peut pas reconstituer.

## Décision

**1. La mesure compte la prose, pas les occurrences.** Le compteur Java était brut, alors que les zones Markdown excluaient déjà les citations. Il butait donc sur un plancher de **50 citations légitimes** du glyphe de valeur absente, que rien ne distinguait d'un reste de travail.

Un cliquet dont on ignore le plancher est un cliquet sur lequel **on ne peut pas clore le chantier** : personne ne sait quel nombre veut dire « fini ». Un plancher **nommé**, en revanche, est une information ; le cliquet final vaut 1, et l'ADR 2843 dit lequel.

**2. Une zone nettoyée se promeut dans la tranche qui l'amène à zéro.** Pas à la tranche suivante. Différer ouvre une fenêtre où l'arbre est propre et où **rien ne le garde** : la première rechute y passe sans bruit, et le cliquet, ayant encore sa marge, reste vert.

**3. Une zone qui ne balaie aucun fichier lève.** La généralisation des zones à d'autres extensions rend possible un motif mal apparié à son arbre (`*.md` sur un arbre Java). Il rapporterait « 0 cadratin de prose » **à jamais**, avec la forme exacte du succès. Rien, ailleurs, ne distingue une zone propre d'une zone jamais regardée.

C'est le même raisonnement que `verifie_scripts.py` applique aux scripts de vérification, et il vaut ici pour les **zones** : un dispositif muet se présente en succès.

**4. Une forme citée se taille au plus juste.** Deux formes ont été ajoutées, le littéral réduit au glyphe et la classe de caractères des analyseurs d'en-têtes. La seconde est écrite **en littéral**, et non « des crochets contenant un cadratin » : un motif si large avalerait les libellés de liens Markdown, où un tiret entre crochets est de la prose ordinaire.

Chaque élargissement du motif de citation est un risque de **déflation silencieuse** du compteur, qui est la panne la plus grave possible pour ce mécanisme. Le garde-fou porte donc, dans la même fixture, la classe épargnée **et** le lien compté : un élargissement fait rougir au lieu de déflater.

**5. Le balayage de la racine est non récursif.** Les sous-arbres ont leurs propres zones, et descendre depuis `.` ramasserait les fichiers **non suivis**. Un artefact local ferait alors rougir le garde chez le développeur sans rien signaler en CI. Un garde qui ment selon la machine ne vaut rien.

## Conséquences

La **ligne de vérification** d'une ADR à cliquet est mutable par construction : le cliquet s'y resserre à chaque baisse, et l'ADR 2843 exige que ce soit fait dans la PR qui fait baisser la mesure. L'immuabilité porte sur le **corps** de l'ADR, pas sur cette ligne. La distinction n'était écrite nulle part et méritait de l'être.

La convention elle-même a été **réécrite** dans `CONTRIBUTING.md` et `dev-docs/ajouter-une-fonctionnalite.md` : elle disait « dans la doc et les commentaires » alors que le chantier l'a étendue aux chaînes affichées, aux styles, aux scripts et aux ateliers, et elle ne nommait aucune des formes citées. Un contributeur qui l'appliquait à la lettre aurait « corrigé » `Formats.VALEUR_ABSENTE`.

**Ce que le cliquet ne couvre toujours pas** : les **titres de pull request**, qui alimentent le `CHANGELOG` par le squash. Le fichier généré porte 28 cadratins hérités, et il est exclu des zones parce que le corriger falsifierait le compte rendu de ce qui a été livré.

## Pistes écartées

**Revenir 2843 à son état d'acceptation.** Cela aurait supprimé du contenu opératoire exact, que les contributeurs lisent pour comprendre le mécanisme, au seul bénéfice d'une forme. Le patron `amende NNNN` de l'index existe précisément pour ne pas avoir à choisir.

**Faire entrer les deux dernières occurrences dans une forme citée.** Un motif épargnant tout littéral **commençant** par le glyphe serait trop permissif, et recomposer l'attente d'un test depuis `Formats.VALEUR_ABSENTE` changerait ce que ce test **épingle**, au détour d'une passe de typographie. Un cliquet de 1 refuse la deuxième occurrence exactement comme une tolérance zéro refuse la première.
