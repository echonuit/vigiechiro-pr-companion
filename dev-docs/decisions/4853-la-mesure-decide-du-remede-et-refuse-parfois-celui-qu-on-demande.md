---
type: adr
title: "La mesure décide du remède, et refuse parfois celui qu'on demande"
status: stable
article: A3
chantier: "#4853 (clôture, passe 11)"
decided_at: 2026-08-30
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/passage/model/SessionDEnregistrementTest.java"
  - "src/test/java/fr/univ_amu/iut/commun/di/DiagnosticGuiceTest.java"
verified:
  - by: machine:ci
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# La mesure décide du remède, et refuse parfois celui qu'on demande

## Contexte

Le chantier #4853 portait deux suites d'une même cause : **un changement qui se propage à la main**.
Une règle écrite en deux exemplaires, et un constructeur dont chaque paramètre neuf coûtait quatre
alignements.

Les deux issues nommaient leur remède. **Les deux mesures l'ont refusé.**

## Ce que la mesure a dit, deux fois

**#4666 demandait d'extraire une règle dupliquée.** En éprouvant le cas neuf par mutation, le mutant a
**survécu** : retirer la branche `isAbsolute()` laisse les vingt-huit cas des deux appelants verts.
`Path.resolve` rend trivialement son argument quand il est absolu, c'est son contrat. Les deux
exemplaires dupliquaient donc **du code que le JDK fournit déjà**.

Sans cette vérification, du code mort aurait été déplacé dans un foyer commun, et cela se serait
appelé une harmonisation.

**#4767 demandait de désamorcer un constructeur coûteux.** Six contrôleurs sont construits à la main,
en treize sites répartis dans neuf fichiers, et l'alignement représente une trentaine d'éditions sur
toute l'histoire du dépôt, **toutes désignées par le compilateur**. Le contrôleur le plus large n'est
même pas le plus coûteux.

Le corps de l'issue disait pourtant où était le vrai coût : « pas les quatre éditions, mais les
minutes passées sur une fausse piste que l'outil désigne lui-même ». La sonde l'a confirmé, et c'est
le diagnostic qui a été corrigé.

## Décision

**La mesure précède le remède, et peut le refuser.** Une issue nomme un remède plausible ; ce qui
décide est ce qu'on mesure, pas ce qu'on a écrit en ouvrant.

**Une règle a un foyer, et c'est le type qui porte la donnée.** `resoudre` vit sur
`SessionDEnregistrement` parce que c'est là que vit `cheminRacine`, non dans un utilitaire de `commun`
qui aurait éloigné la règle de ce qu'elle règle.

**Un remède qui n'est pas adopté n'est pas un remède.** `DiagnosticGuice` a été posé sur **86 sites**
et non sur les deux où le défaut s'était vu. Un outil que deux fichiers utilisent est une étagère.

**Une décision de ne pas faire s'écrit.** Le constructeur de `QualificationController` n'a pas été
touché, et la fabrique de test qui l'entoure dit dans sa javadoc qu'elle ne répond à aucune douleur
mesurée : elle sert à ce que les cas disent ce qu'ils éprouvent.

## Le cas qui ne peut plus rougir, gardé et annoté

`un_chemin_absolu_ne_bouge_pas` ne rougit sur aucune mutation, puisqu'il n'y a plus de branche à muter.
Il est gardé, et sa javadoc dit exactement ce qu'il tient : non pas une branche du code, mais le
**contrat de `Path.resolve`** sur lequel les deux appelants s'appuient sans le dire.

Le dépôt retire un garde vacant. Celui-ci n'en est pas un : il rougirait sur une résolution écrite par
concaténation de chaînes, qui est la régression plausible. Le dire vaut mieux que le retirer, et mieux
que le laisser passer pour ce qu'il n'est pas.

## Conséquences

Deux remèdes demandés n'ont pas été écrits, et les deux refus sont chiffrés. Un lecteur futur qui
voudrait « finir le travail », en remettant la branche jugée manquante ou en ajoutant l'objet de
dépendances écarté, trouvera ici la mesure qui l'en dissuade.

Le troisième appelant que #4666 annonçait existe, et il ne résout pas du tout : consigné en #4891
plutôt que corrigé au détour d'une clôture.

## Alternatives écartées

**Extraire la règle telle quelle**, branche comprise. C'était la demande, et cela aurait figé du code
mort dans un foyer partagé, où il aurait l'air délibéré.

**Un objet de dépendances ou une fabrique de production** pour le constructeur. Défendables, et non
justifiés : le compilateur désigne chaque alignement, aucun n'est silencieux.

**Adopter le diagnostic sur les deux fichiers où le défaut s'était vu.** Plus petit, plus prudent, et
sans effet : la prochaine liaison manquante serait tombée dans l'un des 84 autres.
