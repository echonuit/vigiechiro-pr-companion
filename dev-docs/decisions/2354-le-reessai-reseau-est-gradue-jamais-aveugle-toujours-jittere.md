# ADR 2354 — Le réessai réseau est gradué par profil, jamais aveugle, toujours jitteré

- **Statut** : Accepté — 2026-07-26
- **Chantier** : #2354 (lot 1 de l'EPIC #2350)
- **Vérification** : certaine — `PolitiqueReessaiTest#refus_definitif_ne_reessaie_pas`

## Contexte

Le dépôt d'une nuit se fait souvent depuis le terrain, sur un partage de connexion mobile : une coupure d'une seconde y est normale, pas exceptionnelle. Or `commun/api/TransportVigieChiro` n'avait **aucun réessai**. Un paquet perdu pendant un `PUT` S3 de plusieurs dizaines de mégaoctets faisait échouer l'unité de dépôt.

Le transport persiste pourtant déjà un plan reprenable (`depot_plan`, `depot_unite`, bouton « Retenter les échecs ») : c'est un **filet** qui **répare** la panne. Mais réparer demande un geste humain, pour un incident qui n'en méritait aucun. Il manquait le **pare-chocs** qui l'**évite**.

## Décision

Une politique de réessai (`PolitiqueReessai`) enveloppe les émissions du transport. Trois règles la gouvernent.

**1. Jamais aveugle.** Seul ce que le type scellé [`ReponseApi`] déclare rejouable, via `estReessayable()`, est rejoué : un incident réseau (`Injoignable`) ou un serveur temporairement indisponible (`Refuse` `429`/`5xx`). Un `4xx` (URL signée expirée, corps refusé, jeton mort) ne deviendra jamais valide en réessayant : on renonce immédiatement. Le sealed le dit **variante par variante, en compilation** - pas un `switch` disséminé chez les appelants qui oublierait un cas.

**2. Graduée par profil.** L'appelant choisit selon ce qu'il fait :

| Profil | Quand | Comportement |
|---|---|---|
| `PREMIER_PLAN` | quelqu'un attend devant l'écran | plusieurs tentatives, backoff exponentiel plafonné |
| `ARRIERE_PLAN` | relevé d'état, tâche périodique | une tentative supplémentaire au plus |

La distinction n'est pas cosmétique : un relevé d'état qui insiste transforme une lenteur serveur en rafale de requêtes et **amplifie** l'incident qu'il prétend absorber.

**3. Toujours jitterée, `Retry-After` faisant autorité.** La temporisation porte un aléa (jitter égal : moitié fixe pour que le délai croisse, moitié aléatoire) : sans lui, plusieurs unités qui échouent ensemble retentent ensemble et la rafale se reforme. Et quand le serveur envoie `Retry-After`, c'est **lui** qui fait autorité, pas notre backoff.

### Ce que la politique NE décide pas : l'idempotence

`estReessayable()` dit qu'une réponse **autorise techniquement** un nouvel essai. Il ne dit **pas** que l'opération est **sûre** à rejouer. Un `POST` de création dont l'envoi expire (`Injoignable`) a pu aboutir côté serveur : le rejouer créerait un doublon. La **sûreté** au rejeu est donc arbitrée **appel par appel** par l'appelant, qui n'enveloppe de réessai que ce qui est idempotent (GET, `PUT` S3 - même clé, même objet -, `PATCH` avec `If-Match`) ou rendu tel. La politique fournit le mécanisme ; le périmètre reste une décision d'appelant, documentée là où elle se prend.

## Conséquences

- Une coupure momentanée est absorbée sans geste humain ni consommation d'une unité de dépôt. Le plan persisté reste le filet pour les pannes que le pare-chocs ne rattrape pas.
- Le mécanisme s'applique à **tout appel réseau ultérieur** : le profil et la sûreté au rejeu se déclarent à l'appel, jamais par défaut.
- Endormissement et aléa sont **injectés** (`Temporisateur`, source d'aléa) : les tests de la politique sont déterministes et instantanés, sans vraie attente ni réseau.
- Pendant un réessai, l'utilisateur voit une **mention discrète** (« nouvelle tentative dans N s »), portée par le hook `Suivi` : ni silence trompeur, ni bandeau d'erreur pour un incident absorbé (décision #2350). Le câblage de ce hook vers le canal de statut vient au lot de câblage.

## Alternatives écartées

- **Tout rejouer, sans classer.** Rejouerait un `4xx` (jamais valide au second essai) et, pire, un `POST` non idempotent (doublon serveur). Le tri par `ReponseApi` **et** l'arbitrage d'idempotence à l'appel l'excluent.
- **Un réessai uniforme.** Appliqué à un relevé d'état de fond, il transforme une lenteur serveur en rafale. D'où les deux profils.
- **Pas de jitter.** Des reprises synchronisées reforment la rafale à chaque cycle. Le jitter les désynchronise.
- **S'en remettre au seul plan persisté.** Il répare, il n'évite pas : il demande un geste humain là où une seconde tentative automatique aurait suffi.
