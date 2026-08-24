---
type: adr
title: "« Tourner sans conclure » a trois formes, et la mesure les départage"
status: stable
article: A3
chantier: "#3560, lot 2 du chantier #3518"
decided_at: 2026-08-13
verification: certaine
enforced_by:
  - ".github/scripts/mesure-duree-portail.sh"
verified:
  - by: machine:ci
    at: 2026-08-13
---

# « Tourner sans conclure » a trois formes, et la mesure les départage

## Contexte

Le lot s'intitulait « un dispositif qui tourne sans conclure », et visait deux outils. En les ouvrant,
la formule s'est révélée recouvrir **trois pannes différentes**, dont deux que les issues elles-mêmes
n'avaient pas vues.

**Il ne tourne pas du tout.** `dependency:analyze` était donné pour « réussit en signalant cinq
écarts ». Le vrai constat était en dessous : le but n'était lié à **aucune phase** et n'apparaissait
dans **aucun workflow**. Il ne s'exécutait que si quelqu'un le tapait. Le dépôt avait pourtant investi
dans sa configuration - des listes d'exclusions nommant chacune sa raison, écrites en #2747. On avait
soigné le réglage d'un outil que personne n'appelait.

**Il conclut sur une prémisse fausse.** Les deux issues portaient un constat daté, et **les deux se
sont trompées** :

- #3515 donnait `archunit-junit5` pour « probablement un faux positif classique ». Faux :
  `ArchitectureTest` n'importe que l'API *core*, jamais `@AnalyzeClasses`, et un doc-comment dit que
  c'est la convention du projet. L'artefact n'apportait que son moteur inutilisé **et `archunit` en
  transitif** - d'où sa présence des deux côtés du rapport ;
- #3508 annonçait « écart 0,7 min » sur **sept** exécutions et concluait « la variance est faible,
  donc un seuil aurait du sens ». Sur **trente** : médiane 10,9 min, vingt-huit entre 9,7 et 12,1
  (écart-type 0,64), et **deux à 21,8 et 23,7 min**. Sept exécutions ne suffisaient pas à en attraper
  une.

**Il conclut juste, et personne ne peut le vérifier.** Le dispositif écrit pour #3508 est arrivé avec
deux défauts de la forme même qu'il combat : il allait chercher ses propres données, donc aucun test
ne pouvait lui en fabriquer ; et son verdict n'allait que dans le résumé d'exécution, si bien que sa
première exécution en CI a rendu une étape verte au **journal vide**.

## Décision

### Un dispositif qui juge est appelé par une phase, jamais par une bonne volonté

`dependency:analyze-only` est lié à `verify` avec `failOnWarning`. Régler les exclusions d'un outil
sans le câbler produit du travail invisible : la configuration vieillit, personne ne la relit, et le
premier écart neuf s'ajoute au bruit.

### Il s'exerce sur chaque PR, jamais sur `main` seul

Les deux dispositifs du lot tournent sur les PR. Une étape réservée à `main` n'est **jamais jouée
avant sa fusion**, et peut donc être fusionnée cassée. Pour la mesure de durée, rien n'est écrit :
rien n'exigeait de la réserver.

### ⚠️ La garde de dépendances bloque aussi en local, contrairement à PMD et JaCoCo

Le dépôt tient un principe écrit : ces deux-là restent tolérants en build local nu, « la CI fait foi ».
`failOnWarning` s'en écarte, délibérément, pour trois raisons :

1. le coût est de l'ordre de la seconde - `analyze-only` seul prend 4 s, démarrage de Maven compris ;
2. une fois la sortie à zéro, il n'a **aucun faux positif** : le verdict est déterministe, là où PMD
   est lent et JaCoCo dépend d'un seuil de couverture ;
3. surtout, le déplacer sous `-Pquality-gate` le **désactiverait** : le job `build` lance
   `verify` **sans** ce profil, et `lint.yml` n'invoque que le but `pmd:check`. Il ne tournerait
   nulle part - c'est-à-dire retour au point de départ.

### Un seuil se choisit contre la variance mesurée, et la mesure se rejoue

Un butoir sur « médiane + 30 % » aurait rougi deux fois sur trente sans qu'aucune PR soit fautive, et
se serait fait relever au troisième coup. D'où la comparaison de **deux médianes glissantes** - douze
contre douze - insensible à deux valeurs extrêmes. Sur la série réelle, aberrantes comprises, la
dérive vaut +5,3 % pour un seuil à 20 %.

**Et la mesure d'un audit se rejoue avant de s'en servir.** Deux issues sur deux portaient un chiffre
ou une hypothèse démentis quelques jours plus tard. Ce n'est pas un reproche à l'audit : un constat
daté est daté. C'est une étape à faire, pas une confiance à accorder.

### Un dispositif qui juge doit être injectable, sinon il n'est pas éprouvable

Le script de mesure a dû être modifié pour accepter sa série depuis l'extérieur. Écrit sans cette
couture, il n'était vérifiable qu'à la main - et une vérification manuelle ne se rejoue pas. C'est
l'application directe de l'[ADR 3624](3624-un-fait-que-rien-ne-peut-faire-rougir-s-ancre-autrement.md)
à du shell.

### Ce qui a été écarté

**Bloquer sur la durée.** Un rouge sur un runner lent un mardi matin se relève, et le dispositif meurt
de sa première fausse alerte. Il avertit avec la comparaison en clair.

**Un test qui figerait le contenu du `pom.xml`** (« `failOnWarning` vaut bien `true` ») : il
n'éprouverait que lui-même. Le vrai dispositif est le build de la CI, exercé à chaque PR, et il a été
**vu rouge** en retirant une déclaration.

## Conséquences

- `pom.xml` : quatre dépendances de test déclarées, `archunit-junit5` remplacé par `archunit`, sortie
  d'analyse à zéro écart, `failOnWarning` actif.
- `maven.yml` : job `duree-du-portail`, non bloquant, exercé par chaque PR.
- `src/test/bats/scripts-ci.bats` : quatre cas, dont la série réelle du dépôt qui doit rester muette.
- ⚠️ **Un seul script de CI sur onze est éprouvé.** Les dix autres rendent des jugements que rien ne
  vérifie, et deux pannes vécues sur l'un d'eux ne vivent qu'en commentaire. Consigné en #3661.
