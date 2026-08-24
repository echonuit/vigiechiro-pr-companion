---
type: adr
title: "Un fil d'Ariane élide des segments, il ne rogne pas des libellés"
status: stable
article: A23
chantier: "#3798, dernière suite du chantier #3536"
decided_at: 2026-08-16
verification: certaine
enforced_by:
  - "src/test/java/fr/univ_amu/iut/commun/view/FilArianeElisionTest.java"
verified:
  - by: machine:ci
    at: 2026-08-16
relations:
  complète: ["3760"]
---

# Un fil d'Ariane élide des segments, il ne rogne pas des libellés

## Contexte

L'[ADR 3760](3760-le-deficit-se-porte-il-ne-se-repartit-pas.md) a tranché que le déficit d'une barre
**se porte** : un contrôle est désigné, et les autres restent entiers. Le fil d'Ariane a été ce porteur
pour la barre du haut, et le titre comme le bouton ← Retour ont cessé de se faire rogner.

Ce que le fil ferait de ce déficit n'a pas été décidé. Il subissait le comportement par défaut d'une
`HBox`.

## Le défaut

**Une `HBox` répartit.** Mesuré sur l'écran le plus profond, aux deux largeurs que `TailleOuverture`
livre :

| Segment | voulu | à 1100 | à 900 | perdu |
|---|---|---|---|---|
| Accueil | 49 | 49 | **17** | 32 |
| Mes sites | 63 | 63 | 24 | 39 |
| Carré 640380 | 88 | 88 | 49 | 39 |
| Détails du passage N° 1 | 151 | 151 | 112 | 39 |
| Diagnostic matériel | 128 | 128 | 89 | 39 |

Le principe de l'ADR 3760 était donc **tenu à l'étage de la barre et enfreint un cran plus bas**, par le
composant même qu'on avait chargé de le porter. Le total réparti, 188 px, est exactement celui que
l'ADR 3760 avait nommé.

Et une répartition égale en pixels est une amputation **inégale** en information : « Accueil » perd 65 %
de sa largeur et se réduit à un « A… », quand « Détails du passage N° 1 » n'en perd que 26 % et reste
lisible. Le déficit frappe le plus fort les libellés les plus courts, sans rapport avec leur utilité.

## Décision

**Un fil d'Ariane élide des segments entiers ; il ne rogne aucun libellé.** Le milieu retiré devient un
menu « … » où chaque segment garde son libellé complet et son action : un ancêtre change de forme,
jamais d'existence.

**La proximité l'emporte.** Le dernier segment est gardé quoi qu'il arrive ; on remonte ensuite vers ses
ancêtres tant qu'ils tiennent ; l'ancre « Accueil » ne vient qu'en dernier, parce qu'elle a un recours
que les autres n'ont pas - le titre de l'application et le bouton ← Retour y mènent déjà.

Cet ordre a été tranché par la mesure et non par un avis : à 900, servir l'ancre d'abord rend
`Accueil › … › Diagnostic matériel` ; servir les proches d'abord rend
`… › Détails du passage N° 1 › Diagnostic matériel`, dans le même espace.

## Ce que la mesure a corrigé en chemin

**Le remède écrit dans l'issue ne tenait pas.** « Garder le premier et les deux derniers » demande
380 px à 900, où il n'y en a que 351. Un schéma **fixe** ne peut pas convenir : ce qui tient dépend de
la place.

**Trois pixels d'optimisme suffisent à reproduire le défaut.** Le premier calcul ignorait le rembourrage
de la barre et arrondissait les largeurs vers le bas : les libellés se coupaient de 1 à 3 px, c'est-à-dire
exactement ce que ce composant existe pour supprimer.

**Dix-huit pixels de flèche de menu changent ce que l'utilisateur voit.** Un `MenuButton` par défaut
coûte 34 px pour un caractère ; réduit à 16, il laisse « Détails du passage N° 1 » dans le fil à 900. La
flèche a donc été effacée pour de bon, pas seulement rendue transparente.

**Un fil d'un seul segment posait l'ancre deux fois.** L'accueil - l'état de départ de l'application.
Trouvé par une exception au premier lancement, pas par un raisonnement.

## Conséquences

**L'exemption est rendue.** Les segments ne portent plus `abregeable`, la classe qui fait taire
[LisibiliteCapture]. Le juge qui refuse d'écrire un aperçu tronqué retrouve sa juridiction sur le fil, et
le silence déclaré par l'ADR 3760 n'a plus lieu d'être. C'est la meilleure part de ce chantier : plutôt
qu'un dispositif de plus, un dispositif existant qu'on cesse de bâillonner.

**La largeur minimale du fil est nulle**, et ce n'est pas cosmétique : sans cela, la barre distribue la
place en tenant compte du minimum du fil, lequel dépend de son contenu. Poser le menu lui faisait gagner
ou perdre une douzaine de pixels, qui relançaient un recalcul, qui changeait le contenu.

**Ce qui n'est pas prouvé** : que le choix des segments gardés soit le meilleur. Les tests vérifient
qu'aucun n'est coupé et qu'aucun n'est perdu. Lequel mérite la place est l'arbitrage ci-dessus, et il se
rouvre par cette ADR, pas par un test qui rougirait.
