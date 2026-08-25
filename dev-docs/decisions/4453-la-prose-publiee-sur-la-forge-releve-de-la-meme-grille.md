---
type: adr
title: "La prose publiée sur la forge relève de la même grille"
status: stable
article: A31
chantier: "#4453 (report des évolutions de méthode, chantier #4334)"
decided_at: 2026-08-25
verification: humaine
loupe:
  - ".github/scripts/verifie-corps-pr.sh"
relations:
  amende: ["4343-la-prose-visible-se-relit-a-l-humaniseur"]
verified:
  - by: human:nedseb
    at: 2026-08-25
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-25
---

# La prose publiée sur la forge relève de la même grille

## Contexte

L'article A31 énumère les surfaces où la prose se relit à l'humaniseur : javadoc, documentation, ADR,
libellés d'interface et de ligne de commande, messages de commit. Le corps d'une issue et celui d'une
pull request n'y figuraient pas.

Ce ne sont pourtant pas des surfaces mineures. La compétence `clore-une-issue` en fait les **deux
textes qui se relisent dans six mois sans le fil** : le corps de l'issue porte l'état courant de la
vérité, celui de la PR est ce qu'atteint quiconque remonte depuis `git log`. La prose la plus durable
du dépôt était la seule que sa règle de prose ne couvrait pas.

## Le défaut

Le déclencheur d'A31 était « avant d'être **commise** ». Un corps de PR n'est jamais commis : la
règle ne pouvait donc l'atteindre à aucun moment, et aucun garde de fichiers ne le balaie, puisqu'il
ne vit pas dans le dépôt.

La mesure, refaite ici sur les **cent** derniers corps de PR de ce dépôt : **dix-sept sont refusés**,
pour cinquante lignes fautives - trente-neuf cadratins de prose et onze élisions sans apostrophe.
**Dix de ces dix-sept ont été ouvertes le jour même de cette décision**, par l'agent qui l'écrit :
la règle existait pour les fichiers, elle attrapait ses cadratins dans le code, et elle le laissait
en semer dans les corps de PR toute une journée. Aucune de ces lignes ne peut plus être retirée de
la forge. Le même glyphe est
refusé dans tous les fichiers du dépôt, et refusé dans le **titre** de la PR par un garde écrit pour
cela. Entre le titre et les fichiers, le corps passait.

Le défaut est aussi celui que l'ADR
[« La prose visible se relit à l'humaniseur »](4343-la-prose-visible-se-relit-a-l-humaniseur.md) décrit
pour son propre compte : un registre qui n'existe qu'en intention se perd. Celui-ci s'est reperdu le
2026-08-25, en séance, et a dû être redemandé.

## Décision

**L'énumération d'A31 gagne le corps d'issue et le corps de pull request, et son déclencheur devient
la publication plutôt que le commit.** Une prose part quand elle part, que ce soit par un commit ou
sur la forge.

**La part mécanisable du corps de PR est refusée par un garde de CI.** Trois défauts, chacun adossé à
une décision déjà prise ailleurs : le tiret cadratin, l'apostrophe courbe, et l'élision sans
apostrophe que le garde du titre refuse déjà. Le contrôle reste **informatif**, comme celui du titre
et pour les raisons que porte
[« Le sujet d'un commit est une syntaxe »](0040-le-sujet-de-commit-est-une-syntaxe.md) : un check requis
gouverne la branche, pas les PR.

**Le corps d'issue reste tenu par la relecture**, et par les fonctions de garde d'`ouvrir-une-issue`
et de `clore-une-issue`. Une issue n'a aucun contrôle qui puisse rougir.

## Conséquences

**Ce que le garde voit.** Onze cas d'auto-test, dont trois qui doivent rougir et quatre contrôles
négatifs. Les trois règles ont été vues rouges chacune sur sa propre mutation, et l'exemption des
blocs de code aussi : sans elle, une sortie de commande collée ferait rougir un corps juste, et le
garde se contournerait en retirant la citation. Sur le corpus réel, dix-sept corps sur dix-huit
passent, et le dix-huitième rend les deux défauts connus.

**Ce qu'il ne voit pas**, et c'est assumé : les quatre tics rhétoriques de `CONTRIBUTING.md`, qu'aucun
motif ne distingue d'une phrase légitime ; le corps vide, que personne n'a décidé d'interdire ; le
corps d'issue.

**Le niveau est `humaine`.** Rien ne vérifie qu'un corps a passé la grille entière, et le garde n'en
tient que la part typographique : c'est une loupe, pas une preuve.

## Alternatives écartées

- **Rendre le contrôle bloquant.** Il contredirait une décision déjà prise, sur une mesure : deux
  automatismes cassés en une heure le jour où un check est devenu requis.
- **Un atelier déclenché sur les issues.** Une issue ne porte aucun contrôle : le run finirait rouge
  dans un onglet que personne n'ouvre, ce qui donne l'apparence d'une garde sans en être une.
- **Refuser aussi le pictogramme.** Aucune décision du dépôt ne l'interdit dans une phrase ; l'ADR des
  pictogrammes l'autorise explicitement. Un garde qui invente sa règle se fait retirer.
