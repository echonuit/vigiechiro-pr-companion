# ADR 0017 — L'origine d'un point d'écoute (rapatrié vs ajouté à la main) est un état porté, pas déduit

- **Statut** : Accepté — 2026-07-17
- **Chantier** : EPIC #1662 (#1738)
- **Vérification** : humaine — que l'origine d'un point soit portée (drapeau) et non déduite est un comportement de vue ; le champ seul ne prouve pas la règle

## Contexte

La synchro « mes sites » rapatrie **tous** les points d'un carré depuis VigieChiro - pour un carré Point Fixe, la grille STOC entière, des dizaines de points. L'observateur n'en utilise qu'un ou deux. Les vues site (fiche et liste « Mes Sites ») les affichaient **tous**, noyant le point qui sert sous des dizaines de cartes et de codes inutiles.

On veut **masquer les points rapatriés non utilisés** sans jamais masquer un point qu'on a **ajouté à la main** : on veut voir ce qu'on crée, même avant d'y importer une nuit. Or rien ne distinguait les deux - rapatriés et manuels passaient par le même `ajouterPoint`, sans marqueur. Filtrer sur le seul **usage** (« a des passages ») masquait aussi les points manuels non encore utilisés : une régression, et huit tests qui encodaient l'invariant « un point qu'on crée reste visible ».

## Décision

Un point porte un drapeau **`synchronise`** : `true` s'il a été **rapatrié** de la plateforme (`RapprochementSites` → `ServiceSites.ajouterPointSynchronise`), `false` s'il a été **ajouté à la main** (`ajouterPoint`). Les vues site masquent par défaut les points **synchronisés ET non utilisés** ; un point **utilisé** ou **manuel** reste toujours affiché. Le rapatriement reste **complet** (tous les points existent en base, utiles pour importer une nuit sur un point neuf) : le filtre est une **projection de la vue**, pas une amputation du modèle.

## Conséquences

- Le décombrage est **sémantiquement juste** : on masque le bruit (la grille rapatriée non utilisée), jamais ce que l'utilisateur gère.
- Migration V29 : reprise des points **déjà en base** - ceux d'un site relié à VigieChiro sont réputés rapatriés (best-effort ; la grille est l'écrasante majorité), les nouveaux points manuels naissant à `false`.
- Le même drapeau sert les **deux** vues (fiche site : révélation à la demande ; liste « Mes Sites » : résumé « + N rapatriés ») : une seule règle, deux surfaces.
- Purement **présentationnel** : aucune capacité métier, donc sans objet côté CLI.

## Alternatives écartées

- **Filtrer sur l'usage seul** (« a des passages »). Masquait les points manuels non utilisés : régression d'un invariant réel, révélée par huit tests de vue.
- **Un seuil** (« ne décombrer qu'au-delà de N points »). Heuristique à nombre magique, au comportement qui bascule arbitrairement au N-ième point.
- **Ne rapatrier que les points utilisés.** Empêchait d'importer une nuit sur un point de la grille pas encore utilisé, et ne s'appliquait pas aux dizaines de points **déjà** en base.
