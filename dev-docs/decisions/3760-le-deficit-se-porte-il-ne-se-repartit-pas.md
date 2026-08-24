---
type: adr
title: "Le déficit de place se porte, il ne se répartit pas"
status: stable
article: A15
chantier: "#3760 et #3743, suites de la clôture du lot 3 (#3540) du chantier #3536"
decided_at: 2026-08-15
verification: certaine
enforced_by:
  - "BudgetHorizontalChromeTest#le_chrome_tient_son_budget"
verified:
  - by: machine:ci
    at: 2026-08-15
relations:
  prolonge: ["0042", "0039"]
---

# Le déficit de place se porte, il ne se répartit pas

## Contexte

Les deux barres du chrome demandaient plus de place qu'elles n'en avaient à la largeur d'ouverture de
l'application, et le symptôme n'était pas celui qu'on attend.

**Barre du haut** : somme des largeurs préférées de **1166,6 px** pour **1100** disponibles. Une `HBox`
ne choisit pas de victime : elle **répartit** le manque sur tout ce qui peut rétrécir. Chacun perdait
vingt-sept pixels, et tout le monde tronquait un peu - le titre de l'application, le bouton ← Retour,
les cinq segments du fil d'Ariane et l'invite du champ de recherche, ensemble.

**Barre du bas** : un `BorderPane` fait l'inverse. Gauche et droite prennent leur largeur préférée, le
centre se contente du reste : la phrase « Espace disque insuffisant… » occupait **791 px** et laissait
**10 px** à la zone centre sous 900.

Deux mécaniques opposées, un même trou : **personne n'avait décidé qui rend sa place**.

## Décision

**Une barre déclare qui porte son déficit, dans l'ordre, et le déclare là où le contrôle vit.**

Trois natures de réponse, qu'il faut distinguer parce qu'elles ne valent pas la même chose :

1. **Figer** (`minWidth="-Infinity"`) ce qui ne doit jamais s'abréger. Ici le titre et le bouton
   ← Retour : ils ne portent aucune information redondante, et un utilisateur qui ne lit plus « Retour »
   ne sait plus où il clique.
2. **Réduire la demande**, quand c'est possible sans perte. Le champ de recherche passe de 240 à 132 px,
   et son invite de « Rechercher (Ctrl+F) » à « Rechercher » : le raccourci reste actif, décrit dans la
   fiche d'écran et porté par le texte accessible. **C'est le seul des trois qui supprime le problème**
   au lieu de le déplacer.
3. **Assumer l'abrègement** par la classe `abregeable` de l'[ADR 0042](0042-un-apercu-qui-ment-est-refuse.md).
   Ici le fil d'Ariane, qui ne rend rien à 1100 et porte 188 px à 900.

### La troisième est un aveu, pas un remède

`abregeable` **fait taire le garde de lisibilité**. C'est sa fonction, et c'est ce qui la rend
dangereuse : elle transforme un rouge en vert sans rien améliorer à l'écran. Une clôture qui l'emploie
doit dire **où** elle l'a posée et **à partir de quelle largeur** elle travaille - faute de quoi le vert
qui suit ne veut plus rien dire.

## Ce que le garde tient, et ce qu'il ne tient pas

`BudgetHorizontalChromeTest` monte le vrai chrome sur l'écran le plus profond, aux **deux** largeurs que
`TailleOuverture` livre, et demande son verdict à `LisibiliteCapture`.

Il tient la **mise en page**. Il ne tient **pas la copie** : la chaîne du pire cas est écrite dans le
test, donc rallonger un message du produit ne le fera pas rougir. C'est le test d'intégration de M-Lot
qui garde ce que l'écran met vraiment dans la zone - et il a attrapé le changement de copie de cette
ADR au premier lancement, ce qui est la meilleure preuve que la répartition est juste.

Les deux vont ensemble, et chacun le dit dans son doc-comment.

## Ce que nous avons écarté

**Élargir la fenêtre.** `TailleOuverture` ouvre à 1100 et interdit 900 : déplacer ces bornes pour faire
tenir une barre, c'est demander à l'utilisateur de payer un défaut de mise en page.

**Rendre le fil d'Ariane intelligent** (élider les segments du milieu par « … » plutôt que d'abréger
chaque libellé). C'est le bon remède à terme, et il reste à faire. Il se décidera mieux une fois le
budget tenu, sur une barre dont on sait qui porte quoi.
