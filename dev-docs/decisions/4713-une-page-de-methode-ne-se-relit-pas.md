---
type: adr
title: "Une page de méthode ne se relit pas, donc ce qu'elle prescrit de vérifiable se garde"
status: stable
article: A3
chantier: "#4713"
decided_at: 2026-08-29
verification: certaine
enforced_by:
  - "scripts/methode/etapes-sans-renvoi-aval.py"
  - "scripts/methode/tests-cites-existent.py"
verified:
  - by: machine:ci
    at: 2026-08-29
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-29
---

# Une page de méthode ne se relit pas, donc ce qu'elle prescrit de vérifiable se garde

## Contexte

Le chantier #4713 a corrigé sept endroits où une trace affirmait plus que la mesure. Aucun n'avait
jamais rougi. Une page qui décrit fidèlement un mécanisme remplacé se lit comme vraie, et rien dans
ce dépôt ne la relit : les gardes lisent le code Java, les ADR, les balises, les captures. Pas la
prose de méthode.

Le coût n'est pas théorique. `AGENTS.md` prescrivait sous « non négociable » une commande rendant
1 449 violations sur un arbre propre. `ouvrir-une-pr` annonçait qu'un plancher périmé sort en `0`
alors qu'il sort en `1`, et trois demandes de fusion ont rougi pour cette raison le même jour.
`TESTING.md` donnait un exemple qui rend `BUILD SUCCESS` sur zéro test exécuté.

## Le défaut

**Une page de méthode n'est lue qu'au moment où on la suit.** Elle est donc éprouvée par la personne
qui a le moins de moyens de la contredire : celle qui vient y chercher la règle.

Le pire cas n'est pas la désinformation. C'est l'exemple de `TESTING.md` : il mettait en main une
commande **verte sans avoir jugé**, et le piège se referme au moment précis où l'on doute d'une
méthode et où l'on veut la cibler seule.

## Décision

**Ce qu'une page de méthode prescrit et qui est vérifiable mécaniquement se garde.** Deux gardes
sont posés, et le chantier a mesuré pourquoi il n'y en a que deux.

`etapes-sans-renvoi-aval.py` refuse qu'une **étape numérotée** d'une des six compétences du cycle
nomme une compétence située plus loin. La prose reste libre de nommer la suite : seules les étapes
prescrivent un geste à faire maintenant.

`tests-cites-existent.py` refuse qu'un `-Dtest=Classe#methode` cité par une page, une compétence ou
un atelier nomme une classe ou une méthode absente.

## Ce que le relevé a mesuré, et pourquoi deux et pas sept

Le chantier s'est clos sur un relevé, dont trois populations :

| Population | Lue | Défauts |
|---|---:|---:|
| chemins cités par les pages de méthode | 132 | 0 - les 4 introuvables sont des gabarits |
| commandes présentées comme exécutables | 39 | 1 |
| alternatives écartées d'ADR | 10 sur 125, **tirées** | 0 |

Sur les sept cas, **deux seulement se mécanisent** :

| Cas | Vérifiable ? |
|---|---|
| une commande, un code de sortie, un test cité | **oui** - la machine peut les lancer |
| une prémisse sur l'état du monde | non - la forge la dément, mais aucun motif ne la désigne |
| une preuve empruntée à un dispositif voisin | non - il faut lire les deux et juger |
| un renvoi vers une compétence déplacée | **oui**, une fois l'ordre du cycle nommé |

La règle est donc étroite par construction, et c'est délibéré : elle porte ce qui se lance, non ce
qui se juge.

## Conséquences

**Le corpus se dérive, il ne s'énumère pas.** `tests-cites-existent` écarte les gabarits de prose -
`-Dtest=A,B,C` en est un - par une règle tirée de la convention du dépôt : une citation ne compte que
si elle nomme quelque chose finissant par `Test`, et 791 classes de test sur 791 la suivent. Une
liste d'exceptions aurait dérivé, comme trois listes tenues à la main l'ont fait pendant ce chantier.

**Le motif s'éprouve avant d'être cru.** Sa première version signalait une classe `A` introuvable,
tirée du gabarit. Publier un défaut inexistant dans un chantier dont le sujet est d'affirmer plus que
la mesure aurait été la faute la plus coûteuse possible.

**Ce qui n'est pas mécanisable reste humain, et le relevé le nomme.** Une prémisse fausse et une
preuve empruntée demandent qu'on lise deux textes et qu'on juge. Ce chantier n'a pas prétendu les
garder ; il a écrit lesquelles et pourquoi.

**Zéro sur dix n'est pas zéro sur cent vingt-cinq.** Le relevé borne le taux des alternatives
écartées sans l'annuler, et il l'écrit. Ce qu'il établit suffit à décider - six cas sur sept vivaient
ailleurs - sans prétendre que les ADR sont saines.

## Alternatives écartées

- **Un garde par cas trouvé.** Cinq des sept ne se mécanisent pas ; les garder aurait produit des
  motifs qui périment au premier synonyme, c'est-à-dire des gardes à réparer plutôt qu'à consulter.
- **Un garde générique sur « la page dit vrai ».** Il faudrait modéliser ce que chaque phrase
  affirme. Le relevé montre que les affirmations vérifiables sont peu nombreuses et de nature
  hétérogène - une commande, un code, une visibilité, un chemin.
- **Rien garder, et relire.** C'est l'état d'où l'on vient : sept pages fausses, aucune rougeur, et
  la plus ancienne datant d'avant la mesure qui l'aurait dite.
