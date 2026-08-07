# Passe ciblée · les constats que le statique ne tranche pas

> **Écrans traversés** : accueil, Mes sites, détail de carré, Carte & passages, importation, passage,
> qualification. · **Statut : à jouer.**
> Ce n'est pas une session propriétaire : c'est une **passe courte**, qui ne vise que les huit constats
> restés en suspens. Retour à la [méthode](../index.md).

## Objectif

L'instruction des constats de la campagne 1 (#3424) a rendu quatre verdicts : *corrigé*, *toujours
vrai*, *prémisse périmée*, et **à confirmer en séance**. Huit sont tombés dans ce dernier bac.

Ce n'est pas un aveu, c'est un verdict : **un comportement ne se juge pas sur une image fixe.** Un
rafraîchissement qui n'a pas lieu, un défilement qui ne suit pas, une pile de navigation écrasée, une
échelle de fréquences qui se recale : rien de tout cela n'apparaît sur une capture, et prétendre les
trancher statiquement aurait produit exactement le faux vert que ce dépôt traque.

Cette passe les tranche **avant** d'ouvrir les chantiers voisins. Cinq des huit touchent l'écran
d'import, que #1493 et #1492 doivent modifier : corriger avant de savoir lesquels sont encore vrais
reviendrait à travailler à l'aveugle.

## Environnement

Base **jetable**, jamais un workspace de travail : plusieurs cas suppriment ou écrasent.

```bash
env -u DISPLAY ./mvnw -q test-compile exec:java@generer-sd \
  -Dexec.args="recette/fixtures/spec /tmp/recette-sd"
```

Trois cartes servent ici : `sd-nominale` (le chemin courant), `sd-rejets` (des fichiers refusés, pour
PC-07 et PC-08 : un import qui rejette donne à la zone de progression quelque chose à montrer) et
`sd-nominale.zip` (pour PC-14 et PC-15, le parcours « Choisir un .zip… »).

Un site déclaré avec **au moins un point** et **une nuit déjà importée** est nécessaire dès PC-01 :
les compteurs de l'accueil ne peuvent pas bouger si rien ne les alimente.

## Le script

Une case = **un fait observable**. Si une case demande deux regards, elle est mal écrite : la couper.

