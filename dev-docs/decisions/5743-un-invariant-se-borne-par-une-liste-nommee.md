---
type: adr
title: "Un invariant se borne par une liste nommée, jamais par un cliquet"
status: stable
article: A9
chantier: "#5743 (quatre gardes plantaient sous mutation sous un verdict=ok), lot de #5768"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/adr/verifie_temoins_non_decoratifs.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  prolonge: ["4682-un-cliquet-unique-sur-deux-zones-laisse-une-regression-se-payer"]
  complete: ["5774-un-refus-ne-voyage-pas-dans-le-canal-des-constats", "5257-un-rouge-par-plantage-ne-prouve-rien"]
generated:
  by: "process:assistance-par-agents"
---

# Un invariant se borne par une liste nommée, jamais par un cliquet

## Le contexte

`verifie_temoins_non_decoratifs.py` classe le rouge d'un garde muté en trois états : `decoratif`,
`tient`, et `non concluant`, ce dernier étant un plantage. Seuls les décoratifs entrent dans ses
suspects, et c'est juste : un plantage ne prouve pas qu'un témoin est décoratif
([ADR 4918](4918-un-cas-rouge-pour-la-mauvaise-raison-ne-prouve-rien.md)).

Rien ne bornait les non concluants : quatre gardes n'avaient rien établi pendant que la dernière
ligne du banc, celle qu'une boucle de cliquets lit, disait `verdict=ok`. Le réflexe était d'ajouter un
second cliquet, et l'issue le supposait.

## Ce qui a été mesuré

Les quatre plantages sont stables à quatre jours d'intervalle, aux mêmes gardes et aux mêmes
erreurs. Et le contrat de ce banc portait déjà sa réponse :

```python
# ⟨`invariant` et non `cliquet` (#5498)⟩ Le critere est ecrit dans `dev-docs/ci-cd-release.md` :
# « c est un invariant, pas un cliquet : il n y a pas de marge a relever, et l echappatoire est
# une liste d exceptions NOMMEES ».
"dispositif": "invariant",
"seuil": "(sans objet)",
```

Un cliquet à quatre aurait autorisé d'échanger un plantage réparé contre un plantage neuf **sans
qu'aucun chiffre bouge** : la défaillance que l'ADR 4682 décrit, un cran plus bas, les deux zones
étant ici deux **états** d'une même population.

## La décision

**Un dispositif qui se déclare `invariant` borne sa population résiduelle par une liste nommée, et
cette liste se vérifie dans les deux sens.**

| Ce qu'on observe | Ce que le dispositif fait |
|---|---|
| un membre résiduel absent de la liste | il rougit : c'est un de plus, et rien ne le bornait |
| une entrée de la liste qui n'est plus résiduelle | il rougit : l'entrée est périmée et se retire |
| la liste décrit exactement le résidu | il passe, et nomme chaque membre avec sa raison |

Le second sens est celui qu'un compteur n'aurait pas : un cliquet reste **vert** quand un membre
disparaît, donc il perd l'information au moment où elle est bonne. Et la valeur de chaque entrée est
sa **raison**, donc la liste se relit comme une classification plutôt que comme un total.

**Et le second sens n'est jugé que sur la population entière.** Un dispositif qui se restreint à ce
que le diff touche ne voit pas les membres non visités : sur une portée partielle, tous paraîtraient
réparés, et il rougirait en annonçant des réparations imaginaires. Le premier sens, lui, vaut
toujours.

## Et le verdict voyage par le canal des CONSTATS

Un écart de liste est un **constat** : le dispositif a parcouru sa population et comparé. Il a jugé,
donc il sort en `1` par le canal de l'[ADR 5774](5774-un-refus-ne-voyage-pas-dans-le-canal-des-constats.md).

**Cette décision la complète sur un point que le code de sortie ne porte pas.** Mesuré sur
`verdict_du_lancement` de `scripts/batterie.py` :

```
code=1 AVEC « REFUS : » et « POUR REPARER : »  ->  muet
code=1 SANS ces marques                        ->  rouge
```

La porte classe par les **marques**. Un dispositif qui a jugé et emploie `refuse(..., code=1)` est
donc annoncé « n'a PAS pu juger ». **Un constat rouge n'emploie donc ni `refuse` ni
`message_de_refus`**, et le cas qui le tient regarde la **sortie**, non le code. Vécu ici par un
premier jet poussé en demande : 28 checks verts, un dispositif passant tant que sa liste concorde.

## Ce que cela ne couvre pas

**Elle n'oblige pas à réparer le résidu**, borner n'étant pas vider : les quatre plantages de ce banc
ne sont pas le même défaut, donc quatre réparations sont quatre lots.

**Et elle ne s'applique pas à un cliquet**, dont la marge existe parce que sa population croît avec le
dépôt. La règle porte sur ceux qui déclarent `invariant`, dont le seuil est « sans objet ».

## Les alternatives écartées

**Un second cliquet**, écarté par la mesure ci-dessus : il échange un gain contre une régression.

**Le même cliquet, élargi aux deux états**, écarté par l'ADR 4682 elle-même, qui nomme exactement
cette faute.

**Faire refuser le dispositif tant que le résidu n'est pas vide**, écarté par arbitrage du porteur :
pris au mot, le critère d'ouverture de #5743 rendait la CI du dépôt rouge pendant quatre lots de
réparation. Le blocage aurait été un choix défendable, et il aurait dû être choisi, non subi.

## Comment on le sait

Six mutations, chacune tuée par l'ensemble de cas **déclaré** dans l'instrument, qui compare les deux
ensembles plutôt que de laisser lire une liste de noms. Dix cas joués à chaque tour : un harnais qui
n'en joue aucun rend zéro rouge, et zéro rouge se lit « survivante ».
