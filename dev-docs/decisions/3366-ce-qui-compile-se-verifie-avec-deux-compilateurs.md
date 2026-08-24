---
type: adr
title: "Ce qui compile se vérifie avec deux compilateurs"
status: stable
article: A4
chantier: "#3366, suite de #3228, lot #2724 du chantier #2720"
decided_at: 2026-08-06
verification: certaine
enforced_by:
  - ".github/workflows/maven.yml"
verified:
  - by: machine:ci
    at: 2026-08-06
---

# Ce qui compile se vérifie avec deux compilateurs

## Contexte

`javac` n'est pas la norme du langage, c'en est **une** mise en œuvre. #3228 l'a coûté cher : une
lambda visant `com.google.inject.Provider` - interface à méthode unique, mais **non annotée**
`@FunctionalInterface` - que `javac` accepte et qu'**ecj refuse**.

Le défaut ne se manifeste pas là où on l'attend. La compilation Maven passe ; c'est l'**IDE** qui
écrit ses classes en erreur dans le **même** `target/classes`, et le `./mvnw test` suivant échoue **à
l'exécution**, sur des tests sans rapport, avec un message qui ne nomme jamais la cause
(`Unresolved compilation problem`, `Could not inject spec`). Une occurrence a produit **133 erreurs**.
Le remède, `./mvnw clean`, doit être redécouvert à chaque fois.

## La mesure, avant la décision

L'issue exigeait de mesurer plutôt que de croire. `ecj 3.46.0` + `plexus-compiler-eclipse 2.16.2`,
`--release 25`, sur les sources **et** les tests :

| | |
|---|---|
| Divergences **réelles** | **4** |
| dont manquées par le balayage textuel de #3228 | **2** |
| Avertissements ecj | **1293** |
| Erreurs après correction | **0** |

Deux familles, et la seconde n'avait pas de nom :

- **lambda visant un `Provider`** : deux occurrences dans `CapturePassage`. Corrigées en **classe
  anonyme** et non par `Providers.of`, qui évaluerait dans `configure()` alors que le fournisseur n'a
  de sens qu'à l'injection ;
- **capture de générique sur `map(...).toList()`** : ecj infère `Stream<Map<String, capture-of ?>>` là
  où `javac` assigne sans broncher à `List<Map<String, ?>>`. Deux occurrences, corrigées par un
  **témoin de type explicite**.

⚠️ La quatrième n'est apparue qu'**après** avoir mis `module-info.java` de côté : ecj s'arrêtait avant
d'atteindre les sources de test. Une mesure interrompue n'est pas une mesure.

## Décision

**Le dépôt compile avec deux compilateurs conformes, et les deux doivent accepter le code.** Un
troisième job de `maven.yml`, en parallèle des deux autres, rejoue `./mvnw -Pecj clean test-compile` -
ni tests ni couverture, seule la compilation.

Ce sont les **deux occurrences manquées** qui tranchent le choix que l'issue laissait ouvert. L'autre
option était une garde textuelle sur la forme connue ; or le balayage textuel avait **déjà tourné** sur
cette forme précise, en #3228, et il en avait laissé deux. **Une garde ne voit que ce qu'on lui a
appris ; un compilateur voit ce qu'il refuse.**

## Conséquences

- une construction qui ne passe qu'avec `javac` est refusée avant d'atteindre `main`, donc avant
  d'empoisonner le `target/classes` de quelqu'un ;
- **seules les erreurs bloquent.** Les 1293 avertissements ne sont pas des défauts, et un job qui
  rougirait dessus serait désactivé en trois semaines ;
- deux dépendances de plus, dans un profil **hors build par défaut**. Leurs versions vivent dans le
  `pom.xml`, **où Dependabot les voit** : posées dans un workflow, elles ne seraient surveillées par
  personne - c'est exactement le défaut que l'[ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md)
  et #3382 viennent de traiter.

### ⚠️ `module-info.java` est exclu de cette passe

Sous `plexus-compiler-eclipse`, ecj ne résout ni les modules automatiques (`com.google.gson`,
`info.picocli`) ni `org.xerial.sqlitejdbc`, et rendait **six erreurs qui ne disent rien du code** ;
`useModulePath=false` n'y change rien. Le profil exclut donc `module-info.java` et compile sur le
classpath.

C'est un **renoncement assumé** et non un oubli : `javac` vérifie le module à chaque build, ce second
avis n'a pas à le refaire. À rouvrir si `plexus-compiler-eclipse` apprend les modules automatiques.

### Le job porte son propre contrôle de non-vacuité

Une faute dans le profil ferait retomber la compilation sur **javac**, et le job resterait **vert** en
n'ayant rien comparé. L'étape exige donc de voir **deux** passes `Compiling with eclipse` dans son
journal, une par jeu de sources, et échoue sinon en le disant. Vérifié dans les deux sens : **2** avec
`-Pecj`, **0** sans.
