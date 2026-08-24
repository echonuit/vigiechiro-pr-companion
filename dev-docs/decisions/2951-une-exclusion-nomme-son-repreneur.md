---
type: adr
title: "Une exclusion nomme son repreneur, ou c'est un trou (amende 2867)"
status: stable
article: A11
chantier: "#2951, chantier #1771"
decided_at: 2026-07-30
verification: humaine
verification_note: "le motif dit **pourquoi** ; aucun scan ne distingue une exclusion justifiée d'une exclusion tacite"
verified:
  - by: human:nedseb
    at: 2026-07-30
---

# Une exclusion nomme son repreneur, ou c'est un trou (amende 2867)

## Contexte

L'[ADR 2867](2867-une-dette-se-tient-par-un-cliquet.md) nomme le premier piège du patron, le
**court-circuit** : dès qu'un détecteur cesse de regarder un objet parce qu'il le croit déjà traité, il
devient aveugle exactement sur ce qui est en cours de migration, et son silence se lit comme un accord.

Elle ne dit pas comment le **reconnaître**, et c'est ce qui manque. Un cliquet a des raisons parfaitement
légitimes d'écarter des fichiers :

- la **destination** de la migration, qui fait par métier ce que le détecteur cherche ;
- le test de l'**analyseur** du format, à qui donner un générateur reviendrait à le tester contre
  lui-même ;
- le **partage du travail** avec un autre cliquet, quand deux dettes voisines se migrent vers deux briques
  différentes.

Rien, dans le code, ne distingue ces exclusions-là d'un court-circuit. Elles ont la même forme : un `if`
qui rend `false`.

## Ce que l'incident a montré

`CliquetSemisTopologieTest` écartait deux populations dans le **même** `if` :

```java
if (source.contains("JeuDeDonneesPassage") || source.contains("new Passage(")) {
    return false;
}
```

La seconde est légitime : un fichier qui va jusqu'au passage relève du cliquet des semis de passage, qui
le compte. La première ne l'est pas : un fichier **partiellement migré** - qui prend la fixture pour sa
nuit et sème encore sa topologie à la main - sortait des **deux** cliquets à la fois.

Deux fichiers étaient dans ce cas, `ServiceImportTest` et `ServiceSoldeSaisonTest` (corrigé par #2948).
Et ce cliquet a été posé **après** la rédaction de l'ADR qui décrit le piège, par la personne qui l'avait
écrite.

## Décision

**Une exclusion nomme le dispositif qui reprend l'objet écarté, ou c'est un trou.**

Le critère qui sépare les deux formes tient en une phrase :

> Une **partition** ne fait disparaître aucun objet des deux comptes. Un **court-circuit**, si.

Il est vérifiable et non déclaratif : pour toute exclusion, il faut pouvoir répondre à « qui compte cet
objet, maintenant ? ». Les réponses admissibles sont un autre cliquet, un autre garde-fou nommé, ou « rien
ne le compte, et c'est justifié parce qu'il n'a rien à migrer ». La réponse inadmissible est le silence,
et c'est celle qu'on donne sans s'en apercevoir quand on écarte « ce qui est déjà traité ».

Corollaire de forme : **deux exclusions de natures différentes ne partagent pas un `if`.** Le `||`
ci-dessus est ce qui a permis à un court-circuit de voyager dans le même geste qu'une partition, sous un
commentaire qui ne décrivait que la seconde.

## Conséquences

- Le commentaire d'une exclusion cite **un nom** : celui du repreneur. « Relève de l'autre cliquet » ne
  suffit pas si l'autre cliquet ne le compte pas.
- Une exclusion dont le repreneur disparaît devient un trou **silencieusement**. Rien ne le signale
  aujourd'hui ; c'est la limite assumée de cette ADR, dont la vérification est humaine.
- **Documenter un piège ne prémunit pas contre lui.** C'est la leçon la plus utile de cet incident, et
  elle vaut au-delà des cliquets : entre l'ADR 2867 et la pose de ce cliquet, il s'est écoulé quelques
  heures. Ce qui prémunit n'est pas le souvenir de l'incident, c'est un **critère applicable sans lui**.

## Alternatives écartées

**Interdire les exclusions.** Un détecteur sans exclusion compte sa propre destination, se compte
lui-même, et compte le test de l'analyseur du format qu'il cherche. Il devient faux dans l'autre sens, et
un cliquet qui surcompte se décrédibilise aussi sûrement qu'un qui sous-compte (ADR 2867).

**Un test qui vérifie mécaniquement que tout objet exclu est compté ailleurs.** Séduisant, et hors de
portée : les détecteurs inspectent des choses trop différentes pour qu'un cadre commun sache ce que
« compter le même objet » signifie. La tentative produirait un garde-fou qui a la forme du succès sans en
avoir la substance - exactement ce que l'ADR 2941 décrit à propos des zones muettes.
