# ADR 0023 — Rendre compte se fait au bandeau ; le modal est réservé à l'irréversible

- **Statut** : Accepté — 2026-07-18, **amendé** par [ADR 0031](0031-un-retour-n-est-pas-un-compte-rendu.md) (sur le vocabulaire ; la décision reste entière)
- **Chantier** : EPIC #1870 (#1886)
- **Vérification** : humaine — le choix bandeau/modal selon l'irréversibilité est une règle d'UX, non observable dans le code par un motif

## Contexte

« Rendre compte à l'utilisateur » se dit aujourd'hui de **trois façons** selon l'écran où l'on se trouve.

| Véhicule | Écrans | Ce qu'il offre |
|---|---|---|
| **Bandeau** (`RetourOperation` + `BandeauRetour`, `commun`) | Sons & validation, Inventaire | texte + sévérité + croix de fermeture |
| **`Notificateur` modal** (`Alert.showAndWait()`) | Passage, Qualification, Détail site, ~7 classes d'action | bloquant |
| **Libellé ad hoc** | Diagnostic, Lot (×2), Carte & passages, Mes sites, Passage, Qualification | `String` nu, ni sévérité ni fermeture |

Le bandeau, remonté dans `commun` par l'EPIC #1792 ([ADR 0021](0021-double-clic-miroir-qui-rend-compte.md)), n'est utilisé que par **deux écrans sur onze**. Les sept libellés ad hoc suivent pourtant exactement la même forme (une propriété `String` liée à `text` + `visible` + `managed`) : la divergence est historique, pas motivée.

Deux constats interdisent de tout unifier mécaniquement.

**Un travail en cours n'est pas un résultat.** Trois messages annonçaient une opération en cours par le canal de retour. Les mettre dans un bandeau **fermable** est un contresens : fermer n'interrompt rien, et le message est écrasé à la fin de l'opération. L'application sait déjà dire « ça travaille » (barres de progression, `IndicateurOccupation`, barre de statut).

**Le modal n'est pas homogène.** L'inventaire de ses appels montre deux natures mêlées : des **bilans d'action destructive** (« 4,2 Go libéré(s) » après une purge confirmée) et des **refus** (« Purge impossible », « Archivage impossible », « Action impossible »).

## Décision

**Le bandeau est le véhicule par défaut** de tout compte rendu d'opération. Les libellés ad hoc le rejoignent, écran par écran (lots #1887 à #1890).

**Le modal est réservé à l'irréversible** : reste bloquant ce qui concerne une action **irréversible**, aussi bien son **bilan** que son **échec**.

- son bilan, parce que l'utilisateur vient de détruire quelque chose et doit le lire (« 4,2 Go libéré(s) ») ;
- son échec, parce qu'il croit avoir détruit et qu'il faut le détromper (« Purge impossible »).

Un compte rendu d'opération **réversible ou anodine** ne bloque pas, quelle que soit sa sévérité.

**Le retour ne porte que des résultats.** L'annonce d'un travail en cours passe par un **état** que l'écran rend à sa façon (barre de progression, barre de statut), jamais par la propriété de retour. Corollaire : **démarrer une opération efface le retour précédent**, faute de quoi le bilan de l'opération d'avant se lit comme celui de celle qui travaille.

## Conséquences

- `RetourOperation` garde ses trois sévérités (succès / information / erreur) : aucune quatrième « en cours » n'est nécessaire, puisque le concept sort du canal.
- Le lancement du traitement (étape ④ du dépôt) a reçu un état **distinct** du téléversement (`lancementEnCoursProperty`) : les deux allumaient `enCours`, mais annoncer « n/N déposées » pendant un simple appel de lancement serait faux. Le garde-fou de #1543 (« le POST ne part plus sans retour visible ») survit, désormais porté par cet état.
- Chaque lot de migration devra **classer ses appels au `Notificateur`** selon la règle ci-dessus : c'est un jugement par appel, pas une transformation mécanique.
- Chaque écran migré demande une **revue visuelle** : le bandeau occupe de la place et déplace le contenu.
- Purement présentationnel : aucune capacité métier, donc sans objet côté CLI ([ADR 0014](0014-parite-cli-ihm.md)).

## Alternatives écartées

- **Une quatrième sévérité « en cours »**, rendue sans croix. Migration mécanique et sans perte, mais l'application garderait **deux** façons de dire « ça travaille », et un bandeau qui ne se ferme pas est une exception à retenir.
- **Tout passer au bandeau, modal compris.** Un seul mécanisme, aucune exception - mais un bilan de purge pourrait être manqué, et c'est un changement de comportement sur des actions irréversibles.
- **Ne pas toucher au modal** et se contenter des libellés ad hoc. Deux véhicules au lieu de trois : un progrès, mais la question « pourquoi celui-ci bloque-t-il et pas celui-là ? » resterait sans réponse.
- **Laisser les messages « en cours » où ils étaient.** L'écran du Lot aurait alors gardé un libellé **et** un bandeau, soit exactement la divergence que le chantier vient corriger.
