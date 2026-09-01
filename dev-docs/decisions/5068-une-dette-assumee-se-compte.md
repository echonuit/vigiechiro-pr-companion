---
type: adr
title: "Une dette assumée se compte"
status: stable
article: A9
chantier: "#5068, lot 1 de l'EPIC #5041"
decided_at: 2026-09-01
verification: certaine
enforced_by:
  - "scripts/adr/5068-clic-sur-reference-tenue.py"
ratchet: 38
verified:
  - by: machine:ci
    at: 2026-09-01
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-01
---

# Une dette assumée se compte

## Contexte

`clickOn(Node)` calcule son point de clic depuis `node.getScene()`. Une scène nulle signifie que le
nœud n'est plus attaché au graphe au moment du clic : il a été résolu plus haut, et rien ne garantit
qu'il ait survécu entre les deux.

L'issue #4696 portait ce défaut. Elle a été **fermée sans remède**, sur une mesure : le relevé le
compte à **1/1234** sur 21 jours, la fréquence la plus basse de sa liste, et il n'est pas retombé
depuis. Convertir 38 sites pour cela n'est pas ce que la mesure désigne.

## Le défaut

**Une dette fermée sans compteur cesse d'exister pour tout le monde.** Trente-huit sites peuvent
devenir quarante-cinq sans que rien ne le dise, et le prochain lecteur du dépôt ne saura pas que la
question s'est posée.

C'est exactement ce que l'article A9 refuse : la dette se tient par un cliquet, pas par un nettoyage.
Fermer #4696 était juste ; la fermer **sans compter** ne l'était pas.

## Décision

**Une dette qu'on assume se compte.** Le cliquet vaut **38**, la valeur mesurée, et il ne peut que
descendre.

Il borne le nombre de sites **exposés**, non le nombre de défauts : une référence tenue sur un nœud
qui ne bouge pas est sans danger. C'est un cliquet de dette, pas un détecteur de bogue, et le dire
fait partie du garde.

**Trois formes sont écartées, et chacune a fait surcompter pendant #4804** :

| Forme | Pourquoi elle ne porte pas le défaut |
|---|---|
| `clickOn("#champCode")` | un sélecteur littéral se **résout au moment du clic** |
| `clickOn(BOUTON_EXPORTER)` | une constante `String` du même fichier est un sélecteur sous un autre nom |
| une citation en commentaire | ce n'est pas un appel |

La première mesure de #4804 comptait **42** faute de les écarter. Les trois sont des cas négatifs du
garde.

## Le remède, écrit pour qui reprendra

**Re-résoudre la cible dans l'attente**, et surtout **pas** `GesteVisible.cliquer(robot, Node)` :
cette surcharge commence par `moveTo`, qui lit `node.getScene()` exactement comme `clickOn`. Elle
déplacerait la `NullPointerException` d'une ligne sans la retirer.

C'était le remède annoncé par #4696, et il est faux. L'écrire ici épargne un cycle à qui reprendra.

## Conséquences

- L'aide partagée `GesteVisible` est **exemptée** : elle porte le seul clic sur nœud qui soit
  délibéré, puisqu'elle **est** le geste commun.
- Le cliquet descend quand un site est converti, jamais parce qu'un fichier disparaît : il compte des
  **appels**, pas des fichiers.
- Si le défaut se reproduit, #4696 rouvrira avec 38 sites déjà inventoriés, la commande qui les
  compte, et le remède juste.

## Alternatives écartées

- **Convertir les 38.** C'est ce que #4696 proposait. La mesure le refuse : 1/1234, et pas retombé.
- **Fermer sans compter.** C'est ce qui a été fait d'abord, et cette ADR le corrige : une dette
  invisible n'est pas une dette assumée, c'est une dette oubliée.
