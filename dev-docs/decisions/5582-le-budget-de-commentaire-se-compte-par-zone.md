---
type: adr
title: "Le budget de commentaire se compte par zone, et le test porte le sien"
status: stable
article: A9
chantier: "#5582 (chantier #4650, passe 7)"
decided_at: 2026-09-29
verification: probable
enforced_by:
  - "scripts/adr/4472-commentaire-en-corps.py"
ratchet: 19
relations:
  applique: ["4682-le-portail-compte-chaque-zone-a-part"]
  amende: ["4472-un-commentaire-long-en-corps-de-methode-est-un-signal"]
verified:
  - by: machine:ci
    at: 2026-09-29
generated:
  by: "process:assistance-par-agents"
---

# Le budget de commentaire se compte par zone, et le test porte le sien

## Le contexte

Deux décisions refusent déjà le compteur unique sur deux zones. L'[ADR 4682](4682-le-portail-compte-chaque-zone-a-part.md)
la tranche pour le portail qualité : « un compteur unique laisserait une régression d'un côté se
payer par un gain de l'autre ». L'[ADR 4587](4587-le-plancher-des-renvois-de-test-est-distinct.md) la
refuse pour les renvois, dans les mêmes termes.

**Aucune des deux n'a été appliquée ailleurs.** La passe 7 de la clôture de #4650 a cherché qui en
relevait encore, en croisant les cliquets non nuls du dépôt avec les zones Java que leur gage lit. Il
restait **un** cas.

| ADR | cliquet | zones lues | état |
|---|---:|---|---|
| 4359 | 741 | les deux | hors sujet : `dispositif: invariant`, `seuil: (sans objet)` |
| 4617 / 4682 | 40 / 0 | les deux | déjà séparé, deux `rapporte` |
| 2843 | 1 | test seul | hors sujet |
| **4472** | **43** | **les deux** | **le cas** |

## La mesure qui décide

```
ADR 4472 | lus=2139 | suspects=43 | cliquet=43 | verdict=ok
   production : 24
   test       : 19
```

Le défaut n'est pas hypothétique : les deux zones sont peuplées. Un gain de cinq blocs côté test
payait une régression de cinq blocs côté production, et le cliquet restait vert à 43.

## La décision

**Le comptage se sépare, la règle ne change pas.** L'ADR 4472 garde la production, à 24. Celle-ci
porte la zone de test, à 19. Le garde rend deux lignes de verdict et **sort sur le pire des deux**,
comme le portail depuis l'ADR 4682.

Un seul garde, deux décisions : c'est le patron de `4617-code-mort-et-zone-de-test.py`, recopié
plutôt que réinventé.

## Ce que cela ne dit pas

**Rien n'établit que la norme diffère entre les deux zones**, et cette décision ne le prétend pas. Le
budget de huit lignes de l'ADR 4472 vaut des deux côtés, pour la même raison : un bloc long dit que
le code d'en dessous est trop obscur, qu'une décision est restée là, ou qu'un pan d'histoire n'a pas
été retiré. Aucun de ces trois motifs ne s'affaiblit dans un test.

C'est une différence avec le portail, où l'ADR 4617 écarte **explicitement** les littéraux dupliqués
de la seule zone de test, « où les répéter est le geste juste ». Ici il n'y a pas d'asymétrie de
norme : il y a seulement deux populations qu'un compteur unique laissait se compenser.

Transposer le mécanisme sans transposer sa justification aurait produit une ADR qui affirme une
asymétrie que personne n'a mesurée.

## Les alternatives écartées

- **Laisser le compteur unique et l'assumer.** Il aurait fallu écrire pourquoi la compensation entre
  zones est acceptable ici et pas ailleurs. Aucune mesure ne le soutient.
- **Deux gardes séparés.** Ils reliraient deux fois les mêmes 2 139 fichiers, pour un coût déjà
  mesuré à 1,13 s par demande. Un garde, deux verdicts.
- **Un cliquet unique à polarité par zone.** Le dispositif ne le porte pas, et l'inventer pour un
  seul cas aurait ajouté une forme que rien d'autre ne lit.

## Comment on le sait

`scripts/adr/4472-commentaire-en-corps.py` rend deux lignes, `ADR 4472` et `ADR 5582`, et son code de
sortie est le maximum des deux. Une régression dans une zone fait donc rougir même si l'autre a
gagné, et c'est exactement ce que le cliquet unique ne pouvait pas faire.
