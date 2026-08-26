---
type: adr
title: "L'amorçage s'écrit d'un seul coup, et se relit en octets"
status: stable
article: A17
chantier: "#3507, lot 1 (#3559) du chantier #3518 ; ADR écrite à la passe 10 de la clôture du lot"
decided_at: 2026-08-10
verification: certaine
enforced_by:
  - "SecretsEcritsProtegesTest#aucun_fichier_d_amorcage_ecrit_en_direct"
verified:
  - by: machine:ci
    at: 2026-08-10
---

# L'amorçage s'écrit d'un seul coup, et se relit en octets

## Contexte

Le fichier d'amorçage dit **où vivent les données** : sans lui, l'application ne sait pas quel dossier
de travail ouvrir ni quelle base migrer ([ADR 1038](1038-la-configuration-d-amorcage-vit-hors-de-la-base.md)).
Il était écrit **en place**, par un `Properties.store` direct sur le fichier final.

Une interruption pendant cette écriture - coupure de courant, disque plein, processus tué - laissait un
fichier **tronqué**. Et sa lecture pardonnait : une clé absente est traitée comme « non configurée »,
donc l'application repartait sur les emplacements **par défaut**, sans rien dire. L'utilisateur
retrouvait une application vide, ses données intactes ailleurs, et aucun message pour l'expliquer.

`EcritureAtomique` existait déjà et servait deux voisins ; ce fichier ne passait pas par elle.

## Décision

**Le fichier d'amorçage s'écrit d'un seul coup**, par `EcritureAtomique.ecrire`. Un lecteur voit
l'ancien contenu ou le nouveau, jamais un fichier coupé.

### Le garde porte sur la STRUCTURE, pas sur le comportement

Une troncature est un **état intermédiaire** : on ne peut pas écrire un test qui l'observe sans
piloter l'ordonnanceur. Le garde vérifie donc que ces fichiers **passent par** `EcritureAtomique`,
c'est-à-dire la seule chose qui soit vérifiable de l'extérieur. `SecretsEcritsProtegesTest` porte une
seconde liste pour cela, à côté de celle des secrets.

C'est un garde de **moyen**, pas de fin. Il est assumé comme tel : le moyen est ici la seule preuve
accessible, et l'absence de preuve directe ne vaut pas dispense de garde.

### Le détour par un flux d'octets n'est pas une lourdeur

`Properties.store(OutputStream)` écrit en **ISO-8859-1** et échappe les caractères non latins en
séquences hexadécimales ; `store(Writer)` écrirait le texte brut. Or `load(InputStream)` relit en
**ISO-8859-1**. Écrire par un `Writer` et relire par un flux corromprait donc **le premier chemin
accentué venu** - un dossier « Données », un utilisateur « Benoît ».

Le rendu obtenu par le flux est purement **ASCII**, donc identique en UTF-8 : il traverse
`EcritureAtomique`, qui travaille sur du texte, sans rien perdre.

Un lecteur futur trouvera ce détour inutilement compliqué et voudra le simplifier. C'est
précisément pourquoi cette ADR existe : la simplification est silencieuse à l'écriture et ne se
manifeste qu'à la relecture, sur un poste dont le nom d'utilisateur porte un accent.

## Conséquences

- Tout fichier dont une lecture **pardonne l'absence** d'une clé est candidat au même traitement : le
  pardon transforme une troncature en retour aux valeurs par défaut, c'est-à-dire en perte silencieuse.
- La liste des fichiers écrits d'un seul coup est tenue dans le garde, pas dans la prose.
