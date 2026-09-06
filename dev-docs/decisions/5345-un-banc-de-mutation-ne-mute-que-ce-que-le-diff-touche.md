---
type: adr
title: "Un banc de mutation ne mute que ce que le diff touche"
status: stable
article: A2
chantier: "#5294 (le coût des ateliers), lot #5345"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "scripts/adr/verifie_temoins_non_decoratifs.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# Un banc de mutation ne mute que ce que le diff touche

## Le contexte, et le dépôt est sa propre cause

`verifie_temoins_non_decoratifs.py` neutralise chaque garde du dépôt puis le relance, pour prouver
que son auto-test n'est pas décoratif. Son coût croît donc **linéairement avec le nombre de gardes**.

| | 2026-09-05 | 2026-09-06 |
|---|---|---|
| l'étape qui le porte | 4,67 min, 56 % de `lint` | **6,87 min, 61 %** |
| `lint` | 9,6 min | **11,3 min** |
| `build` | 11,0 min | 8,9 min |

`lint` est ainsi devenu le **chemin critique**, pour les demandes avec Java comme sans. Il ne l'était
pas la veille.

Et la cause est le chantier #5294 lui-même, qui a ajouté quatre gardes au banc de la CI et deux au
banc des ADR. **Le dépôt a rendu `lint` plus lent en le rendant mieux gardé**, et cela recommencera à
chaque garde écrit. C'est une dérive structurelle, pas un accident.

## La décision

**Le banc ne mute que les gardes que le diff touche.**

## Ce que le dépôt accepte de ne pas rejouer, et c'est étroit

Le banc prouve qu'un garde porte un auto-test **non décoratif**. C'est une propriété du **code du
garde** : quand ce code n'a pas changé, elle rend le même verdict que sur la base.

Ce qu'on accepte de ne pas rejouer se nomme donc précisément : *la non-décorativité d'un garde dont
le code est identique à celui que la base a déjà jugé.*

## Les deux replis, sans lesquels la décision serait fausse

**Le fonds partagé fait tout muter.** Un garde peut cesser de rougir à cause d'un module qu'il
importe, sans que son propre fichier ait bougé. Toucher `scripts/_commun/`, le banc lui-même ou le
harnais fait donc muter le corpus entier. La liste est courte et **large à dessein** : se tromper y
coûte des minutes, l'oublier coûte un faux vert.

**Sans base de comparaison, on mute tout.** Le défaut penche du côté coûteux, jamais du côté muet.
C'est le même parti que les portées de job du même chantier.

## Vide par décision, vide par accident

`rapporte` refuse sur `lus=0`, et il a raison : un garde dont la population s'est vidée en silence
reste vert sans juger.

Mais une portée vide n'est pas cet accident. Elle dit « ce diff ne touche aucun garde », ce qui est un
fait lisible, pas une cécité. **Confondre les deux ferait refuser chaque demande documentaire, et
apprendrait à passer outre** - ce qui coûte bien plus cher que la minute gagnée.

Le banc conclut donc et **écrit pourquoi**, sur la forme de l'ADR 2748 : un silence explicite n'est
pas une absence. L'accident reste refusé : si le **corpus entier** est vide, le harnais ne trouve
plus rien, et cela n'a aucun rapport avec le diff.

## Le compte lu est celui de ce qui a été muté

`population` appelait `mutes()` et `autonomes()` **sans** la portée : le banc aurait annoncé
quarante-sept gardes lus en en jouant trois. Un `lus` qui dépasse ce qu'on a lu est le faux vert que
l'ADR 5007 refuse, et c'est exactement le défaut que ce chantier combat ailleurs.

## Comment elle se vérifie

Dix cas d'auto-test, dont les deux bords qui comptent : le fonds partagé fait tout muter, et une
portée vide sur un corpus plein **conclut** là où une portée vide sur un corpus vide **refuse**.

Les deux ont été vus rouges sous mutation : neutraliser la distinction fait rougir « une portée vide
sur un corpus plein conclut », et neutraliser le fonds partagé fait rougir ses deux cas.

## Ce que la décision ne fait pas

Elle ne conditionne pas l'étape de `lint` et n'ajoute aucune portée d'étape. Le lot #5303 proposait
cela ; traiter la cause rend le symptôme sans objet, et sans concept neuf.
