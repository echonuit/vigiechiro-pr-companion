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
| RUNNER, JVM effondrée ou couche native absente | 9 % | rejouer une fois, et le consigner |
| INDÉTERMINÉ | 5 % | lire le journal, ne pas rejouer sans l'avoir lu |

**Un rouge sur cinq seulement vaut un rejeu.** Les quatre autres reviennent au tirage suivant, chez
quelqu'un d'autre, sur une demande sans rapport. Le rejeu ne supprime pas le rouge : il le déplace,
et il en efface la trace, la tentative rejouée écrasant le verdict de la précédente.

**La mesure a refusé le remède que l'issue demandait.** #4187 attendait une conduite pour le runner.
Le runner pèse 9 %. Ce qui pèse 56 %, ce sont nos propres bancs, et aucune conduite ne les répare :
il faut les corriger, ce que #4845, #4847 et #4696 portent. C'est l'ADR 4853 appliquée à un chantier
de CI plutôt qu'à un service.

**Une cause non reconnue se nomme telle quelle.** `INDÉTERMINÉ` existe parce que trois tentatives ne
portent aucune erreur dans les journaux lus. Les ranger de force aurait inventé une cause, et l'ADR
2213 refuse cela.

## La cascade existe à deux niveaux, et un seul était modélisé

Le relevé distinguait déjà, **entre tests**, celui qui tombe en tête de ceux qu'il emporte. Le même
phénomène joue **entre jobs** : 11 tentatives ne portent aucun test tombé, seulement un
`The operation was canceled` qui dit qu'une étape voisine avait déjà échoué. Chercher la cause dans
le job annulé ne mène nulle part, et c'est pourtant le journal qu'on ouvre en premier.

## Ce qui se lit est la fin du journal, jamais le journal

Deux mesures plausibles et fausses ont précédé la bonne.

Compter les exceptions du journal entier faisait remonter `java.net.ConnectException` à 348 fois,
avec des comptes **identiques à l'unité** sur sept runs : une coupure réseau qu'un test provoque
exprès. Chercher les motifs dans le texte entier rangeait ensuite **20 tentatives sur 20** sous « un
garde a refusé », parce qu'un garde **vert** imprime aussi le mot `REFUSE`.

Le classement ne lit donc que les douze lignes précédant la **dernière** ligne d'erreur.

## Ce qui prouve que le classement voit

Vingt-quatre témoins, sur des extraits réels. Quatre mutations, chacune vérifiée comme appliquée
avant d'être jouée. Trois meurent. **Relire le journal entier au lieu de sa fin a survécu**, faute
d'un témoin où le motif traîne loin au-dessus d'une erreur d'un autre type : le faux positif même qui
avait produit le 20 sur 20. Ce témoin existe désormais.

## Conséquences

- Le seuil d'effondrement vaut **50** tests tombés. Le plus gros rouge normal du dépôt en 21 jours en
  a fait tomber 2 ; un effondrement en emporte plus de 1 300. Aucune valeur intermédiaire n'a été
  observée, et ce vide est ce qui rend le seuil sûr plutôt qu'arbitraire.
- Un rouge de runner qui revient **deux fois dans la même semaine avec la même signature** cesse d'en
  être un : c'est une dépendance à ce que l'image ne garantit pas. Les deux tentatives à couche
  native absente relevées sont sous ce seuil.
- La conduite vit dans `dev-docs/ci-cd-release.md`, où l'on va quand la CI rougit.

## Alternatives écartées

- **Un rejeu automatique borné.** Il rendrait vert 19 % des cas et masquerait les 81 % restants,
  c'est-à-dire précisément ceux qu'il faut voir. Le dépôt refuse déjà les rejeux silencieux.
- **Un garde qui bloque sur un banc instable.** Rien à bloquer : le rouge est déjà là. Le manque
  était de savoir à qui il appartient.
