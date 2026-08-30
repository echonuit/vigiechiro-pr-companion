---
name: couvrir-les-usages-livres
description: Use at closure pass 6, once the project brief is aligned, to check that every usage the chantier introduced is covered. The inventory starts from the diff rather than from memory, a zero is confirmed by hand, and the pass concludes on two counts rather than one.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Couvrir les usages livrés

## Loi d'airain

```
ON CHERCHE CE QUI MANQUE, ON NE RELIT PAS CE QU'ON A ÉCRIT
```

C'est le point qui a le plus souvent failli : on relit ses propres tests, on les trouve verts, et on
conclut que c'est couvert, alors qu'un pan entier n'a jamais été regardé.

## Annoncer

« J'utilise la compétence couvrir-les-usages-livres sur les usages introduits par <le chantier>. »

## Fonction de garde

```
1. INVENTORIER les capacites ajoutees ou changees DEPUIS LE DIFF du chantier,
               jamais depuis sa memoire.
2. NOTER       pour chacune quel test la couvre, ET DANS QUELLE FAMILLE.
               « Aucun » est une reponse valable, mais elle se DIT.
3. CONFIRMER   tout zero a la main, en ouvrant les fichiers. Un grep se trompe
               dans les DEUX sens.
4. COMBLER     ce qui manque, dans la famille qui le couvre seule.
5. MUTER       ce que le chantier a livre : PIT cible, plus la mutation a la main
               pour ce que PIT ne mute pas.
6. LIRE        les survivants un par un, et les eliminer.
7. RENDRE      LES DEUX COMPTES : tests ajoutes par famille, survivants elimines.
```

## Les familles, et ce que chacune couvre seule

« Les usages sont couverts » se conclut sinon sur la famille qu'on a sous la main.

| Famille | Compte au 30 août 2026 | Ce que rien d'autre ne couvre |
|---|---:|---|
| tests Java | 805 classes | l'unité et l'intégration |
| dont UI, TestFX headless | 191 | le geste sur un écran monté et ses liaisons |
| dont E2E, `fr.univ_amu.iut.e2e` | 24 | la couture IHM, ViewModel, service, base |
| `bats`, sous `src/test/bats` | 4 fichiers, 129 cas | la **ligne de commande** |
| recette | 13 sessions, 370 cas | ce qu'un humain observe |

**La famille `bats` est celle qu'on oublie**, parce que rien ne la remplace : un test Java appelle le
service, il ne **lance pas la commande**. Un geste couvert côté service et absent des `bats` n'est pas
couvert en ligne de commande.

Les frontières d'architecture, elles, sont couvertes d'office par `ArchitectureTest`.

## Les trois familles d'angles morts

**Les chemins non nominaux** : le refus, l'erreur, l'annulation, l'état vide, la donnée absente, la
feature désactivée. Le cas nominal est presque toujours testé ; c'est l'autre branche qui manque.

**Les surfaces jumelles** : un geste couvert côté IHM mais pas côté ligne de commande, ou l'inverse,
n'est couvert qu'à moitié. C'est le prolongement de la passe 2.

**Le cas réel** : un test synthétique vert ne prouve pas que le vrai jeu de données passe. Quand
c'est possible, rejouer le geste sur une vraie nuit avant de conclure.

## Un zéro se confirme à la main, et se trompe dans les deux sens

L'inventaire se mène volontiers à coups de `grep`. Bonne amorce, mauvaise conclusion : un audit réel
de la suite E2E a produit **quatre** erreurs de suite.

| Ce que le `grep` faisait | Ce qu'il rendait |
|---|---|
| chercher le nom d'un écran | un **homonyme**, « recherche » remontant `PointFixeRecherche` |
| chercher par clé de vue | **zéro** pour `importation`, `lot`, `qualification`, tous couverts par des parcours qui pilotent les services |
| chercher une commande par son nom de classe | **non testée**, quand le test l'invoque en kebab-case |
| et l'inverse | **non testée**, quand le test instancie la classe |

Il a fallu **croiser deux signaux** pour obtenir la vraie réponse : sur 41 commandes, **zéro** sans
test, là où les greps naïfs en annonçaient jusqu'à vingt.

Le second signal le moins cher est le graphe du dépôt : ses arêtes `calls` entrantes disent qui
exerce réellement une méthode, y compris depuis un test qui ne cite pas son nom.

```bash
graphify query "qui exerce <la méthode> ?" --budget 2500
```

Sa sortie est elle aussi une hypothèse. Mais **deux hypothèses obtenues par des chemins différents
valent mieux que la même deux fois**, et un « aucun test » sorti d'un grep n'est qu'une hypothèse : à
confirmer en ouvrant les fichiers **avant** d'en faire une issue. Une issue fausse coûte plus cher que
le trou qu'elle prétend signaler.

## La mutation est la condition de fin, pas un geste facultatif

Un garde-fou de non-régression ne prouve rien tant qu'on ne l'a pas vu **échouer** avec le défaut en
place. C'est le seul dispositif qui distingue un test qui **passe** d'un test qui **juge**.

Deux gestes, complémentaires :

- le **PIT ciblé** sur les classes du chantier, exhaustif là où il s'applique ;
- la **mutation à la main** pour ce que PIT ne mute pas : attribut d'annotation, câblage, FXML, sonde
  réseau.

Les survivants se lisent **un par un**. Un survivant est soit un test à écrire, soit une mutation
équivalente qu'on écarte en le disant.

## La passe rend deux comptes, et un seul ne conclut pas

C'est ce qui la sépare des treize autres, qui se concluent sur une mesure.

| Axe | Ce qu'il compte |
|---|---|
| par famille | le nombre de tests **ajoutés** dans chacune |
| par mutation | le nombre de **survivants éliminés** |

**Des tests ajoutés sans survivant éliminé ne prouvent rien** : ils passent, ils ne jugent pas.
**Des survivants éliminés sans test ajouté dans la bonne famille laissent un usage entier dehors** :
on a durci ce qu'on regardait, pas ce qui manquait.

Les deux se rendent, et l'un sans l'autre ne conclut pas la passe.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « Mes tests sont verts, c'est couvert » | On cherche ce qui manque. Verts, ils ne disent rien de ce qui n'a pas été regardé |
| « J'ai relu mes tests » | L'inventaire se fait depuis le **diff**, pas depuis la mémoire |
| « Le `grep` dit zéro test » | C'est une hypothèse. Elle s'est trompée quatre fois de suite, dans les deux sens |
| « Ce geste est testé » | Dans quelle **famille** ? Un test Java ne lance pas la commande |
| « PIT n'a rien trouvé » | Il ne mute ni les annotations, ni le câblage, ni les FXML |
| « J'ai ajouté six tests » | Et combien de survivants éliminés ? Un compte seul ne conclut pas |
| « Il reste un survivant, tant pis » | Il se lit : test à écrire, ou mutation équivalente écartée **en le disant** |
| « Ce trou, je le noterai » | Un trou assumé devient une issue immédiatement ; tacite, il devient une régression |
