# ADR 0039 — Une barre de statut dit où l'on en est, pas si c'est bien ou mal

- **Statut** : Accepté — 2026-07-20
- **Chantier** : EPIC #1990 / sous-EPIC #2004 (passe d'harmonisation de la clôture)
- **Complète** : [ADR 0035](0035-un-pictogramme-est-une-icone-pas-un-caractere.md) et [ADR 0038](0038-l-echelle-de-severite-a-quatre-niveaux.md), en donnant à la quatrième surface de restitution la règle qui lui manquait.

## Contexte

L'audit d'harmonisation de #2004 a recensé **quatre** surfaces par lesquelles l'application s'adresse à l'utilisateur. Trois portent un type qui sait dire une sévérité ; la quatrième, la plus employée, n'en porte aucun :

| Surface | Type | Fichiers |
|---|---|---|
| `BandeauRetour` | `RetourOperation` | 17 |
| `VueCompteRendu` | `CompteRendu` | 9 |
| `LibelleRetour` | `RetourOperation` | 3 |
| **`ZonesStatut`** | **`(String, String, String)`** | **20** |

Ce déséquilibre n'est pas resté théorique. Ce chantier a produit, dans son propre code et pendant le lot qui interdit ce motif :

```java
return String.format("✓ Import terminé : %d séquence(s) produite(s)…", …);
```

La coche est allée dans la chaîne parce que, dans une barre de statut, **c'est le seul moyen de qualifier quoi que ce soit**. C'est le mécanisme décrit par l'ADR 0038 - quand un type ne sait pas exprimer un cas, le cas sort du type - observé un cran plus bas : ici il n'y a même pas de type dont sortir.

La garde de #2075 ne pouvait pas l'attraper : elle protège `RetourOperation`, et ceci est un `String`.

## Décision

**Une barre de statut est neutre par nature.** Elle dit **où l'on en est** - « Import terminé : 412 séquence(s) », « Décompression : 740 / 3692 », « Opération annulée » - pas si c'est une bonne ou une mauvaise nouvelle.

Trois conséquences :

**1. Ses phrases s'écrivent sans marqueur.** Pas de `✓`, pas de `⚠`. « Import terminé » se suffit ; le mot *terminé* porte déjà l'information que la coche redoublait.

**2. Ce qui doit alerter n'y va pas.** Un avertissement ou un échec passe par un **bandeau** (retour d'opération) ou un **encart** (commentaire attaché à un champ ou une section). Ces deux-là savent dire une sévérité, en couleur et en forme.

**3. `ZonesStatut` reste sur des `String`.** C'est délibéré, et c'est la raison pour laquelle cette ADR existe plutôt qu'un refactoring : lui donner un type de sévérité produirait un **second canal d'alerte** concurrent du bandeau, coloré en permanence là où la barre doit rester discrète. L'absence de type n'est plus un manque, elle est la forme de la règle.

## Conséquences

La barre de statut d'import perd sa coche (clôture #2004).

`DiagnosticViewModel.alerteHorsNuit` alimente aujourd'hui la barre **et** un encart. Le premier usage relève désormais de la règle 2 : c'est un état qui commente une nuit, sa place est l'encart seul. Non traité ici - l'écran Diagnostic sort du périmètre de ce chantier, et le point est tracé dans #2050.

## Ce que la règle ne dit pas

Elle ne prétend pas qu'une barre de statut ne peut jamais rien signaler d'anormal. « Opération annulée » y a sa place : c'est un **état d'avancement**, pas un jugement. La frontière est là - ce qui décrit une position dans un déroulement contre ce qui qualifie un résultat.
