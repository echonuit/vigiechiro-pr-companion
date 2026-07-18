# Glossaire du domaine

Le **vocabulaire** employé dans le code, les revues et les issues. Cette page fixe *les mots* ; le
[Modèle de données & domaine](modele-de-donnees.md) fixe *les entités et le schéma* ; le
**[glossaire utilisateur](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/glossaire/)** fixe
les définitions vues par l'utilisateur final. Une divergence de mot entre ces trois surfaces est un
**défaut de vocabulaire** à corriger.

## L'unité de travail : nuit, passage, participation

Un même objet porte trois noms selon le point de vue. Les distinguer lève l'ambiguïté historique du
mot « lot ».

| Terme | Point de vue | Définition |
|---|---|---|
| **nuit** | capture (utilisateur) | Une nuit d'enregistrement sur un point d'écoute. Terme naturel, dominant dans l'IHM. |
| **passage** | protocole / modèle | L'entité `Passage` : un point d'écoute, une année, un **numéro de passage**. C'est la **racine d'agrégat** du [workflow à états](patterns.md#machine-a-etats-moteurworkflowpassage) `Importé → … → Déposé`. |
| **participation** | plateforme | La nuit **déposée** sur Vigie-Chiro. Un passage donne lieu à **au plus une** participation. |

**1 nuit ↔ 1 passage** : chaque nuit importée devient un passage distinct. « nuit » et « passage »
désignent donc le même objet ; on garde les deux mots car ils portent des nuances différentes (la
capture physique d'un côté, l'entité protocolaire numérotée de l'autre).

## « lot » : terme déprécié

!!! warning "N'employez plus « lot » pour désigner un passage"
    Historiquement, « lot » désignait *l'ensemble préparé des fichiers d'un passage au moment du
    dépôt*. Ce n'est **pas** un regroupement de plusieurs passages : le dépôt se fait **passage par
    passage** (un passage donne lieu à au plus une participation). Le mot induit en erreur, car il
    évoque un batch. **Vocabulaire retenu : on parle du _dépôt_ d'un passage.**

    - Côté IHM et doc utilisateur : « lot » est **purgé** (« préparer le **dépôt** »…).
    - Côté code : le package `fr.univ_amu.iut.lot` **conserve son nom pour l'instant** ; son
      renommage est tracé dans une issue de refactor dédiée du chantier
      [#1524](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/issues/1524).

Le mot « lot » subsiste par ailleurs au sens générique de *jeu de fichiers* (l'entrée du classifieur
Tadarida, une écoute filtrée sur plusieurs sites). Là aussi on préfère « **jeu** » ou
« **sélection** », pour ne pas le confondre avec le dépôt.

## Verdict de vérification

Le **verdict** est le jugement de qualité porté sur un passage après écoute d'un échantillon de
séquences (parcours P3, règles R13 consultative et R14 bloquante).

### Verdict final du passage

**Dérivé** des verdicts par fichier son et **surchargeable** (proposé, pas imposé — R13).

| Verdict final | Sens | Dépôt |
|---|---|---|
| `Non vérifié` | pas encore jugé (valeur sentinelle) | — |
| `OK` | pleinement exploitable | autorisé |
| `Utilisable` | exploitable avec réserve | autorisé |
| `Inexploitable` | à écarter | **bloqué (R14, garde reprise au lot 7)** |

### Verdict par fichier son

Saisi à l'écoute de chaque séquence de l'échantillon ; alimente la colonne « Verdict » et la barre
tricolore de la sélection, et sa dérivation (`AgregationVerdict`) propose le verdict final.

| Verdict fichier | Sens |
|---|---|
| `Non jugé` | pas encore écouté (défaut) |
| `Bon` | séquence exploitable |
| `Mauvais` | mauvaise qualité, mais pas inutilisable |
| `Inexploitable` | rien d'exploitable |

Les enums vivent dans [`commun/model/Verdict`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/Verdict.java) et [`commun/model/VerdictFichier`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/VerdictFichier.java).

!!! note "Bascule de vocabulaire livrée (chantier #1524, lot 6b)"
    Le lexique du **verdict final** a basculé de `À vérifier / OK / Douteux / À jeter` vers `Non vérifié
    / OK / Utilisable / Inexploitable` (correspondance : `OK`→`OK`, `Douteux`→`Utilisable`,
    `À jeter`→`Inexploitable`, `À vérifier`→`Non vérifié`). Seuls les **libellés** changent : les **noms
    de constantes** de l'enum restent `A_VERIFIER/OK/DOUTEUX/A_JETER` (pour préserver les badges CSS, le
    tri par `ordinal` et les vues de filtre sauvegardées), et le libellé stocké dans
    `passage.verification_verdict` est réécrit par la migration `V28`. La **garde de dépôt**
    (`Inexploitable` bloque, requalification) est reprise au **lot 7**.

## Les verbes des échanges avec la plateforme

Le verbe qui nomme un geste est la **seule** indication que l'utilisateur a sur ce qui va se passer :
il ne voit ni la requête, ni la direction, ni ce qui sera écrit chez qui. Deux fois, à deux chantiers
d'écart, ce mot a menti (#1855, #1838) : d'où la règle de l'[ADR 0022](decisions/0022-le-verbe-dit-le-sens-de-l-echange.md),
**le verbe dit le sens réel de l'échange**.

| Verbe | Ce qu'il désigne | Exemples |
|---|---|---|
| **Importer** | L'**entrée de données dans l'application**, quelle qu'en soit la source. C'est le verbe de l'objet qui **n'existait pas encore** localement. | « Importer une nuit » (carte SD), « Importer depuis VigieChiro… » (observations d'une participation) |
| **Récupérer** | Le **rapatriement, depuis la plateforme, de ce que l'application connaît déjà** : compléter ou rafraîchir. | « Récupérer depuis VigieChiro » (sites, points, passages ; météo et matériel d'un passage), « Récupération des identifiants depuis VigieChiro… » (ancrage) |
| **Envoyer** | L'écriture **vers** la plateforme d'un objet déjà rattaché. Fait la paire avec « Récupérer ». | « Envoyer vers VigieChiro » (métadonnées d'un passage) |
| **Publier** | L'écriture **vers** la plateforme de **corrections** portées sur des observations existantes. | « Publier les corrections vers VigieChiro… » |
| **Téléverser** / **Déposer** | Le **dépôt** des fichiers d'une nuit vérifiée. Voir l'entrée « Dépôt » du glossaire utilisateur. | « Téléverser sur Vigie-Chiro » |

!!! warning "« Importer » et « Récupérer » ne sont pas interchangeables, et ce n'est pas une incohérence"
    Le menu ☰ dit « **Importer** depuis VigieChiro… » là où Mes sites dit « **Récupérer** depuis
    VigieChiro » : même plateforme, même direction, deux verbes. C'est **voulu**, ils ne portent pas le
    même objet (règle 2 de l'ADR 0022). Aligner les deux reviendrait à écrire « Récupérer depuis la
    carte SD », ce que personne n'écrirait.

!!! note "« Synchroniser » : réservé à l'interne"
    Le mot promet un échange **bidirectionnel** et ne doit apparaître dans **aucun** libellé, message
    de progression ou nom de commande CLI dont le geste ne fait que recevoir. Il reste juste, en
    revanche, pour les classes qui réconcilient deux états (`RapprochementVigieChiro.synchroniser`,
    `RapportSynchro`) : elles font bien ce que le mot dit.

## Autres termes

Les termes du **domaine vus par l'utilisateur** (carré, point d'écoute, préfixe, séquence, sonogramme,
spectrogramme, statut, Tadarida, passage archivé, réactivation…) sont définis dans le
**[glossaire utilisateur](https://iutinfoaix-s201.github.io/vigiechiro-pr-companion/glossaire/)**.
Cette page-ci ne reprend que les termes dont le **sens interne** mérite une précision pour qui lit ou
modifie le code.
