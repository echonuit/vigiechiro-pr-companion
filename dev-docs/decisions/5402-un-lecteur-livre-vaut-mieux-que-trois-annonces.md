---
type: adr
title: "Un lecteur livré vaut mieux que trois annoncés"
status: stable
article: A9
chantier: "#5402 (les gardes lisent le Java par motif), décision de clôture"
decided_at: 2026-09-07
verification: humaine
enforced_by: []
loupe: "scripts/adr/loupe-4992-lots-sans-critere.py"
verified:
  - by: humain
    at: 2026-09-07
generated:
  by: "process:assistance-par-agents"
---

# Un lecteur livré vaut mieux que trois annoncés

## Le contexte

Le chantier #5402 annonçait **trois lecteurs** dans `scripts/_commun/`, chacun pour ce qu'il sait
faire, et quatre lots pour les poser :

| module | lecteur | ce qu'il sait faire |
|---|---|---|
| `arbre.py` | tree-sitter | la structure d'une source |
| `attache.py` | JavaParser via JPype | attacher un commentaire à ce qu'il documente |
| `index.py` | Spoon, via `target/index-*.json` | répondre à une question qui traverse le corpus |

À la clôture, **un seul existe**. `attache.py` et `index.py` sont absents, `jpype1` n'est pas déclaré,
`pom.xml` ne porte pas Spoon, et aucune issue ne portait les deux sous-chantiers : ils vivaient dans
des cases à cocher du corps de l'EPIC.

La passe 0 pose la question que le reste du cycle ne pose nulle part : ce qui était **promis**
a-t-il été livré ? La réponse était non, et un « non » n'a que deux issues, livrer ou décider de ne
pas livrer.

## La décision

**Les deux sous-chantiers sortent en EPIC indépendants, et #5402 se clôt sur ce qu'il a livré.**

- **#5463** porte l'attachement, et les gardes de javadoc que `4468` mène.
- **#5464** porte l'index, et le garde qui prouvera qu'il répond à une question que les deux autres
  lecteurs ne savent pas poser.

Aucun des deux n'est rattaché à #5402 par `--parent` : un EPIC fermé ne porte pas d'enfants ouverts.
C'est la forme qu'a prise le même geste à la clôture de #4925, dont le lot 3 est sorti en #4937 sans
parent.

## Pourquoi ne pas avoir livré

**Parce que la valeur du chantier ne dépendait pas des trois.** `arbre.py` a suffi à migrer six
gardes et à retirer une approximation que l'ADR 4472 déclarait depuis son écriture. Ce que les deux
autres apportent est réel et sépare mal : l'attachement sert une famille de gardes, l'index une
question que personne ne pose encore.

**Et parce que les tenir dans un seul EPIC les aurait faits attendre.** Le sous-chantier C annonce
« trois PR au moins », touche `pom.xml`, un extracteur Spoon, un `setup-java` et un build en CI.
Garder #5402 ouvert le temps de cela aurait laissé un chantier courir des semaines sur un travail
déjà livré, et le corps de l'EPIC aurait continué d'annoncer trois lecteurs à qui le lit.

## Ce que la décision coûte, et qui doit être su

**Le corps de l'EPIC #5402 restera faux après sa fermeture** : il annonce trois lecteurs, un tableau
de quatre lots, et il n'y a pas de geste pour réécrire un corps d'EPIC clos. C'est le prix assumé, et
c'est pourquoi cette ADR existe : elle est l'endroit où un lecteur qui bute sur l'écart trouve la
raison.

**Le travail de spike ne se perd pas.** Les mesures qui ont coûté cher - Spoon acceptant l'arbre
entier une fois `module-info.java` écarté, le pont JPype à 0,37 s pour 14 913 méthodes, le coût CI de
18 s dans le job qui compile déjà - sont **transportées** dans le corps des deux EPIC plutôt que
laissées dans celui de #5402. Une mesure qui ne survit pas à la clôture du chantier qui l'a faite
sera refaite.

## Alternative écartée

**Clore #5402 en laissant les deux lots dans leurs cases à cocher.** C'est la troisième voie que la
méthode nomme et refuse : la promesse reste à moitié tenue sans que personne ne l'ait décidé. Elle
est la voie par défaut, celle qu'on prend en ne faisant rien, et elle ne laisse aucune trace de la
décision - donc aucune façon, plus tard, de distinguer un abandon d'un oubli.

Un lot dans une case à cocher n'a de critère de fin nulle part, et
`scripts/adr/loupe-4992-lots-sans-critere.py` ne peut pas le voir : il compte les **sous-issues**
ouvertes sans critère, pas les cases. C'est ce qui rend cette forme d'oubli invisible aux
dispositifs, et c'est pourquoi la loupe est nommée ici comme le regard qui s'en approche le plus
sans y atteindre.
