---
type: adr
title: "Un avertissement de qualité ne se tait pas, et un garde le refuse"
status: stable
article: A10
chantier: "#6022 (sous-chantier de #6012), lot #6114"
decided_at: 2026-10-07
verification: certaine
enforced_by:
  - "scripts/adr/6022-annotation-qui-fait-taire.py"
ratchet: 0
verified:
  - by: machine:ci
    at: 2026-10-07
relations:
  complete: ["4617-le-portail-voit-les-tests-et-le-code-mort"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-07
---

# Un avertissement de qualité ne se tait pas, et un garde le refuse

## Contexte

L'article A10 interdit de faire taire un avertissement : ni `@SuppressWarnings`, ni `//NOPMD`, on
refactore. Quatre surfaces le répétaient, `CONSTITUTION.md`, `AGENTS.md`,
`.github/copilot-instructions.md` et la description de `pmd-ruleset.xml`. Aucun dispositif ne le
vérifiait, et la matrice de la Constitution rangeait A10 parmi les deux articles tenus par la seule
relecture, sans une décision à son nom.

Le défaut a eu lieu. Deux méthodes mortes ont vécu sous `@SuppressWarnings("unused")` dans les
scénarios d'emport, du 30 août au 6 octobre 2026. Le cliquet de l'[ADR 4617](4617-le-portail-voit-les-tests-et-le-code-mort.md)
est resté à 40 après leur retrait (#6028) : PMD ne les comptait pas. L'annotation cachait le code
mort au portail, pas seulement au lecteur.

## Décision

**Un garde refuse, sans marge, l'annotation et la marque qui font taire le portail.**
`scripts/adr/6022-annotation-qui-fait-taire.py` lit le Java de `src/main/java` et de `src/test/java`
par l'arbre syntaxique, et son cliquet est à zéro : il se lit comme un refus, au sens de l'ADR 4682.

**La liste des valeurs admises est fermée : `unchecked` et `rawtypes`.** Elles s'adressent à javac,
sur un transtypage générique qu'il ne peut pas prouver. PMD ne les lit pas, et les 55 annotations du
dépôt sont de celles-là. Toute autre valeur est refusée, y compris celles que PMD n'honore pas
aujourd'hui. Une valeur qui n'est pas un littéral, constante ou concaténation, est refusée sans être
résolue.

**La marque `NOPMD` se refuse là où PMD la lit, et pas ailleurs** : dans un commentaire de ligne qui
suit du code. Sur une ligne qui ne porte que du commentaire, aucune violation ne peut commencer, et
la mention ne fait rien taire.

## Ce que PMD honore, mesuré

Le garde doit refuser ce que l'outil honore, et la documentation de l'outil n'est pas une mesure. Le
7 octobre 2026, sur `80ae6d71db`, une classe jetable de dix-huit méthodes privées mortes a été jouée
contre `UnusedPrivateMethod` sous PMD 7.17.0.

| Forme | PMD se tait |
|---|---|
| `"unused"`, `"all"`, `"PMD"`, `"PMD.UnusedPrivateMethod"` | oui |
| un tableau qui contient l'une d'elles | oui |
| `"unchecked"`, `"rawtypes"`, `"fallthrough"`, le nom d'une autre règle | non |
| `// NOPMD` en fin de ligne, collé ou non, seul ou dans une phrase | oui |
| un `///` de fin de ligne qui cite `NOPMD` | oui |
| `/* NOPMD */`, `// nopmd`, un `///` posé sur la ligne d'au-dessus | non |

## Pourquoi une liste fermée, et pourquoi l'arbre

Une liste de refus, `unused` et `PMD.*`, aurait laissé passer `all`, que PMD honore, et la prochaine
valeur qu'il apprendra à honorer. La liste fermée inverse la charge : ajouter une valeur admise est
une décision, qui se prend ici.

Un motif textuel ne peut pas tenir ce zéro. Deux fichiers citent ces mots en prose,
`HorairesDistants.java` et `CourbesActivite.java`, dans la javadoc qui explique pourquoi on a
extrait plutôt que fait taire. Sur `NOPMD`, un `grep` ne rend que la seconde : une occurrence, et
elle est innocente. Le garde lit donc `scripts/_commun/arbre.py`, comme l'ADR 5437 l'a fait pour la
même raison.

## Conséquences

- **A10 a sa première décision et son premier garde.** Il ne reste qu'un article tenu par la seule
  relecture, A8.
- **Une annotation réintroduite fait rougir `methode` à chaque demande**, et la porte locale en
  quelques secondes, sans JVM.
- **`fallthrough`, `deprecation` ou `serial` sont refusées** bien qu'elles ne fassent pas taire PMD.
  Le dépôt n'en porte aucune. La première qui se présentera se discutera, et c'est voulu.
- **Les trois règles `Unused*` que le chantier #6022 fait entrer au portail** arrivent derrière une
  porte fermée : une règle neuve qui mord est le moment où l'annotation tente.

## Ce que le garde ne voit pas

- Les exclusions écrites dans `pmd-ruleset.xml`, `violationSuppressXPath` et `violationSuppressRegex`.
  Elles se voient au diff du jeu de règles, et celle de `UnusedPrivateMethod` est motivée par l'ADR
  4617.
- Une zone que la grammaire n'a pas su lire n'est pas blanchie : le garde y cherche les deux mots
  comme du texte, et les refuse.
- Si PMD venait à signaler des commentaires, la tolérance d'une marque posée sur une ligne de
  commentaire seule serait à revoir. Aucune règle du jeu ne le fait.

## Alternatives écartées

- **Un balayeur Java dans le paquet `architecture`.** La règle se lit dans la source, sans résoudre
  de types : un script la juge avant toute compilation.
- **Une règle PMD qui interdirait l'annotation.** L'outil qu'on fait taire serait chargé de dire
  qu'on le fait taire, et `@SuppressWarnings("all")` la ferait taire aussi.
- **Ne lire que `"unused"`**, la seule forme rencontrée. Les trois autres font taire le portail de
  la même façon, et la mesure le montre.
