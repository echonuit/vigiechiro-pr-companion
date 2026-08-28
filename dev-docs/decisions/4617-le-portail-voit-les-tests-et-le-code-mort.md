---
type: adr
title: "Le portail regarde les deux zones, et le code mort compte"
status: stable
article: A9
chantier: "#4617 (sas des suites #4562)"
decided_at: 2026-08-27
verification: probable
enforced_by:
  - "scripts/adr/4617-code-mort-et-zone-de-test.py"
ratchet: 55
verified:
  - by: machine:ci
    at: 2026-08-27
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-27
---

# Le portail regarde les deux zones, et le code mort compte

## Contexte

Une méthode privée jamais appelée a vécu dans `SonsValidationArchiveViewTest`, à soixante-quinze
lignes de la version correcte du même helper, et rien ne l'a vue (#4554). Deux trous l'expliquent,
et les confondre conduit à un correctif qui ne corrige rien.

**Le jeu de règles.** `pmd-ruleset.xml` ne portait que sept règles, toutes de conception. Aucune
`Unused*`. Le job `analyser-ecj` compile pourtant les tests, mais ne configure aucun avertissement
de ce genre : il ne cherche que les divergences entre javac et ecj. Le code mort n'était donc
couvert **nulle part**, production comprise.

**L'assiette.** `<includeTests>false</includeTests>` laissait dehors 143 052 lignes contre 127 814,
soit 52 % du Java du dépôt.

Le défaut observé relève du premier. Corriger le second seul aurait donné le sentiment d'avoir
fermé le trou sans rien fermer, et c'est l'erreur que l'issue d'origine commettait.

## Décision

**Le portail voit les deux zones, et le code mort compte comme le reste.**

`UnusedPrivateMethod` entre dans le jeu, et `includeTests` passe à `true`.

**Les méthodes du `FXMLLoader` sont écartées, pas les fichiers qui les portent.** `@FXML` et
`initialize()` sont appelées par réflexion, donc invisibles pour PMD : elles font 135 des 158
signalements. La suppression vise ces méthodes par leur forme, ce qui laisse les 42 contrôleurs
couverts pour tout leur autre code. Les exclure en entier aurait rendu la règle inoffensive là où
elle est la plus utile.

**Les littéraux dupliqués ne comptent pas dans la zone de test**, et le filtre vit dans le cliquet,
pas dans le ruleset. Répéter un littéral est ce qu'un test doit faire, et `AvoidDuplicateLiterals`
rend 1 366 des 1 428 signalements du dépôt, tous en zone de test, **zéro** en production.

PMD ne sait pas exprimer ce filtre, et quatre formes ont été essayées avant de le déplacer :
`<exclude-pattern>` dans une règle est refusé ; une suppression par nom de fichier en XPath coupe
bien au-delà de sa cible, production comprise ; une seconde exécution du plugin n'est jamais
appliquée, le goal `check` déclenchant `pmd:pmd` en fork avec la configuration **globale** ; et
`failOnViolation` est binaire là qu'il faut un cliquet.

**Le cliquet est à 55** : 32 `NcssCount`, 15 `UnusedPrivateMethod` dont 9 en production,
5 `GodClass`, 2 `ExcessiveParameterList`, 1 `CyclomaticComplexity`.

**Une méthode retirée n'est pas une violation retirée.** Trois surcharges sont parties pour deux
violations : `MultisiteVueIntegrationTest` portait un escalier `ligne(...)` à 6, 7 puis 9 arguments,
et PMD ne signalait que la première, la deuxième lui paraissant vivante puisqu'appelée par la morte.
Les retirer d'un coup n'a coûté qu'une violation. Le cliquet ne descend donc pas du nombre de
méthodes supprimées, et seule la re-mesure tranche.

Posé à 62, il est monté à 63 puis descendu à 57 et 55 au fil du chantier #4656.

Il a été posé à 62 et relevé d'un cran le lendemain, ce qui mérite d'être dit plutôt que lissé.
`SynchronisationParticipationTest` a franchi le seuil `NcssCount` en gagnant les cas qui ferment un
défaut d'écriture concurrente (#4632), fusionné entre la mesure et la mise en place. Allonger une
classe de test pour couvrir un cas de plus est le geste juste : refuser cette montée pousserait à
ne pas couvrir, ce qu'aucun seuil de longueur ne vaut.

**Ce que cet incident apprend sur le cliquet lui-même.** Sa valeur porte sur l'arbre ENTIER, donc
elle vieillit dès qu'une autre demande est fusionnée. Une branche mesurée puis rebasée doit être
**re-mesurée avant d'être poussée** : le premier passage a laissé `main` rouge parce que le rebase
avait nettoyé `target/`, que le garde a refusé de conclure faute de rapport, et que ce refus a été
lu comme une vérification. Un garde qui refuse dit qu'il n'a rien mesuré, pas que tout va bien.

## Conséquences

**Ce qu'on gagne.** La moitié du dépôt qui n'était jugée par rien l'est désormais, et le code mort
a un garde, dans les deux zones.

**Ce qu'on perd.** Le portail passe de 29 à 53 secondes, parce qu'il faut compiler les tests. C'est
mesuré, et faible au regard du reste de la chaîne.

**Ce qui n'est pas fait.** Les 23 méthodes mortes ne sont pas corrigées : leur tri demande une
lecture par site, PMD y visant des **surcharges** précises qu'aucun comptage par nom ne distingue.
Elles feront leur propre issue. Mêler un dispositif et un nettoyage dans la même demande aurait
rendu les deux illisibles.

**Ce que le garde refuse de faire.** Conclure sans avoir lu. Si `target/pmd.xml` manque, il sort en
erreur au lieu de rendre zéro : un garde qui tombe en marche passante est vert au moment précis où
il sert, et c'est le défaut de #4544 sous une autre forme.
