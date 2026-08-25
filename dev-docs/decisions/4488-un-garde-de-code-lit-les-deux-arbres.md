---
type: adr
title: "Un garde de code lit les deux arbres, et ce qu'il n'en lit pas se décide"
status: stable
article: A3
chantier: "#4488 (passe 7 de la clôture de #4462)"
decided_at: 2026-08-26
verification: certaine
enforced_by:
  - "scripts/adr/verifie_scripts.py"
verified:
  - by: machine:suspects
    at: 2026-08-26
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-26
---

# Un garde de code lit les deux arbres, et ce qu'il n'en lit pas se décide

## Contexte

Sept gardes de `scripts/adr/` ne lisaient que `src/main/java`. En relisant les ADR qui les fondent, **aucune ne restreignait leur corpus** : elles sont nées avant que la question ne se pose, à l'époque où la dette se mesurait sur la production.

Ce silence coûte plus qu'il n'y paraît. Un cliquet ne se plaint jamais qu'on lui retire du corpus : il compte ce qu'on lui donne, et il reste vert. Un défaut que la règle nomme peut donc s'accumuler dans l'arbre de test sans que rien ne le montre, et l'angle mort grandit d'autant plus vite qu'il est invisible.

L'article A3 tranche déjà la question sous une autre forme : un dispositif dit ce qu'il couvre, et ce qu'il n'a pas pu lire. Un garde borné à un arbre sans le dire a une **couverture non déclarée**.

## Décision

Le corpus par défaut d'un garde de code est **`src/main/java` et `src/test/java`**. Un garde qui n'en lit qu'un le dit dans son en-tête, avec la raison.

Sept gardes rejoignent le corpus complet : `0010-dialogue-hors-port`, `0035-pictogramme-caractere`, `0037-slot-actions-hbox`, `2493-modale-suit-croissance`, `3053-capture-libelle`, `3947-message-enveloppe` et `4476-javadoc-raconte-son-extraction`. La mesure d'ouverture rendait **zéro** suspect côté test pour chacune : aucun cliquet ne bouge, et la question se ferme.

**`2635-refus-sans-surface` reste bornée à la production**, et c'est une décision. Ses trois suspects sont dans `MoteurTraitementGroupeTest`, le test qui prouve l'ADR 2635 : il doit citer le glyphe du menu pour vérifier que la rédaction de la surface atteint le compte rendu **et** le journal. L'y étendre interdirait aux tests d'affirmer les chaînes mêmes que la règle produit. C'est l'article A2 d'un cran plus loin : un détecteur textuel s'exclut de son propre corpus, et le test qui prouve sa règle en fait partie.

## Conséquences

- Un cas de `verifie_scripts.py` tient la liste des gardes à deux arbres. Il tient le **corpus**, là où le témoin propre à chaque garde tient sa **détection** : les deux se cassent séparément, et un garde qui continuerait de détecter parfaitement sur l'arbre qu'on lui laisse ne rougirait pas.
- Le garde qui reste borné porte sa raison dans son en-tête, et la liste nomme son absence plutôt que de la laisser deviner.
- Un garde neuf hérite du corpus complet sans avoir à le redemander ; c'est le restreindre qui demande une phrase.

## Alternatives écartées

- **Étendre les huit, cliquet de 2635 relevé à 3.** Le cliquet aurait porté trois lignes qu'aucune correction ne retirera jamais, puisqu'elles sont la preuve de la règle. Un cliquet qui ne peut pas descendre est un tapis.
- **Laisser chaque garde décider et le documenter au cas par cas.** C'est l'état d'où l'on vient : sept omissions et aucune décision. Un défaut par défaut se répète, une exception écrite se relit.
- **Un garde qui exclurait les fichiers en `*Test.java` du corpus.** Il rendrait la règle inapplicable aux tests dans leur ensemble pour épargner un cas, alors que c'est le cas qui est particulier.
