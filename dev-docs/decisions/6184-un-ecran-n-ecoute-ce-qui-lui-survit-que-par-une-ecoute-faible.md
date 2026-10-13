---
type: adr
title: "Un écran n'écoute ce qui lui survit que par une liaison ou une écoute faible, et un test le veut rendu une fois quitté"
status: stable
article: A22
chantier: "#6178 (l'interface tient O3 et O5), lot #6184"
decided_at: 2026-10-13
verification: certaine
enforced_by:
  - "EcranQuitteEstRenduTest#chaque_ecran_qui_recoit_un_singleton_observable_est_rendu_une_fois_quitte"
  - "EcranQuitteEstRenduTest#tout_controleur_de_vue_qui_recoit_un_singleton_surveille_est_dans_l_inventaire"
  - "EcouteFaibleTest#la_source_ne_retient_pas_le_porteur"
verified:
  - by: machine:ci
    at: 2026-10-13
relations:
  prolonge: ["3651-un-cycle-de-vie-qu-on-peut-oublier-est-mal-place"]
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-13
---

# Un écran n'écoute ce qui lui survit que par une liaison ou une écoute faible, et un test le veut rendu une fois quitté

## Contexte

Un singleton vit autant que l'application, un écran le temps d'être affiché. Quand l'écran pose un
écouteur sur une propriété du singleton, la liste des écouteurs tient l'écouteur, qui tient ce qu'il
capture : un contrôle, et par lui tout l'écran, jusqu'à la fermeture.

La fiche d'un carré le faisait sur `EtatConnexion`. #6170 l'a corrigée par un écouteur faible gardé
dans un champ, sans que la règle soit écrite. Rien ne rougit à l'oubli : la fuite ne se lit qu'au
tas, plusieurs ouvertures plus tard.

L'[ADR 3651](3651-un-cycle-de-vie-qu-on-peut-oublier-est-mal-place.md) avait traité un cas voisin, la
révision des données, en confiant l'abonnement au `Navigateur`. #6184 demandait s'il fallait faire de
même pour la connexion, ou tenir une règle générale par un garde.

## Décision

**Un écran suit un objet plus durable que lui par une liaison, ou par une écoute faible que son
porteur tient. Jamais par un `addListener` direct.**

- Une liaison (`bind`, `bindBidirectional`) ne retient rien : c'est le premier choix.
- Quand il faut un écouteur, `EcouteFaible.poser(porteur, source, ecouteur)` l'inscrit faible auprès de
  la source et le fait tenir par le nœud qu'il met à jour. Les deux moitiés comptent : sans la
  première l'écran reste, sans la seconde l'écouteur tombe au premier ramasse-miettes et l'écran cesse
  de suivre sans rien dire.

**Un test d'exécution tient la règle**, `EcranQuitteEstRenduTest`, en trois cas.

1. Il ouvre par la navigation chaque écran de l'inventaire `EcransSuivantUnSingleton`, constate qu'il
   est vivant, le quitte, insiste auprès du ramasse-miettes et refuse que son contrôleur ou sa vue
   soient encore tenus.
2. Il lit la composition : tout singleton lié qui expose un observable est surveillé, ou déclaré tenu
   ailleurs avec sa raison.
3. Il lit ce que la composition remet à chaque contrôleur de vue, directement ou par un collaborateur
   qu'elle lui construit : celui qui reçoit un singleton surveillé est ouvert par le premier cas, ou
   déclaré hors historique avec sa raison.

**L'abonnement à la connexion ne passe pas au `Navigateur`.** Tout écran qui suit la révision se
rechargerait alors à chaque connexion, et le remède n'aurait couvert qu'un singleton sur cinq.

## Pourquoi un test d'exécution, et pas une règle sur le texte

Le garde a trouvé à son premier tir un écran qu'aucune relecture n'avait vu : celui des réglages.
`ControleursReglages` posait deux écouteurs forts sur des propriétés de `ReglagesReactifs`, dans une
aide qui reçoit la propriété et non le singleton. Chaque ouverture des réglages laissait un écran en
mémoire. Une règle qui chercherait `addListener` près d'un singleton ne l'aurait pas vu, et aurait
refusé des écouteurs sans danger posés sur le modèle de l'écran lui-même.

Le même tir a corrigé l'inventaire : cinq singletons observables arrivent dans des vues, et non les
trois lus dans le code.

## Ce que le garde ne couvre pas

- **Les modales et le chrome**, déclarés hors historique : le test ne les ouvre pas, et rien ne dit
  ici qu'ils sont rendus.
- **Les vingt contrôleurs de vue sur trente** à qui la composition ne remet aucun singleton surveillé.
  Le troisième cas rougit le jour où l'un d'eux en reçoit un.
- **Un singleton qui se fait écouter sans exposer d'observable**, par un rappel qu'on lui confie : le
  deuxième cas ne le voit pas.
- **Un écouteur posé plus tard qu'à l'ouverture.** Chaque écran est ouvert sur une base d'un passage
  et d'une observation, puis quitté sans geste. TestFX garde tout événement tiré sur la fenêtre, et un
  clic retiendrait un écran à lui seul.
- **L'écoute elle-même** : le test dit qu'un écran est rendu, pas qu'il suivait. `EcouteFaibleTest`
  tient cette moitié pour l'outil seul.

## Les mutations vues rouges

| Mutation | Ce qui rougit |
|---|---|
| `EcouteFaible` inscrit l'écouteur fort | l'écran des réglages retenu, et `EcouteFaibleTest` |
| `EcouteFaible` ne confie plus l'écouteur au porteur | `EcouteFaibleTest` : l'écoute tombe |
| l'écouteur fort remis sur la fiche du carré | la fiche retenue |
| un écran retiré de l'inventaire | les cas 1 et 3 |
| un singleton surveillé remis à un contrôleur hors inventaire | le cas 3 |
| des références faibles sur autre chose que l'écran | le témoin de vie du cas 1 |

## Conséquences

- L'écran des réglages n'est plus retenu après avoir été quitté. Rien ne change à l'usage.
- Le test coûte douze secondes, joué seul.
- Un nouvel écran qui reçoit un singleton surveillé s'ajoute à l'inventaire avec le geste qui
  l'ouvre : le troisième cas le réclame.
- La fiche d'un carré garde son écouteur faible en champ, que le garde surveille.
