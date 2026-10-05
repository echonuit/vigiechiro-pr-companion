## Context

`EtapesDepot` calcule le fil d'après le seul statut du passage ; il est pur et ne sait rien du dépôt ni du
traitement. Le titre et le bouton de la dernière carte suivent `DepotViewModel.participationLiee`, et le bouton
se désactive pour de bon quand `TraitementViewModel` dit l'analyse demandée ou la relance bloquée.

## Decisions

**Un type pour le dernier geste.** `DernierGesteDuDepot` a trois états : marquer déposé, lancer la participation,
participation lancée. Il porte le nom du bouton, une fois, et sait corriger un fil calculé (`appliquerAuFil`).

**Le fil lit la liaison du bouton, à l'affichage.** `EtapeDeposerUI.dernierGeste` compose les deux faits, et
`LotController`, seul à connaître les trois ViewModels, applique le geste au fil quand il le dessine. Écarté :
pousser le geste dans `LotViewModel`, qui franchissait alors le plafond `GodClass` (ADR 4682).

**L'état ne bouge que sur une nuit déjà sur la plateforme.** Un dépôt partiel offre déjà son bouton de lancement,
mais son étape courante reste le téléversement : c'est lui qui est à finir.

## Risks / Trade-offs

L'état du traitement n'est connu que par un relevé, ou par le dernier relevé gardé en local. Sur une nuit dont
l'analyse a été lancée ailleurs, le fil dit « à lancer » jusqu'au prochain relevé, exactement comme le bouton.
