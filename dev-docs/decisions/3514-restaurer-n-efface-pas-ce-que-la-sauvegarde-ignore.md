---
type: adr
title: "Restaurer n'efface pas ce que la sauvegarde ignore, et ne tient pas de journal"
status: stable
article: A17
chantier: "#3514, lot 1 (#3559) du chantier #3518 ; ADR écrite à la passe 10 de la clôture du lot"
decided_at: 2026-08-10
verification: certaine
enforced_by:
  - "RestaurationCompleteTest#un_fichier_occupant_la_destination_fait_refuser"
verified:
  - by: machine:ci
    at: 2026-08-10
---

# Restaurer n'efface pas ce que la sauvegarde ignore, et ne tient pas de journal

## Contexte

La restauration complète remplaçait les dossiers de son **un par un**, après avoir déjà remplacé la
base. Une panne au troisième dossier laissait une base restaurée, deux dossiers neufs, et le reste tel
quel : un état qu'aucune des deux versions ne décrit, et dont l'utilisateur ne sait rien.

Deux questions se sont posées en chemin, et aucune n'avait de réponse évidente.

## Décision 1 - Une destination qui porte autre chose est un refus, pas un écrasement

Basculer, c'est **remplacer la destination entière** par le dossier étalé. Si la destination contient
un fichier que la sauvegarde ne contient pas - un WAV rangé là à la main, un export oublié - ce
fichier disparaît sans avoir été nommé.

`restaurer` ne doit pas devenir un moyen détourné d'effacer ce qui traînait là. La bascule **refuse
avant d'écrire** quand la destination porte quelque chose d'inconnu de la sauvegarde.

Le refus porte sur les **fichiers**, pas sur les dossiers : un dossier vide supplémentaire n'est pas
une donnée à protéger, et refuser dessus rendrait la restauration capricieuse pour rien.

Ce cas a été découvert par un test qui **est passé au vert tout seul** : écrit pour vérifier autre
chose, il a cessé d'échouer quand la copie a été déplacée vers une zone temporaire, révélant que le
fichier intrus était désormais silencieusement détruit. Un rouge inattendu est une trouvaille ; un
**vert** inattendu aussi.

## Décision 2 - Pas de journal de bascule, et c'est une décision

Un journal des bascules, relu au démarrage, permettrait de terminer ou d'annuler une restauration
interrompue, et supprimerait l'état mixte résiduel. Il est **écarté**.

La raison n'est pas le coût mais la **nature de sa panne** : un journal est un fichier de plus, écrit
pendant l'opération qu'il protège, et relu par un chemin de démarrage qui doit fonctionner quand tout
le reste a échoué. Sa défaillance serait du même genre que celle qu'il répare, avec un dispositif de
plus à surveiller - et [ADR 2748](2748-un-dispositif-qui-peut-ne-rien-verifier-le-dit.md) dit ce qu'un
dispositif qu'on ne surveille pas finit par valoir.

Ce qui est retenu à la place : **réduire la fenêtre** plutôt que la rattraper. Étaler puis basculer
ramène le risque d'une copie de plusieurs minutes à une suite de renommages de quelques
millisecondes. Ce n'est **pas de l'atomicité**, et il ne faut pas le dire : basculer trois dossiers,
ce sont trois renommages, et aucune astuce ne les rendra indivisibles.

Cette décision est celle qu'un lecteur futur défera le plus volontiers, parce qu'un journal se
conçoit facilement et que son absence ne laisse aucune trace dans le code. Elle est donc écrite ici,
avec sa raison.

## Ce que cette ADR ne dit plus

L'étalement lui-même a été **conditionné à la place disponible** par
[ADR 3563](3563-le-regime-de-restauration-suit-la-place-disponible.md), qui amende l'ADR 2727 : ce
qui est décrit ici comme « le » régime est devenu le régime **nominal**, employé quand la place le
permet. Les deux décisions ci-dessus valent dans les deux régimes.
