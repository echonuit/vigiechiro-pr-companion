---
type: adr
title: "Lire la structure coûte, et ce coût s'accepte contre une justesse par construction"
status: stable
article: A5
chantier: "#5402 (les gardes lisent le Java par motif), lot #5430"
decided_at: 2026-09-07
verification: probable
enforced_by:
  - "scripts/adr/5340-chemins-non-declares.py"
ratchet: 43
verified:
  - by: machine:ci
    at: 2026-09-07
generated:
  by: "process:assistance-par-agents"
---

# Lire la structure coûte, et ce coût s'accepte contre une justesse par construction

## Le contexte

Six gardes lisaient la structure du Java en comptant des accolades ou en équilibrant des
parenthèses. Le dépôt le savait : l'ADR 4472 déclarait son comptage « naïf », et `0008` écrivait
qu'« il n'existe pas de motif syntaxique qui les distingue tous à coup sûr ».

Ces approximations étaient **justes par chance du corpus**, et la chance tournait. Mesuré à la
migration :

| Garde | Ce que le motif rendait faux |
|---|---|
| `5068` | `"utilisez robot.clickOn(carte)"` compté comme un appel ; un appel sur deux lignes retenu à l'aveugle, son argument lu **vide** |
| `loupe-4472` | `code.indexOf('{', ...)` faisant avaler 44 lignes des méthodes suivantes, 55 au lieu de 11 |
| `5278` | `String s = "}"` tronquant le corps d'une aide, donc son `lookup(` invisible, donc l'attente qui lui délègue **hors du cliquet** |
| `4974` | une ligne médiane de commentaire de bloc comptée comme un appel |
| `0008` | `sans_commentaires_java` prenant le `//` de `https://` pour un commentaire, sur 161 lignes |

Le cas de `5278` est le plus coûteux, et il ne se voit dans aucun compte : un faux négatif
**silencieux**, sur un cliquet à zéro, c'est-à-dire sur le seul verdict qu'on ne peut pas distinguer
d'un succès.

## Ce que la structure coûte, mesuré

    les cinq gardes, lecture par motif    0,18 s
    les cinq gardes, lecture par l arbre  6,26 s

Le facteur est de **35**, et il est plus élevé que celui du lot 0, où `4472` passait de 0,23 s à
1,13 s : les cinq gardes de ce lot partaient d'une lecture ligne à ligne, la moins chère de toutes.

Deux tiers du coût sont incompressibles, c'est le parsing lui-même. Le tiers restant a été payé
deux fois plutôt qu'une :

- `noeuds_de_type` descendait tout l'arbre en Python, et coûtait **0,66 s** contre 0,56 s pour le
  parsing. Une requête `tree-sitter` rend les mêmes nœuds aux mêmes positions en **0,18 s**,
  compilée une fois par jeu de types ;
- `5278` parsait le corpus **trois fois**, une par fonction publique. Une seule passe le ramène de
  2,49 s à 1,41 s, ce que `4472` avait déjà appris au lot 0.

## La décision

**Le coût s'accepte, et il se circonscrit par les `chemins` plutôt que par un retour au motif.**

Chaque garde migré déclare dans son `CONTRAT` les chemins qui l'engagent, ce que l'ADR 5340 impose
déjà à qui ne le fait pas. La porte se tait donc sur toute demande qui ne touche pas de Java, et le
coût n'est payé que par les lots qui en produisent la raison.

Le cliquet de `5340` descend de 47 à 43 par ce lot : les gardes migrés sortent de la population des
gardes non déclarés.

## Ce que la décision refuse

**Revenir au motif pour regagner du temps.** C'est la remise en cause qu'un lecteur futur fera
naturellement en voyant un garde à 1,4 s, et la réponse est dans les mesures ci-dessus : le motif ne
rendait pas le même verdict plus vite, il rendait un verdict que le corpus n'avait pas encore
contredit.

**Se restreindre au diff plutôt que déclarer ses chemins.** L'option existe et le chantier #5380 la
pose, mais un cliquet ne peut pas la prendre : il compte une **population** et la compare à un seuil
global. Un garde qui ne lirait que le diff rendrait un compte partiel, donc un cliquet faux.

**Et payer le coût sans le mesurer.** Le dépôt travaille en sens inverse - #5400 vient de retirer
134 s à `verifie_scripts.py` - et un garde qui rajoute du temps par négligence irait contre ce
travail. Chaque lot annonce son delta de durée, mesuré **à chaud** : le premier lancement d'un
environnement neuf vaut 51 s et ne mesure que la compilation du bytecode de Python.
