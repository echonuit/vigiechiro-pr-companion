## Context

Voir `proposal.md` pour le pourquoi. Trois faits du code cadrent l'approche.

- Un refus définitif porte sa cause depuis #5598, et elle est **enregistrée** : `depot_unite.cause_refus`
  (migration V41). Le record `DepotUnite` ne la relit pas ; seul le réarmement s'en sert, par une requête.
- L'écran lit « l'étape des archives est-elle offerte » à un seul endroit, `EtapeDesArchives.offerte`, qui
  compte les puces du fil. Cette question en cache deux : le **numéro** des étapes, et la **présence** de la
  carte et du dépôt manuel. Jusqu'ici elles avaient la même réponse.
- La forme d'un dépôt entamé se déduit des unités **déposées** du plan (`ServiceLot.modeDuDepotEntame`).
  Générer des archives sur le disque n'écrit rien dans le plan.

## Goals / Non-Goals

**Goals:**

- Une seule règle pour « le repli s'offre », lue par l'écran et par la commande.
- Un repli qui tient à la réouverture de l'écran, sans téléversement rejoué.

**Non-Goals:**

- Restreindre les archives du repli aux séquences refusées : le générateur n'est pas touché.
- Changer la forme du dépôt automatique, ou le sort de la forme archive à terme.
- Le mode ZIP et le hors connexion, où la carte est déjà offerte.

## Decisions

**1. La règle se lit dans le plan enregistré, au modèle.** `ServiceLot` rend le nombre de séquences refusées
sans recours du passage, par une requête de `DepotUniteDao`. Le compte est porté à l'écran par le modèle de
vue du **dépôt**, dont c'est l'état : posé sur `LotViewModel`, il lui faisait franchir le plafond `GodClass`. Écartés : lire le bilan en mémoire du dernier
téléversement (perdu à la réouverture, et absent de la commande après coup) ; ajouter la cause au record
`DepotUnite` (touche le lecteur de lignes et ses deux constructeurs pour un seul appelant).

**2. « Sans recours » veut dire : définitif, et d'une cause autre que les droits.** La cause des droits est
celle que `RearmementDepotUnites` réarme, et le DAO la reçoit déjà en paramètre : la règle la reçoit de la
même façon, pour qu'il n'y ait qu'un endroit qui nomme la cause levée par une reconnexion. Une ligne
antérieure à V41, sans cause, compte : elle ne se réarme pas. Seules les unités de type WAV comptent : un
dépôt en archives a déjà sa carte.

**3. Le fil reste à trois, et la vue distingue deux questions.** `EtapeDesArchives.offerte` garde les numéros.
Une seconde liaison, « les archives servent », vaut « offerte, ou repli offert » et porte la carte et les
éléments du dépôt manuel. Écarté : repasser le fil à quatre étapes, qui remettrait « 2. Générer les archives »
avant l'étape refusée et renuméroterait l'écran (réponse du porteur sur #5867).

**4. La carte du repli est la carte des archives, déplacée.** En repli elle passe sous la carte du
téléversement et change de titre et de consigne. Écarté : une seconde carte, qui dupliquerait le bouton, la
barre et la table.

**5. Le dépôt automatique garde sa forme.** Le plan reste en séquences WAV : les archives du repli sont sur le
disque et partent à la main. La règle 3 de l'ADR 5677 tient donc pour ce que l'application téléverse ; ce que
ce changement ajoute est un dépôt manuel à côté d'elle. La conséquence consignée par l'ADR 5824 (« un
téléversement en échec n'offre plus de repli manuel ») est levée. L'ADR de ce lot le dira.

## Risks / Trade-offs

- [Les archives contiennent toute la nuit, séquences déjà en ligne comprises] → hypothèse acceptée par le
  porteur sur #5867, qui sait seul ce que le portail en fait ; l'ADR la nomme comme une hypothèse.
- [Déplacer une carte dans son conteneur change l'ordre de tabulation] → le scénario d'interface lit la
  position de la carte, et l'aperçu est regardé.
- [`LotController` et `LotViewModel` sont près de leurs plafonds PMD] → le câblage va dans une classe de vue
  à part, le compte dans `SuiviEtapesLot`.

**6. Le dernier geste a deux portes.** La carte du repli porte « Marquer le passage déposé », et `deposer`
consulte l'état avant de préparer, comme `exporter-lot`. Écarté : rendre « Marquer déposé » à la dernière
étape en repli, qui porte déjà « Lancer la participation » et ne peut offrir qu'un geste (ADR 5676).

**7. La table rechargée et le bilan de récupérabilité lisent le plan jusqu'au bout.** Le premier oubliait le
drapeau définitif, le second ne lisait que le type des unités : deux lectures partielles du même plan, que le
repli rendait visibles.
