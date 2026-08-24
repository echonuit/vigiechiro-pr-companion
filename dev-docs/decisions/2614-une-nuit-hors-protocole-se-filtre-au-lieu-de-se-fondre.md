---
type: adr
title: "Une nuit hors protocole se filtre, au lieu de se fondre dans le lot"
status: stable
article: A21
chantier: "#2614 (suite du lot 2 de l'EPIC #2348)"
decided_at: 2026-07-28
verification: certaine
enforced_by:
  - "NatureNuitTest#un_passage_marque_est_une_participation_opportuniste"
verified:
  - by: machine:ci
    at: 2026-07-28
---

# Une nuit hors protocole se filtre, au lieu de se fondre dans le lot

## Contexte

Une **participation opportuniste** (#2525) est une nuit enregistrée sur le carré d'un tiers, quand l'occasion se présente. Elle porte exactement les mêmes données qu'une nuit du protocole, mais elle ne compte pas de la même façon : elle est exemptée de R3 (fenêtre calendaire) et de R4 (intervalle conseillé), et le solde de saison l'écarte de son décompte.

Le marquage est arrivé **pendant** le lot 2, et l'écran d'activité, écrit en parallèle, ne l'a jamais connu. Résultat : dans les vues agrégées (Activité de la nuit, Espèces & observations), une nuit opportuniste s'ajoutait aux autres sans que rien ne le signale. Une courbe transverse mêlait alors des nuits comparables entre elles et des nuits qui ne le sont pas, et l'inventaire comptait sur le même pied une espèce vue sous protocole et une espèce vue au hasard d'un passage ailleurs.

C'est un défaut d'intégration, pas d'implémentation : chaque morceau était juste, personne n'avait relié les deux.

## Décision

**La nature de la nuit devient une dimension de filtre à part entière**, offerte au menu « + Filtre » des deux écrans agrégés, avec deux lectures : « Protocole » et « Opportuniste ».

Trois choix la précisent :

1. **Un filtre, pas un décompte séparé.** L'écran ne tranche pas à la place de l'utilisateur : les deux natures restent affichées ensemble par défaut, et c'est une question posée à la main (« et si je ne gardais que le protocole ? ») qui les sépare. Une exclusion d'office aurait caché des données sans le dire.
2. **L'absence de marquage vaut « Protocole ».** C'est le sens même de la table de présence `passage_opportuniste` (V34), où seule l'exception coûte une ligne. Une ligne sans passage rattaché suit la même règle plutôt que de disparaître des deux lectures : un filtre qui escamote en silence ce qu'il ne sait pas classer ment sur ce qu'il montre.
3. **Un port de lecture distinct du port d'écriture.** `NuitsOpportunistes` (lecture groupée) tient séparé de `MarquageOpportuniste` (écriture) : `importation`, qui ne fait que marquer, ne se voit pas offrir une lecture dont elle n'a que faire, et `analyse`, qui ne fait que lire, n'hérite pas d'un pouvoir d'écriture.

Le ViewModel en garde un **instantané immuable**, renouvelé à chaque chargement : le critère consulte l'ensemble une fois par ligne filtrée, ce qu'une requête par ligne ne supporterait pas.

## Conséquences

- La dimension existe sur **les deux écrans** avec les mêmes libellés : une nuit opportuniste se lit pareil, qu'on regarde une courbe ou un inventaire.
- Les deux écrans partagent désormais une fabrique de critère à valeur unique dans le socle (`CritereListe.simple`), pendant de `CritereListe.multiple` livré en #2615. La liste déroulante que chaque écran réécrivait pour lui-même disparaît.
- L'instantané est pris **au chargement**, pas à l'ouverture de la puce : marquer une nuit opportuniste depuis un autre écran ne se reflète qu'au prochain rafraîchissement de celui-ci. Acceptable, le marquage est un acte rare, posé à l'import ou au rattachement.
- **Aucun repère visuel** n'est ajouté dans les listes, alors que l'issue l'envisageait. Les deux écrans agrègent : une ligne d'inventaire par espèce, une courbe par espèce, chacune pouvant recouvrir plusieurs nuits des deux natures. Un badge par ligne y désignerait un mélange, pas un fait. Le jour où une vue montrera la nuit elle-même comme ligne, la question se reposera, et se posera bien.

## Alternatives écartées

- **Exclure les nuits opportunistes par défaut.** Cohérent avec le solde de saison, qui les écarte. Mais le solde répond à « ai-je rempli mes obligations ? », là où ces écrans répondent à « qu'ai-je entendu ? » : question à laquelle une nuit opportuniste répond aussi bien qu'une autre. Écarter d'office aurait retranché des observations réelles sans le dire.
- **Porter le drapeau dans la ligne** (`ContactHoraire`, `ObservationAnalyse`). La dimension se lirait sans port ni instantané, au prix d'une composante de plus sur deux projections déjà larges, et d'un aller-retour en base à chaque projection : pour un fait vrai de la nuit, pas de la ligne. Écarté pour la raison même qui a mis ce fait dans une table latérale plutôt que dans `passage` (ADR 2525), et dans le sens de l'EPIC arité #2483.
- **Injecter `PassageOpportunisteDao` directement dans `analyse`.** Le plus court. Il aurait fait dépendre une feature du DAO d'une autre, là où les ponts existants (`CoordonneesPoint`, `MarquageOpportuniste`, `InventaireBrutsSource`) montrent l'usage : une interface étroite, qui dit ce qu'on prend et rien de plus.
- **Ajouter la lecture à `MarquageOpportuniste`.** Un port au lieu de deux, mais un port que ses deux consommateurs n'utiliseraient qu'à moitié chacun, et qui donnerait à `analyse` le droit de marquer.
