---
type: adr
title: "Un témoin qui n'affirme que des vides ne prouve rien"
status: stable
article: A2
chantier: "#5054, sous-chantier du compte lu (#5015)"
decided_at: 2026-09-01
verification: certaine
enforced_by:
  - "scripts/adr/verifie_temoins_non_decoratifs.py"
verified:
  - by: machine:ci
    at: 2026-09-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-01
---

# Un témoin qui n'affirme que des vides ne prouve rien

## Contexte

L'[ADR 4490](4490-un-temoin-se-prouve-par-mutation-mecaniquement.md) rend l'article A2 mécanique :
un garde est neutralisé, la suite doit rougir. La neutralisation remplace chaque fonction de module
par `lambda *a, **k: []`.

Le lot #5054 devait éprouver le refus sur population vide sur un garde réel de chaque famille. Ses
premiers cas affirmaient une seule chose par famille : « quand on vide sa population, le garde rend
zéro ». Ils passaient.

**Ils étaient décoratifs.** Sur un garde neutralisé, tout rend zéro : l'assertion restait vraie. Ces
cas ne distinguaient pas « le garde a correctement trouvé zéro » de « le garde est mort et trouve
toujours zéro ». C'est le faux vert que le chantier existait pour tuer, reproduit dans le cas écrit
pour l'éliminer.

`verifie_temoins_non_decoratifs.py` les a trouvés, avec `suspects=2` : la batterie rendait six rouges
au lieu des cinq de référence.

## Décision

**Un cas qui éprouve une population affirme d'abord qu'elle DISCRIMINE, puis qu'elle se vide.**

Une assertion de vacuité seule est indistinguable d'une fonction morte. Le contraste est ce qui la
rend témoin : la population lue sur le dépôt réel doit être non vide, et c'est cette assertion-là qui
rougit sous mutation.

```python
_verifie("0008 sur le depot lit des fichiers", len(fichiers()) > 0, True)   # discrimine
_verifie("0008 sur un arbre vide n a rien lu", len(fichiers(vide)), 0)      # se vide
```

## Conséquences

- Les quatre familles de population du chantier ont chacune leur paire, et chacune a été **vue
  rouge** en mutant la fonction de population du garde qu'elle éprouve.
- Le corpus de l'ADR 4490 passe de 27 à **29 gardes**, `loupe-4712-lots-multi-pr.py` et
  `verifie_temoins_non_decoratifs.py` entrant dans le contrôle par le fait d'être chargés.
- La règle vaut au-delà du champ `lus` : tout témoin dont l'assertion est « c'est vide » demande son
  contraste.

## La cécité déclarée

Rien ne distingue mécaniquement une assertion de vacuité d'une autre. Ce que le garde des témoins
attrape, c'est le cas décoratif dans son ensemble, pas la ligne fautive : il dit qu'un garde survit à
sa neutralisation, et c'est au lecteur de voir que ses assertions ne discriminent pas. La règle
ci-dessus est ce qu'il faut savoir pour lire ce verdict.