**Étape 1 · Accueil, les compteurs suivent-ils la donnée ? (#1376)**

- [ ] **PC-01** · Base vierge, se connecter : le bandeau de compteurs **apparaît** sans quitter l'écran.
- [ ] **PC-02** · Importer une nuit, revenir à l'accueil : le compteur de passages a **augmenté**.
- [ ] **PC-03** · Supprimer ce passage, revenir à l'accueil : le compteur a **diminué**.

> PC-03 compte autant que les deux autres : une liaison qui ne suivrait que les ajouts passerait pour
> vivante tant qu'on ne retire rien.

**Étape 2 · « Voir sur la carte », le contexte survit-il ? (#1378)**

- [ ] **PC-04** · Depuis le détail d'un carré, « Voir sur la carte » : la carte est **centrée** sur ce carré.
- [ ] **PC-05** · Le fil d'Ariane porte **Accueil › Mes sites › Carré › Carte & passages**, pas Accueil › Carte.
- [ ] **PC-06** · Le bouton retour ramène au **détail du carré**, pas à l'accueil.

> Le centrage est déjà corrigé côté code (`FocalisationCarte.centrerSurCarre`) : PC-04 ne fait que le
> constater. Ce sont PC-05 et PC-06 qui restent ouverts.

**Étape 3 · Import, suit-on ce qui se passe ? (#1486)**

- [ ] **PC-07** · Au clic « Importer cette nuit », la zone de progression est **visible sans faire défiler**.
- [ ] **PC-08** · Sur un import court, on voit la barre progresser **au moins une fois**.

> La liste des rejets, elle, est corrigée : le bilan agrège désormais les raisons (« 1 fichier(s) :
> Original illisible… »). Ne reste que le défilement.

**Étape 4 · Import, les libellés disent-ils vrai ? (#1487)**

- [ ] **PC-09** · L'état du nommage dit que le renommage porte sur les **copies**, les originaux restant intacts.
- [ ] **PC-10** · La case « Conserver les originaux » se comprend **dans le bon sens** à la première lecture.
- [ ] **PC-11** · Son explication **passe à la ligne** au lieu d'être coupée en fin de ligne.

> L'en-tête de l'écran porte déjà « sans jamais modifier vos fichiers d'origine ». PC-09 juge si cela
> suffit **à cet endroit-là**, sous le champ concerné.

**Étape 5 · Import, le n° de passage est-il protégé ? (#1489)**

- [ ] **PC-12** · Point non choisi : le champ « N° de passage » est **grisé**.
- [ ] **PC-13** · Saisir un numéro, **puis** choisir le point : le numéro saisi n'est pas **écrasé en silence**.

> `ImportationController` lie bien `champPassage` à une propriété, mais à `traitement` (import en
> cours), pas à l'absence de point : le remède demandé ne semble pas posé.

**Étape 6 · Import, le .zip choisi reste-t-il reconnaissable ? (#1490)**

- [ ] **PC-14** · Après « Choisir un .zip… », le champ « Dossier source » affiche le **chemin du .zip choisi**.
- [ ] **PC-15** · Aucun chemin interne du workspace (`import-zip-<horodatage>`) n'apparaît à l'écran.

> Aucune capture ne couvre ce parcours : c'est en soi une lacune du harnais, à corriger dans la
> campagne.

**Étape 7 · « Modifier le passage », la modale tient-elle ? (#1494)**

- [ ] **PC-16** · À l'apparition du message météo, les boutons **restent dans** la modale.
- [ ] **PC-17** · Le récapitulatif de renommage s'affiche **en entier**, sans ellipse.
- [ ] **PC-18** · Après « Appliquer », un **retour visible** confirme que le renommage a eu lieu.

> Même famille que #1373, dont le volet « les boutons sortent de la modale » ne se reproduit plus sur
> la connexion : PC-16 juge si cette modale-ci a bénéficié du même correctif.

**Étape 8 · Spectrogramme, l'échelle sert-elle l'écoute ? (#1508)**

- [ ] **PC-19** · Sur une séquence de 5 s, l'échelle couvre la **bande utile** (20-120 kHz) sans dézoomer à la main.
- [ ] **PC-20** · Deux séquences de **durées différentes** s'ouvrent sur la **même** échelle de fréquences.

> Le rendu vient de la bibliothèque `audio-view`, dont la version a bougé depuis le constat
> (normalisation, boucle, palette daltonien). Il se peut que le comportement ait changé sans que
> personne ne l'ait noté : PC-19 et PC-20 sont autant susceptibles de fermer l'issue que de la
> confirmer.

## Verdict par axe

À remplir en séance. Les axes **P** (parité CLI) et **D** (doc & captures) ne sont pas instruits ici :
cette passe ne vise que ce qui demande l'application en marche.

| Axe | Verdict | Notes |
|---|---|---|
| **C** · Conformité | | |
| **E** · États | | |
| **F** · Fluidité | | |
| **R** · Clavier | | |

## Les huit issues

| Issue | Cases | Ce qu'un vert signifie |
|---|---|---|
| #1376 | PC-01 à PC-03 | la liaison est vivante : **fermer** |
| #1378 | PC-04 à PC-06 | le contexte survit : **fermer** |
| #1486 | PC-07, PC-08 | le suivi est visible : **fermer** |
| #1487 | PC-09 à PC-11 | les libellés disent vrai : **fermer** |
| #1489 | PC-12, PC-13 | le numéro est protégé : **fermer** |
| #1490 | PC-14, PC-15 | le .zip reste reconnaissable : **fermer** |
| #1494 | PC-16 à PC-18 | la modale tient : **fermer** |
| #1508 | PC-19, PC-20 | l'échelle sert l'écoute : **fermer** |

Un **rouge** ne se contente pas de laisser l'issue ouverte : il la **recadre**, puisque son corps décrit
un produit d'il y a un mois. Une case rouge dont l'issue décrit autre chose est un constat neuf.

## Notes de méthode

Les identifiants sont préfixés `PC-` et non `S1-`/`S2-` : ils appartiennent à **cette passe**, pas aux
sessions complètes, dont la numérotation reste libre pour quand elles seront rejouées en entier. C'est
la première fois qu'un script du dépôt porte des identifiants stables et de vraies cases à cocher - la
refonte des sept autres suivra, la méthode les annonce depuis le début sans qu'aucun ne les ait.
