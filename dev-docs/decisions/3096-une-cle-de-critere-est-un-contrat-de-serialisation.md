---
type: adr
title: "Une clé de critère est un **contrat de sérialisation** : un concept, un endroit, et un renommage sans migration"
status: stable
article: A16
chantier: "#3096, palier 3 du chantier #3092"
decided_at: 2026-08-04
verification: certaine
enforced_by:
  - "ClesCriteresTest#aucune_cle_publiee_deux_fois"
verified:
  - by: machine:ci
    at: 2026-08-04
---

# Une clé de critère est un **contrat de sérialisation** : un concept, un endroit, et un renommage sans migration

## Contexte

Chaque critère de filtre porte un **nom court** (`statut`, `groupe`, `lieu`) qui sert à deux choses très
différentes : identifier le critère dans le code, et le **sérialiser** dans la table `vue_sauvegardee`
quand l'utilisateur enregistre une vue.

Quatre catalogues déclaraient ces noms **chacun de son côté**, en constantes privées ou en littéraux.
Deux conséquences étaient déjà là :

- `statut` désignait `StatutObservation` en audio et en analyse, mais `StatutWorkflow` en multisite :
  **une clé, deux concepts**. La collision ne mordait pas encore - les vues sont persistées par écran,
  et multisite n'est cible d'aucun transport - mais le mécanisme de transport (#476) l'arme ;
- rien n'empêchait un cinquième écran de nommer `lieux` ce que quatre autres nomment `lieu`.

## Décision

**Les clés partagées vivent à un seul endroit**, `ClesCriteres`, avec le concept que chacune désigne.
La clé multisite devient `statut_workflow`. Un catalogue ne réécrit **jamais** une clé partagée en
littéral, et `ClesCriteresTest` le refuse.

**Une clé propre à un seul écran reste chez lui.** `ClesCriteres` ne porte que le **commun** : `gravite`,
`categorie` et `passage` (Audit) vivent dans `CriteresAudit`.

**Les libellés sont une préoccupation distincte**, dans `LibellesCriteres`, qui doit couvrir **toutes**
les clés - y compris celles qu'aucun écran local n'offre.

**Un renommage ne migre pas la base** : `CritereFiltre.nomsHerites()` (défaut vide) déclare les noms
qu'un critère a portés, et `critereParNom` les accepte.

## Pourquoi

**Une clé est un contrat, pas un identifiant de code.** La renommer invalide les vues déjà enregistrées
chez les utilisateurs. C'est ce qui la distingue d'un nom de variable, et c'est la raison pour laquelle
elle mérite un endroit déclaré plutôt qu'une constante privée par écran.

**Clés et libellés ne bougent pas au même rythme.** Un libellé se reformule quand l'ergonomie l'exige,
une clé ne bouge qu'au prix des vues existantes. Les loger ensemble ferait porter le coût du second au
premier - on hésiterait à corriger un libellé maladroit.

**Une clé propre à un écran n'a rien à faire dans le commun.** `categorie` (nature d'un constat d'audit)
n'a aucun rapport avec `groupe` (taxon parent), et les mettre au même endroit inviterait précisément la
collision que ce contrat existe pour empêcher.

**`nomsHerites` est un pont, pas un fourre-tout.** N'y mettre que des noms **réellement portés** : un
nom ajouté « au cas où » rendrait le compte rendu de restauration (ADR 3093) muet sur une vraie clé
inconnue, en la faisant passer pour un alias connu.

## Conséquences

- Ajouter un critère à un deuxième écran, c'est **promouvoir sa clé** dans `ClesCriteres` et lui donner
  un libellé. Le garde le rappellera au premier littéral dupliqué.
- Le garde d'inventaire (#3105) ancre le **nombre** de critères de chaque écran dans sa fiche : un
  catalogue qui grossit sans que la doc suive fait rougir la CI.
- Renommer une clé reste possible **sans migration**, mais chaque `nomsHerites` est une dette de
  lecture : elle dit qu'un nom a existé, et il faudra un jour décider de la retirer.

## Alternatives écartées

**Une énumération de clés.** Écarté : une clé propre à un écran n'aurait pas sa place dans une
énumération commune, et l'y mettre relancerait la confusion que la séparation vient de lever.

**Migrer `vue_sauvegardee` au renommage.** Écarté : une migration pour un renommage d'affichage est
disproportionnée, et `nomsHerites` rend le même service sans toucher aux données de l'utilisateur.
