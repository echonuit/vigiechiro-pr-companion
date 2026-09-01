---
type: adr
title: "Un rouge se classe avant de se rejouer"
status: stable
article: A3
chantier: "#4187, lot 3 de l'EPIC #4804"
decided_at: 2026-08-31
verification: certaine
enforced_by:
  - "scripts/methode/releve-les-bancs-instables.py"
verified:
  - by: machine:ci
    at: 2026-08-31
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-31
---

# Un rouge se classe avant de se rejouer

## Contexte

Un rouge de CI se rejouait à l'aveugle, et le geste passait pour raisonnable : la suite repartait
souvent verte sans qu'une ligne ait changé. L'issue #4187 en tirait « la suite rougit sur des pannes
JavaFX du runner », et citait trois signatures internes à JavaFX.

Rien ne mesurait la part de vrai. Le relevé du lot 0 rangeait sous un seul mot, « échouées pour autre
chose », toutes les tentatives où aucun test n'était nommé.

## Le défaut

**Un seau sans nom est un seau qu'on ne conteste pas.** Sur 21 jours, 1 233 tirages et 55 relances,
ce seau valait **20 tentatives sur 57**, et il portait quatre causes qui n'appellent pas la même
conduite.

## Décision

**Un rouge se classe avant de se rejouer**, par `releve-les-bancs-instables.py --classe`.

| À qui il appartient | Part | Conduite |
|---|---:|---|
| DÉPÔT, un ou deux bancs qui vacillent | 56 % | ne pas rejouer, nourrir l'issue du banc |
| CASCADE, annulé car une autre étape avait rougi | 19 % | ne pas rejouer, lire l'étape qui a rougi la première |
| FORGE, artefact ou action indisponible | 11 % | rejouer, une fois |
| RUNNER, JVM effondrée, couche native absente, ou **cause traversant la couche graphique** | 9 % | rejouer une fois, et le consigner |
| INDÉTERMINÉ | 5 % | lire le journal, ne pas rejouer sans l'avoir lu |

**Un rouge sur cinq seulement vaut un rejeu.** Les autres reviennent au tirage suivant, chez
quelqu'un d'autre. Le rejeu ne supprime pas le rouge : il le déplace, et la tentative rejouée efface
le verdict de la précédente.

**La mesure a refusé le remède demandé.** L'issue attendait une conduite pour le runner, qui pèse
9 % ; ce qui pèse 56 %, ce sont nos bancs, qu'aucune conduite ne répare. ADR 4853, sur un chantier
de CI.

**Une cause non reconnue se nomme telle quelle.** `INDÉTERMINÉ` existe parce que trois tentatives ne
portent aucune erreur : les ranger de force aurait inventé une cause (ADR 2213).

## La cascade existe à deux niveaux, et un seul était modélisé

Le relevé distinguait déjà, **entre tests**, celui qui tombe en tête de ceux qu'il emporte. Le même
phénomène joue **entre jobs** : 11 tentatives ne portent aucun test tombé, seulement un
`The operation was canceled` qui dit qu'une étape voisine avait déjà échoué. Chercher la cause dans
le job annulé ne mène nulle part, et c'est pourtant le journal qu'on ouvre en premier.

## Le volume ne suffit pas : la COUCHE décide (#5036)

Le classement décidait « runner » sur deux signes : couche native absente, ou plus de cinquante tests
tombés. Un défaut de rendu qui n'emporte **qu'un** test lui échappait.

Il lit désormais la **cause profonde**, celle que `Caused by:` porte dans le bloc surefire, et cherche
si elle traverse la couche graphique. **Cinq formulations plus séduisantes ont été réfutées sur des
journaux réels**, que #5036 porte : la plus instructive est que 61 % des piles d'une suite **verte**
sont entièrement étrangères. La sixième classe correctement les **huit** journaux disponibles, et
généralise ce que `OSPango` faisait pour un seul symptôme : nommer la couche plutôt que compter.

## Ce qui se lit est la fin du journal, jamais le journal

Deux mesures fausses l'ont établi, et l'ADR 4804 en porte la règle générale. Le classement ne lit que
les douze lignes précédant la dernière ligne d'erreur.

## Ce qui prouve que le classement voit

Vingt-quatre témoins, sur des extraits réels. Quatre mutations, chacune vérifiée comme appliquée
avant d'être jouée. Trois meurent. **Relire le journal entier au lieu de sa fin a survécu**, faute
d'un témoin où le motif traîne loin au-dessus d'une erreur d'un autre type : le faux positif même qui
avait produit le 20 sur 20. Ce témoin existe désormais.

## Conséquences

- Le seuil d'effondrement vaut **50**. Le plus gros rouge normal du dépôt en 21 jours en a fait
  tomber 2, un effondrement plus de 1 300 : ce **vide** rend le seuil sûr plutôt qu'arbitraire.
- Un rouge de runner qui revient **deux fois la même semaine avec la même signature** cesse d'en être
  un : c'est une dépendance à ce que l'image ne garantit pas.
- La conduite vit dans `dev-docs/ci-cd-release.md`, où l'on va quand la CI rougit.

## Alternatives écartées

- **Un rejeu automatique borné.** Il rendrait vert 19 % des cas et masquerait les 81 % restants,
  c'est-à-dire précisément ceux qu'il faut voir. Le dépôt refuse déjà les rejeux silencieux.
- **Un garde qui bloque sur un banc instable.** Rien à bloquer : le rouge est déjà là. Le manque
  était de savoir à qui il appartient.
