---
type: adr
title: "Trois bancs de mutation partagent ce qu'ils rendent, pas leur mécanique"
status: stable
article: A2
chantier: "#5265"
decided_at: 2026-10-06
verification: humaine
verification_note: "savoir si un écart entre deux bancs est une raison ou un oubli demande de lire ce que chacun mute et comment il le lance : aucun motif ne le lit. Ce qui se tient mécaniquement est la part commune, par l'auto-test de chaque banc et par le cas du banc de méthode qui compare leurs trois déclarations"
enforced_by: []
relations:
  complete: ["4490-un-temoin-se-prouve-par-mutation-mecaniquement", "4770-la-mutation-se-transpose-par-le-point-d-entree", "5257-un-rouge-par-plantage-ne-prouve-rien", "5743-un-invariant-se-borne-par-une-liste-nommee"]
verified:
  - by: humain
    at: 2026-10-06
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-06
---

# Trois bancs de mutation partagent ce qu'ils rendent, pas leur mécanique

## Le contexte

Le dépôt porte trois bancs de mutation depuis #5254, pour `scripts/adr`, `scripts/methode` et
`.github/scripts`. Chacun neutralise la détection d'un garde, puis regarde si son auto-test le voit.

À l'ouverture du chantier #5265, deux rangeaient un plantage avec les preuves, et deux cherchaient
le point d'entrée d'un garde dans son texte. Le dépôt annonçait 109 gardes éprouvés, il en prouvait 90.

Ses lots les ont alignés, et la même question est revenue : ce qu'on écrit trois fois, faut-il
l'écrire une fois ? Et ce que les trois font différemment, faut-il l'unifier ?

## La décision

Ce que les trois bancs ont en commun est ce qu'ils rendent, et c'est cela qui s'aligne. Leur mécanique
reste propre à chacun, et chaque écart porte sa raison là où il est écrit.

| Ce qui est commun aux trois | Depuis |
|---|---|
| le point d'entrée où s'insère la neutralisation se lit dans l'arbre syntaxique, jamais par un motif | #5263 |
| trois comptes dont la somme vaut la population ([ADR 5257](5257-un-rouge-par-plantage-ne-prouve-rien.md)) | #5264 |
| la déclaration `invariant`, seuil « sans objet » | #5498 |
| la table `PLANTENT_SOUS_MUTATION`, confrontée dans les deux sens ([ADR 5743](5743-un-invariant-se-borne-par-une-liste-nommee.md)) | #5743, #5497 |
| ce qui épargne une fonction, dans `scripts/_commun/mutation.py` ([ADR 5452](5452-une-exemption-se-derive-de-ce-que-la-chose-fait.md)) | #5524 |

| Ce qui reste propre à chacun | ADR | méthode | CI |
|---|---|---|---|
| d'où vient le corpus | ce que `verifie_scripts.py` charge, plus les autonomes | ce que `lint.yml` lance sous `scripts/`, hors `scripts/adr` | ce que les ateliers nomment |
| le banc est-il dans son corpus | par sa moitié chargée, jamais par celle qu'il lance | oui ([ADR 4770](4770-la-mutation-se-transpose-par-le-point-d-entree.md)) | non |
| ce qui borne le second sens de la table | la portée du diff | rien | les outils du poste, `NE_JOUENT_QU_AVEC` |
| ses exemptions nommées | `HORS_PORTEE` (#5495) | `HORS_PORTEE` (#5479) | aucune |

La seconde ligne a trois raisons. Le banc des ADR lance les scripts qu'il éprouve et se rappellerait
sans fin. Celui de méthode ne mute que de faux gardes dans son auto-test, donc il peut se muter. Celui de CI s'écarte de son corpus, et un cas le tient. Depuis #5550, celui de
méthode le dit aussi par un cas qui peut rougir ; celui des ADR le tient par conséquence.

La confrontation de la table est écrite trois fois. Son noyau tient en deux différences d'ensembles,
et ce qui l'entoure diffère : la borne du second sens, et la forme des non concluants.

## Les conséquences

Trois fonctions du même nom ne sont pas une duplication à réduire : la lecture du point d'entrée
est identique dans deux bancs et diffère dans le troisième, la confrontation diffère dans les trois.

Un écart entre deux bancs se lit avec sa raison, ou se consigne comme une trouvaille. Les aligner
« par cohérence » retirerait au banc des ADR sa barrière contre le rappel sans fin, ou ferait rougir
la table du banc de CI sur tout poste qui porte ImageMagick.

Ce qui se paie : une règle neuve s'écrit trois fois, et la troisième s'oublie (#5263, #5495, #5550).
Le remède retenu est que chaque banc porte son cas rouge, non que le code soit commun.

## Ce que cette décision ne couvre pas

Elle ne dit pas que rien ne se partagera. Un fonds a été refusé en #5263 parce que les bancs ne
partageaient alors ni import ni arbre ; depuis #5524 ils importent tous trois
`scripts/_commun/mutation.py`, et cet argument ne vaut plus. Reste l'autre : on ne partage que ce qui
est le même.

Elle ne répare pas les gardes que les tables nomment : le chantier l'a exclu.

## Les alternatives écartées

- Un quatrième banc pour la racine de `scripts/`, où vit la porte : le banc de méthode dérivait déjà
  son corpus de `lint.yml`, et c'est son motif qui tenait moins que sa prose (#5397).
- Une seule réponse à « le banc est-il dans son corpus » : rien n'oblige les trois à répondre pareil,
  mais chacun doit répondre.

## Comment on le sait

Le job `temoins` sur `main` à `e8682ff5f` : 50 tiennent et 4 ne concluent pas sur 54 pour le banc
des ADR, 20 et 10 sur 30 pour celui de méthode, 39 et 9 sur 48 pour celui de CI. Aucun
décoratif, et chaque somme vaut sa population. Deux des 39 n'ont rien prouvé sur ce runner : leur
auto-test refuse de commencer sans ImageMagick ou ffmpeg, et le banc lit ce refus comme un rouge qui
tient (#5497).

Les fonctions homonymes ont été comparées par leur arbre à `0467bca9f`.
