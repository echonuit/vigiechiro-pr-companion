## Context

`PremierPoint` porte la case, sa condition et son compte rendu, et ne lisait du formulaire que le texte de la
position. « Situer » lit le carroyage embarqué par `PropositionCarre`, dépose le numéro du carré quand un seul
l'emporte, et dit ce qu'il remplace. La modale de point, elle, confronte une position au carré du site par la
plateforme (`ControleCarreStoc`), et dit la divergence par `VerdictCarre.Diverge`.

## Decisions

**Le carroyage embarqué, pas la plateforme.** La modale de site marche hors connexion (décision D0 de #4577), et
l'avertissement annonce exactement ce que « Situer » remplacerait. Écarté : `ControleCarreStoc`, qui ajouterait
une requête réseau à une case, et rendrait le verdict muet hors connexion.

**La phrase de la modale de point.** `VerdictCarre.Diverge` porte déjà « Ce point tombe dans le carré X de la
grille STOC, alors que ce site déclare le carré Y ». Elle est reprise par le même type, pour qu'une divergence
se lise de la même façon aux deux endroits.

**Un avertissement, pas un refus.** Comme dans la modale de point : l'observateur peut tenir à son numéro.

**Seulement case cochée.** Décochée, aucun point ne sera créé : il n'y a rien à confronter, et « Situer » dit
déjà ce qu'il remplace à qui le clique.

## Risks / Trade-offs

Les deux modales ne lisent pas le même instrument. Sur une position proche d'une frontière, la plateforme peut
trancher là où le carroyage embarqué se tait (ADR 4610). C'est assumé : la modale de site ne choisit pas.
