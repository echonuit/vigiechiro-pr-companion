# Passe ciblée · les constats que le statique ne tranche pas

> **Écrans traversés** : accueil, Mes sites, détail de carré, Carte & passages, importation, passage,
> qualification. · **Statut : jouée le 2026-08-07.**
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

- [x] 🔴 **PC-01** · Base vierge, se connecter : le bandeau de compteurs **apparaît** sans quitter l'écran.
- [x] 🟢 **PC-02** · Importer une nuit, revenir à l'accueil : le compteur de passages a **augmenté**.
- [x] 🟢 **PC-03** · Supprimer ce passage, revenir à l'accueil : le compteur a **diminué**.

> PC-03 compte autant que les deux autres : une liaison qui ne suivrait que les ajouts passerait pour
> vivante tant qu'on ne retire rien.

**Étape 2 · « Voir sur la carte », le contexte survit-il ? (#1378)**

- [x] 🟢 **PC-04** · Depuis le détail d'un carré, « Voir sur la carte » : la carte est **centrée** sur ce carré.
- [x] 🔴 **PC-05** · Le fil d'Ariane porte **Accueil › Mes sites › Carré › Carte & passages**, pas Accueil › Carte.
- [x] 🔴 **PC-06** · Le bouton retour ramène au **détail du carré**, pas à l'accueil.

> Le centrage est déjà corrigé côté code (`FocalisationCarte.centrerSurCarre`) : PC-04 ne fait que le
> constater. Ce sont PC-05 et PC-06 qui restent ouverts.

**Étape 3 · Import, suit-on ce qui se passe ? (#1486)**

- [x] 🔴 **PC-07** · Au clic « Importer cette nuit », la zone de progression est **visible sans faire défiler**.
- [x] ⚪ **PC-08** · Sur un import court, on voit la barre progresser **au moins une fois**.

> La liste des rejets, elle, est corrigée : le bilan agrège désormais les raisons (« 1 fichier(s) :
> Original illisible… »). Ne reste que le défilement.

**Étape 4 · Import, les libellés disent-ils vrai ? (#1487)**

- [x] 🔴 **PC-09** · L'état du nommage dit que le renommage porte sur les **copies**, les originaux restant intacts.
- [x] 🟢 **PC-10** · La case « Conserver les originaux » se comprend **dans le bon sens** à la première lecture.
- [x] 🟢 **PC-11** · Son explication **passe à la ligne** au lieu d'être coupée en fin de ligne.

> L'en-tête de l'écran porte déjà « sans jamais modifier vos fichiers d'origine ». PC-09 juge si cela
> suffit **à cet endroit-là**, sous le champ concerné.

**Étape 5 · Import, le n° de passage est-il protégé ? (#1489)**

- [x] 🔴 **PC-12** · Point non choisi : le champ « N° de passage » est **grisé**.
- [x] 🔴 **PC-13** · Saisir un numéro, **puis** choisir le point : le numéro saisi n'est pas **écrasé en silence**.

> `ImportationController` lie bien `champPassage` à une propriété, mais à `traitement` (import en
> cours), pas à l'absence de point : le remède demandé ne semble pas posé.

**Étape 6 · Import, le .zip choisi reste-t-il reconnaissable ? (#1490)**

- [x] 🔴 **PC-14** · Après « Choisir un .zip… », le champ « Dossier source » affiche le **chemin du .zip choisi**.
- [x] 🔴 **PC-15** · Aucun chemin interne du workspace (`import-zip-<horodatage>`) n'apparaît à l'écran.

> Aucune capture ne couvre ce parcours : c'est en soi une lacune du harnais, à corriger dans la
> campagne.

**Étape 7 · « Modifier le passage », la modale tient-elle ? (#1494)**

- [x] 🟢 **PC-16** · À l'apparition du message météo, les boutons **restent dans** la modale.
- [x] 🟢 **PC-17** · Le récapitulatif de renommage s'affiche **en entier**, sans ellipse.
- [x] 🔴 **PC-18** · Après « Appliquer », un **retour visible** confirme que le renommage a eu lieu.

> Même famille que #1373, dont le volet « les boutons sortent de la modale » ne se reproduit plus sur
> la connexion : PC-16 juge si cette modale-ci a bénéficié du même correctif.

**Étape 8 · Spectrogramme, l'échelle sert-elle l'écoute ? (#1508)**

- [x] 🟢 **PC-19** · Sur une séquence de 5 s, l'échelle couvre la **bande utile** (20-120 kHz) sans dézoomer à la main.
- [x] ⚪ **PC-20** · Deux séquences de **durées différentes** s'ouvrent sur la **même** échelle de fréquences.

> Le rendu vient de la bibliothèque `audio-view`, dont la version a bougé depuis le constat
> (normalisation, boucle, palette daltonien). Il se peut que le comportement ait changé sans que
> personne ne l'ait noté : PC-19 et PC-20 sont autant susceptibles de fermer l'issue que de la
> confirmer.

## Verdict par axe

À remplir en séance. Les axes **P** (parité CLI) et **D** (doc & captures) ne sont pas instruits ici :
cette passe ne vise que ce qui demande l'application en marche.

| Axe | Verdict | Notes |
|---|---|---|
| **C** · Conformité | **bloquant** | deux écrans affirment le contraire de ce qui s'est produit (#3448, #3449) |
| **E** · États | remarque | le n° de passage est modifiable trop tôt, puis écrasé sans signal (#1489) |
| **F** · Fluidité | remarque | le défilement fuit la progression (#1486) ; la fenêtre s'ouvre trop petite (#3452) |
| **R** · Clavier | non instruit | aucune case de cette passe ne portait sur le clavier |

## Les huit issues

| Issue | Cases | Sort |
|---|---|---|
| #1376 | PC-01 à PC-03 | **à recadrer** : le remède qu'elle demande est déjà en place |
| #1378 | PC-04 à PC-06 | **à recadrer** : le centrage est livré, la navigation non |
| #1486 | PC-07, PC-08 | **à recadrer** : les rejets sont réglés, le défilement non |
| #1487 | PC-09 à PC-11 | **à recadrer** : deux constats sur trois réglés par un déménagement |
| #1489 | PC-12, PC-13 | **confirmée intégralement** |
| #1490 | PC-14, PC-15 | **confirmée intégralement** |
| #1494 | PC-16 à PC-18 | **à recadrer** : PC-18 échoue autrement (#3449) |
| #1508 | PC-19, PC-20 | **fermée** |

Un **rouge** ne s'est pas contenté de laisser l'issue ouverte : il l'a **recadrée**. Cinq issues sur huit
décrivaient mal ce qu'elles visaient - non par négligence, mais parce que le produit a bougé sous elles.

## Ce que la passe a produit

**Dix-huit cases tranchées sur vingt** : neuf rouges, neuf vertes. PC-08 est restée non concluante
(l'import de six fichiers est instantané, et la carte de charge demandait 10 Go que le disque n'avait
pas) ; PC-20 n'était pas jouable, les six séquences de la fixture ayant toutes la même durée.

| Issue | Sort |
|---|---|
| #1508 | **fermée** : la borne à 35 kHz a disparu avec la version d'`audio-view` |
| #1489, #1490 | **confirmées intégralement** |
| #1376, #1378, #1486, #1487, #1494 | **à recadrer** : chacune décrit un défaut plus large ou autre que le réel |

Cinq issues sur huit décrivaient donc **mal** ce qu'elles visaient - non par négligence, mais parce que
le produit a bougé sous elles. C'est l'argument le plus concret pour rejouer une campagne plutôt que de
reprendre ses constats au mot.

### Six constats neufs, dont deux bloquants

Aucun n'était visé par une case, et **aucun ne pouvait sortir d'une capture** :

| | Constat |
|---|---|
| #3448 | **bloquant** · l'import annonce « Participation créée » alors qu'aucune ne l'a été |
| #3449 | **bloquant** · le renommage réussit, base et disque, et l'écran annonce « Envoi impossible » |
| #3452 | la fenêtre s'ouvre trop petite : deux activités coupées au premier lancement |
| #3453 | la modale de connexion déborde en transitoire pendant la récupération |
| #3454 | « Choisissez d'abord un site » s'affiche alors que le site est choisi |
| #3455 | le bouton retour garde le numéro d'avant le renommage |

Les deux bloquants sont **la même faute** vue des deux côtés : une opération composite dont on ne
rapporte qu'une moitié - le succès distant qui n'a pas eu lieu d'un côté, l'échec distant qui masque le
succès local de l'autre.

### Ce qui marche, et qu'il faut dire aussi

Le refus d'un import faute de place est **exemplaire** : « besoin d'environ 10,1 Go, seulement 9,5 Go
disponibles. Libérez de l'espace, ou décompressez l'archive vous-même. » Il chiffre le besoin, chiffre le
disponible, et donne deux sorties. Une séance qui ne relèverait que les fautes donnerait une image fausse
du produit.

## Notes de méthode

Les identifiants sont préfixés `PC-` et non `S1-`/`S2-` : ils appartiennent à **cette passe**, pas aux
sessions complètes, dont la numérotation reste libre pour quand elles seront rejouées en entier. C'est
la première fois qu'un script du dépôt porte des identifiants stables et de vraies cases à cocher - la
refonte des sept autres suivra, la méthode les annonce depuis le début sans qu'aucun ne les ait.
