---
type: adr
title: "Un refus déclare sa forme, et la porte ne la devine plus"
status: stable
article: A3
chantier: "#5533 (la porte rend un verdict complet sur une population qu'elle ne couvre pas)"
decided_at: 2026-09-29
verification: certaine
enforced_by:
  - "scripts/batterie.py"
verified:
  - by: machine:ci
    at: 2026-09-29
relations:
  amende: ["5475-aucune-position-n-est-fiable-dans-un-refus"]
  complete: ["5398-une-exemption-se-declare-elle-ne-s-infere-pas", "5407-un-verdict-local-porte-sur-le-code-pas-sur-le-poste"]
generated:
  by: "process:assistance-par-agents"
---

# Un refus déclare sa forme, et la porte ne la devine plus

## Le contexte

L'ADR 5475 a tranché qu'**aucune position n'est fiable** dans le refus d'un garde : sur trois refus
réels, deux commencent par la cause et le troisième par un titre. Elle en a conclu qu'il fallait
**montrer plus** - les deux premières lignes non vides - plutôt que d'inférer une structure que les
gardes ne partagent pas.

Cette conclusion était juste, et elle assumait sa limite dans son propre texte : le geste de
`4617-code-mort-et-zone-de-test.py` vit en **troisième** ligne, et restait invisible.

## La mesure qui décide

Le 2026-09-29, sur les quatre dossiers de gardes, en dérivant de ce que le garde **fait** - sortir non
nul - plutôt que d'un motif de texte :

```
gardes pouvant sortir non nul : 130
  code 2 : 28 sites, dans 16 gardes      code 1 : 22 sites
```

Le `2` est donc la convention du dépôt, non écrite jusqu'ici. Trois codes divergent et sont nommés :
un `sys.exit(64)`, deux `sys.exit(3)`, et un `SystemExit` qui sort une **chaîne**, donc en 1.

**Le chiffre lui-même n'existait pas avant cette mesure.** Les deux tentatives de #5475 cherchaient un
motif de texte et ont rendu 13 gardes - dont une fixture YAML et un exemple de docstring - puis 0.

## La décision

**Un refus déclare sa cause et son geste ; la porte les lit, où qu'ils soient.**

La prémisse de 5475 - « les gardes ne partagent pas la structure de leur message » - n'est pas
réfutée, elle est **retirée** : on leur en donne une. Ce qui change n'est pas le nombre de lignes
montrées, c'est **qui décide lesquelles**. La position hier, le garde lui-même désormais.

Deux moitiés, parce que les gardes n'ont pas tous le même flot :

```python
refuse(cause, geste)            # ecrit la forme et sort en 2
message_de_refus(cause, geste)  # construit la forme, sans sortir
```

Imposer la première aux gardes qui rendent `(code, message)` à leur appelant aurait changé leur flot
de contrôle pour une question de forme.

## Ce qui reste de l'ADR 5475, et ce n'est pas un vestige

**Le repli à deux lignes est la règle pour tout garde qui n'a pas la forme**, c'est-à-dire la plupart.
`lit_le_refus` rend `None` plutôt que de deviner, et c'est ce `None` qui permet de convertir les
gardes **un à un** sans fausser la porte entre-temps.

Une décision qui aurait exigé la forme partout aurait demandé de convertir 130 gardes en une fois,
ou de mentir sur ceux qui ne l'ont pas.

## Les alternatives écartées

- **Deviner la position à partir de la ponctuation** - « la deuxième ligne quand la première finit par
  deux points ». C'est l'inférence que 5475 refuse et que 5398 a refusée avant elle, sur cette porte
  même.
- **Normaliser les 130 d'un coup.** Le coût est mesuré et la conversion se fait garde par garde ; le
  repli existe pour ça.
- **Exiger le code 2 partout.** Trois codes divergent et deux vivent dans des scripts d'assets que ce
  lot ne touche pas. Les nommer vaut mieux que les convertir sans les avoir lus.

## Comment on le sait

Trois cas dans l'auto-test de la porte, dont **deux contrastes** : un geste déclaré se lit même en
cinquième ligne ; un refus non déclaré garde le repli mot pour mot ; une cause sans geste retombe sur
le repli. Sans les deux derniers, une lecture qui devinerait la position passerait le premier, et une
forme à moitié écrite rendrait un geste vide - pire que le repli, puisque le lecteur croirait avoir
tout vu.
