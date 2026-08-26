---
type: adr
title: "Un test qui mesure monte une scène habillée"
status: stable
article: A4
chantier: "#3826, lot 1 des suites #3802"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "ScenesHabilleesTest#les_tests_qui_mesurent_montent_une_scene_habillee"
verified:
  - by: machine:ci
    at: 2026-08-16
relations:
  prolonge: ["3374"]
---

# Un test qui mesure monte une scène habillée

## Contexte

L'ADR 3374 pose `Habillage` comme point de passage unique de ce que porte une fenêtre - la police
embarquée et le trio du chrome - et l'étend explicitement aux **captures**, pour qu'un aperçu montre
l'écran de l'utilisateur par construction.

Son garde ne parcourait que **`src/main/java`**. L'invariant s'arrêtait donc à la frontière de la
production, alors que ce sont les **tests** qui *mesurent* : dix d'entre eux comparaient des hauteurs
et des largeurs sur une scène qu'ils montaient eux-mêmes.

## Le mécanisme, et pourquoi il est invisible

`Typographie.installer()` garde un `static boolean` : l'enregistrement de la police est **global au JVM
et fait une seule fois**. Avec `reuseForks=true`, un test qui ne l'appelle pas voit la police du produit
**si un voisin l'a installée avant lui**, et celle du système sinon. C'est l'**ordre d'exécution** qui
décide du verdict.

Mesuré : `CartesAccueilTest` a rendu **vert à 8 h 14 et rouge à 15 h 34**, sur le **même commit** et la
**même image** `macos-26-arm64`. Puis, joué **seul** sous macOS - donc sans voisin -, il **échoue**.
L'écart tient à 20,43 px contre 17,666 px selon la police rendue.

**Le défaut ne se voit pas depuis un poste Linux.** `Noto Sans` y est une police système : la suite
locale la trouve installée ou non. Une expérience d'isolement menée là est **aveugle par
construction** - elle a été faite, elle n'a rien montré, et elle a failli servir de preuve.

## Décision

**Un test qui mesure une géométrie monte sa scène avec `Habillage.scene(...)`.** Un garde le vérifie,
étendu à `src/test/java`.

### Pourquoi la règle ne vise pas tous les `new Scene`

`new Scene(` apparaît dans une **centaine** de tests, et l'immense majorité a raison de l'écrire : ils
vérifient un **comportement** - un clic, un intitulé, une navigation - et se moquent de la police.
Interdire la construction directe partout serait une règle fausse, donc une règle qu'on désactive.

Le garde vise donc l'**usage** - construire une scène **et** demander une dimension - et non l'appel.
C'est le même choix que le cliquet de #3632, qui vise qui *appelle* `Files.walk` et non qui le
*consomme*.

### Le contrôle de non-vacuité est fabriqué

Une fois les dix corrigés, **plus aucun fichier du dépôt ne correspond au motif**. Le garde
certifierait alors une absence qu'il ne saurait plus constater - le défaut qu'il est censé empêcher,
appliqué à lui-même. Il monte donc trois fichiers jetables : un coupable, un innocent qui ne mesure
pas, un en règle qui passe par `Habillage`.

## Conséquences

- **Le verdict de ces tests ne dépend plus de leurs voisins**, ni de la machine qui les joue.
- **Ce que la règle coûte** : un test de mesure ne peut plus se contenter de `new Scene`. C'est une
  ligne de plus, et elle dit ce qu'elle fait.
- **Le correctif est en grande partie prophylactique, et il faut le dire.** Les dix passaient sous
  macOS : leurs assertions ont de la marge. Seul `CartesAccueilTest` était assez serré pour basculer.
  Ce qu'ils partagent est la **faiblesse de structure**, pas un défaut avéré - le jour où l'un resserre
  une assertion ou change un libellé, il bascule, et personne ne saura pourquoi.
- **Ce que la CI voit exactement n'est pas mesuré.** L'ADR 3361 note que `sans-serif` se résout en
  « une police plus large » sur le runner Ubuntu. Si ce n'est pas `Noto Sans`, ces dix tests pouvaient
  y basculer aussi - la question reste ouverte, faute d'avoir sondé le runner.

## Alternatives écartées

- **Interdire `new Scene` dans tous les tests** : faux pour une centaine de tests de comportement, et
  une règle fausse se fait retirer plutôt que corriger.
- **Une liste d'exemption explicite** (tout test de `view` passe par `Habillage`, sauf ceux qui
  déclarent pourquoi) : plus lisible en théorie, mais elle demandait d'annoter une centaine de fichiers
  pour en protéger dix. La forme étroite a été choisie **après** avoir traité les dix, pas avant - et
  c'est ce qui a permis de constater que le faux positif redouté n'existait pas.
