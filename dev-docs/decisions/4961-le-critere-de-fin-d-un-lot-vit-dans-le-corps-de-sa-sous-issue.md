---
type: adr
title: "Le critère de fin d'un lot vit dans le corps de sa sous-issue"
status: stable
article: A11
chantier: "#4961, clôture passe 11"
decided_at: 2026-08-31
verification: humaine
verification_note: "aucun garde ne peut dire qu'un critère est vérifiable, et deux dessins mécaniques sur de la prose d'EPIC ont déjà été mesurés puis écartés. Ce qui se vérifie est sa PRÉSENCE, et l'ADR 4992 porte les deux dispositifs qui la tiennent"
verified:
  - by: human:nedseb
    at: 2026-08-31
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-31
---

# Le critère de fin d'un lot vit dans le corps de sa sous-issue

## Contexte

`ouvrir-un-chantier` exige depuis le 2026-08-29 que chaque lot dise comment on saura qu'il est fini.
La règle vivait dans un paragraphe de fin, sous un titre qui ne l'annonçait pas, et elle ne disait
pas **où** ce critère s'écrit.

Trois endroits se présentaient, et le dépôt en pratiquait déjà trois à la fois : une ligne du corps
de l'EPIC, le corps de l'issue du lot, ou la ligne « Ce que je vérifierai » du bloc d'ouverture que
`CLAUDE.md` prescrit.

## Décision

**Le critère vit dans le corps de la sous-issue qui porte le lot.**

Le corps de l'EPIC dit ce que chaque lot **livre** : c'est le plan. Le critère dit comment on saura
que c'est fait : c'est l'engagement du lot, et il appartient au lot.

## Pourquoi le corps, et pas une ligne de l'EPIC

**Un lot n'est plus une ligne.** Depuis l'issue #4829, livrée par #4854, le rattachement d'un lot à
son chantier est une **sous-issue native** que la forge relie, et non une case cochée dans le corps
du parent. #4852 et #4853 l'écrivent en toutes lettres. Une règle qui range le critère dans le corps
de l'EPIC décrit un support que le dépôt a quitté.

**Et l'énumération devient une donnée.** `gh issue view <EPIC> --json subIssues` rend les lots sans
qu'aucun dispositif ait à deviner une forme de prose. La loupe de l'ADR 4992 en dépend, et c'est ce
qui la rend possible : le comptage d'origine s'était trompé trois fois en cherchant un libellé.

## Pourquoi le corps, et pas le bloc d'ouverture

`CLAUDE.md` demande « Ce que je vérifierai » dans le bloc de prise, et ce bloc se dépose **en
commentaire**. Un commentaire descend sous le fil à mesure que l'issue vit ; le corps est ce qui
survit à l'onglet fermé, et c'est lui que la clôture relit. `clore-une-issue` le pose déjà : le corps
porte la vérité, les commentaires portent le journal.

La ligne du bloc reste, et elle se **recopie** au corps. Elle a d'ailleurs une valeur propre : elle
oblige à énoncer le critère avant de commencer, quand le corps peut être complété après.

## Ce que cette décision a coûté d'être implicite

Le comptage qui a ouvert le chantier annonçait 3 EPIC sur 70 portant un critère, et ce chiffre a
servi pendant un jour à justifier qu'on ne relise pas les critères à la clôture. Il mêlait 118
chantiers antérieurs à la règle avec les 22 qui pouvaient lui obéir, et ne cherchait qu'une des
formulations en usage. Refait au grain du lot : **9 lots ouverts sur 11**.

**Un comptage qui sert à renoncer se vérifie avant de renoncer.**

## Conséquences

**Quatre surfaces l'exigent** : `ouvrir-un-chantier` à l'étape 3, `ouvrir-une-issue` par une étape 4b
de sa fonction de garde, `AGENTS.md` à ses deux endroits, et `dev-docs/cycle-de-chantier.md`. La
quatrième a manqué au premier lot, et c'est celle qu'un agent lit en premier.

**La passe 0 d'une clôture relit ces critères un par un**, ce que `ecrire-une-adr` demande depuis
#4936. Cette clôture-ci est la première à le faire, et elle a rendu un « non ».

**Ce qui se vérifie est la présence, jamais la qualité.** « Fini quand c'est fait » satisfait les
deux dispositifs. Juger qu'un critère est vraiment vérifiable reste un jugement humain, et deux
dessins de garde mécanique sur de la prose d'EPIC ont déjà été mesurés puis écartés dans ce dépôt.
