---
type: adr
title: "Un avis de relecteur se range à côté du nôtre, il ne le remplace pas"
status: stable
article: A17
chantier: "#4517, chantier #4511 (mise en service d'OpenSpec), pour l'EPIC produit #3848"
decided_at: 2026-08-27
verification: humaine
verification_note: "le lot 0 rend un document, pas du code : aucun garde ne tient encore cette décision. Les scénarios qui la vérifieront sont écrits dans la delta spec du changement `emporter-une-nuit`, et les gardes arrivent avec les lots qui la réalisent (#4624 à #4628)"
verified:
  - by: human:nedseb
    at: 2026-08-27
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-27
---

# Un avis de relecteur se range à côté du nôtre, il ne le remplace pas

## Contexte

Une nuit commencée sur un poste ne peut pas être reprise sur un autre. Le besoin et ses deux
scénarios de terrain sont dans l'EPIC #3848 ; ce lot en instruit le périmètre.

Trois faits mesurés cadrent la décision, et aucun n'était dans l'énoncé.

**Le domaine n'a pas de place pour deux avis.** Une séquence porte un `selection_sequence.verdict`,
au singulier. Aucune fonction du dépôt ne combine deux jugements sur la même séquence.

**Le verdict d'un passage est consommé partout.** Relevé : **vingt-trois classes** le lisent, du
tableau multisite et ses filtres au solde de saison et à quatre commandes de la ligne de commande.

**L'avis d'expert ne se saisit pas localement.** L'[ADR 0020] et V26 posent que `taxon_validator` est
« toujours un REFLET du serveur, jamais une saisie locale ».

## Décision

**Le relecteur juge sur son exemplaire, et son avis revient signé, rangé à côté du nôtre.** Ni fusion,
ni écrasement, ni dérivation d'un troisième verdict.

`selection_sequence` gagne deux colonnes additives, `verdict_relecteur` et `relecteur_pseudo`.

**Le dépôt a déjà tranché ce problème une fois, dans le même sens.** V26 devait loger l'avis d'un
expert du MNHN sur une détection déjà jugée par Tadarida puis corrigée par l'observateur. Elle n'a
pas dupliqué l'observation : elle a ajouté des colonnes à côté. Trois avis sur la même ligne. Le
verdict d'un relecteur est le même motif, et le résoudre autrement créerait deux façons de dire
« quelqu'un d'autre a jugé ceci ».

### Pourquoi pas la fusion

Une validation naturaliste **s'attribue**. Deux avis divergents sur une séquence ne sont pas une
anomalie à réduire : ce sont deux jugements d'experts, et les écraser l'un par l'autre détruirait
une information que le domaine tient pour la donnée elle-même. C'est l'article A17 appliqué à un
jugement : ne rien effacer.

Le dépôt tient déjà ce raisonnement ailleurs. V26 a logé l'avis du validateur du MNHN à côté de
celui de l'observateur plutôt que de l'écraser, et n'a prévu **aucune** réconciliation entre les
deux : ils coexistent, chacun attribué à son auteur.

### Pourquoi pas la voie générale

Retirer le `UNIQUE` sur `listening_selection.passage_id` porterait N relecteurs au lieu d'un. Écarté
sur la mesure : les vingt-trois classes ci-dessus consomment « le verdict du passage » comme une
notion à valeur unique. La faire passer à N déborde très largement ce chantier.

## Conséquences

- **Deux colonnes portent un relecteur, pas N**, exactement comme V26 porte un validateur. Une nuit
  se confie à quelqu'un, pas à un comité. Un second prêt écraserait le premier : l'écran doit le dire
  **avant** d'importer.
- **L'avis du relecteur s'affiche, il ne vote pas.** `AgregationVerdict` reste inchangé, et le verdict
  du passage continue de se dériver des seuls verdicts de l'expéditeur. Décider qu'un relecteur y
  pèse est une décision de domaine, et c'est une décision de **ne pas faire** : sans cette ligne, le
  prochain lecteur la croirait oubliée.
- **L'identité s'appose à l'ouverture du paquet, pas au moment du jugement.** Mesuré :
  `StockageConnexion.profil()` filtre sur la péremption du jeton à quatorze jours et rend une
  identité vide au-delà, alors qu'elle est écrite dans `connexion.json`. Un relecteur qui juge sans
  s'être reconnecté depuis deux semaines produirait des verdicts anonymes.
- **Le paquet emporte toutes les séquences transformées, pas les bruts.** C'est la régénération de la
  sélection d'écoute qui décide du contenu, et non le couple transformées contre brutes que l'EPIC
  opposait : sans elle, le relecteur ne pourrait que juger l'échantillon tiré par l'expéditeur.
- **Les identifiants de plateforme ne voyagent pas.** Le relecteur juge, l'expéditeur publie.
  L'hésitation de l'EPIC sur le lien vers un compte tiers tombe sans arbitrage.

Le détail des cinq décisions, leurs alternatives rejetées et leurs mesures vivent dans
`openspec/changes/emporter-une-nuit/design.md`. Cette note-là part à l'archivage ; celle-ci reste.
