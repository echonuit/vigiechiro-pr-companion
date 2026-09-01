---
type: adr
title: "Le compte lu vient de l'arbre visé, pas de ce que la source en rapporte"
status: stable
article: A3
chantier: "#5053, sous-chantier du compte lu (#5015)"
decided_at: 2026-09-01
verification: certaine
enforced_by:
  - "scripts/adr/verifie_scripts.py"
verified:
  - by: machine:ci
    at: 2026-09-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-01
---

# Le compte lu vient de l'arbre visé, pas de ce que la source en rapporte

## Contexte

Le champ `lus` dit ce qu'un garde a LU, quand `suspects` ne dit que ce qu'il a RETENU, et `lus=0`
refuse. Pour un garde qui balaie un arbre, la réponse est évidente : le nombre de fichiers parcourus.

`4617-code-mort-et-zone-de-test.py` ne balaie pas d'arbre. Il lit `target/pmd.xml`, et rend deux
verdicts, un par zone. La réponse évidente aurait été de compter les entrées du rapport.

**Elle était fausse, et la mesure l'a montré.** Le rapport porte 425 balises `file` et 1 493
violations, **toutes en zone de test**. La production n'y a aucune entrée, non parce que PMD ne l'a
pas lue, mais parce qu'elle est propre : `includeTests=true`, et les huit règles du jeu sont
générales. PMD ne liste que les fichiers fautifs.

Compter les entrées aurait donc donné `lus=0` en production, et **fait refuser une zone
irréprochable**.

## Décision

**`lus` compte les unités vers lesquelles le garde a été POINTÉ, non celles que sa source lui
rapporte.**

Pour `4617`, ce sont les fichiers Java de la zone analysée : 1 226 en production, 854 en test. Le
verdict dit désormais `ADR 4682 | lus=1226 | suspects=0`, soit « PMD a lu 1 226 fichiers de
production et n'y a rien trouvé », là où le zéro seul ne se distinguait pas de « PMD n'a pas tourné
sur cette zone ».

La règle se généralise : dès qu'un garde lit une source **dérivée** (un rapport, un manifeste, un
index), la population de la source n'est pas la sienne. Une source qui ne rapporte que les
anomalies a une population vide quand tout va bien.

## Conséquences

- `fichiers()` de `4617` prend une racine, sans quoi sa zone n'était pas videable et son refus pas
  éprouvable. Le cas des quatre familles s'appuie dessus.
- Le compte et le nombre de suspects peuvent porter des unités différentes, et c'est voulu : 1 226
  fichiers lus pour 0 violation retenue.
- Un garde qui déclarerait la population de sa source rougirait le jour où cette source serait
  vide **parce que tout va bien**. C'est le mode de panne que cette décision écarte.

## La cécité déclarée

Rien ne vérifie mécaniquement qu'un `lus` compte la bonne population. Un garde peut déclarer un
nombre plausible pris au mauvais endroit, et son verdict paraîtra sain. Ce que le dépôt tient, c'est
que le nombre EXISTE et qu'un zéro refuse ; que ce soit le bon nombre reste une lecture.
