---
type: adr
title: "Le contrôle du carré a deux sources, et l'écran garde la plateforme"
status: stable
article: A17
chantier: "#4671 (passe 2 de la clôture des suites du carré par position)"
decided_at: 2026-08-28
verification: certaine
enforced_by:
  - "ControleCarreLocalTest#concorde_hors_ligne"
  - "ChaineDuCarreStocTest#departement_a_un_chiffre_ne_fait_pas_crier_le_controle"
  - "ControleCarreStocTest#hors_connexion_se_tait"
  - "CliControleDuCarreTest#position_hors_du_carre_declare_avertit"
verified:
  - by: machine:ci
    at: 2026-08-28
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-28
---

# Le contrôle du carré a deux sources, et l'écran garde la plateforme

## Contexte

L'[ADR 4577](4577-le-carre-d-une-position-se-calcule-hors-ligne.md) range « quel carré couvre cette
position » du côté de la **géométrie**, et la géométrie se calcule hors ligne : le carroyage embarqué
reproduit la plateforme au centimètre.

Or `ControleCarreStoc` pose exactement cette question, et il la pose au réseau. Lu vite, cela ressemble
à une incohérence qu'on aurait laissée traîner - le geste de proposition a été rendu hors ligne, celui
de contrôle non.

La clôture de #4671 a par ailleurs comblé un écart : `ajouter-point --lat --lon` et `modifier-point`
posaient des coordonnées sans rien contrôler, là où l'écran contrôlait depuis #733.

## Décision

**Le contrôle existe en deux exemplaires, sur deux sources, et c'est délibéré.**

| Porte d'entrée | Source | Ce qu'il en coûte |
|---|---|---|
| L'écran | la plateforme (`ControleCarreStoc`) | un appel réseau par point géolocalisé |
| La ligne de commande | le carroyage embarqué (`ControleCarreLocal`) | rien |

La **règle** du verdict, elle, ne s'écrit qu'une fois : `ConfrontationCarre`.

## Pourquoi l'écran ne bascule pas hors ligne

C'est le remède évident, et il détruirait quelque chose.

Le contrôle en ligne distingue **quatre** issues, dont `Indisponible` : hors connexion, plateforme
injoignable, refus. Ce cas-là **se tait**, et son silence est une décision de #733 - « le contrôle est
un confort, jamais une condition ; il ne doit ni bloquer la saisie, ni la commenter à tort ».

Un contrôle hors ligne n'a pas d'issue indisponible : il répond **toujours**. Basculer l'écran
supprimerait donc le cas, ses tests, et la nuance qu'il porte - au profit d'une cohérence apparente.

Et la plateforme reste la **seule** autorité sur ce que la grille officielle contient réellement. Le
référentiel embarqué la reproduit, mais il date : #4612 a montré qu'il portait quatre mailles que la
plateforme ne connaît pas.

## Pourquoi la ligne de commande ne demande pas au réseau

`ajouter-point` et `modifier-point` sont des commandes **locales** : ni jeton, ni réseau, ni échec
possible pour cause de connexion. Y faire entrer un appel les changerait de nature, et un script qui
tourne sur le terrain cesserait de tourner.

La géométrie répondant hors ligne, il n'y avait rien à sacrifier.

## Comment on saurait qu'elle est rompue

`ControleCarreLocalTest#concorde_hors_ligne` tient que la CLI juge sans réseau.
`ChaineDuCarreStocTest#departement_a_un_chiffre_ne_fait_pas_crier_le_controle` tient que l'écran passe
toujours par le transport - il monte un vrai transport, et rougirait si l'écran basculait sur le
carroyage.

`ControleCarreStocTest#hors_connexion_se_tait` tient le cas que la bascule ferait disparaître : c'est
lui le vrai garde de cette décision.

`CliControleDuCarreTest#position_hors_du_carre_declare_avertit` tient que la capacité existe des deux
côtés, ce qui est l'autre moitié de la décision.
