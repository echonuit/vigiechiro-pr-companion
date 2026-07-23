# ADR 0007 — Les retours de l'API sont un type scellé `ReponseApi`

- **Statut** : Accepté — rétroactif
- **Chantier** : EPIC #1284 (faire parler le transport HTTP)
- **Vérification** : humaine — un appel réseau qui contourne ReponseApi ne se distingue pas par motif d'un utilitaire du paquet api ; vérifié en revue

## Contexte

Le client VigieChiro peut échouer de plusieurs façons **distinctes**, qu'il faut traiter différemment : l'utilisateur n'est **pas connecté**, la plateforme est **injoignable** (timeout, réseau), ou elle **refuse** (422, 4xx/5xx avec un corps). Modéliser tout cela par un retour « nul ou liste vide » ou par des exceptions hétérogènes noyait ces cas : un `null` silencieux passait pour une absence de données, un reset guidé se lançait alors qu'il ne pourrait rien repeupler, etc.

## Décision

Tout appel réseau rend une valeur d'un **type scellé** `sealed interface ReponseApi<T>`, dont les variantes **énumèrent** les issues :

- `Succes<T>(T valeur)`
- `NonConnecte<T>()`
- `Injoignable<T>(String cause)`
- `Refuse<T>(int statut, String corps)`

Un `TransportVigieChiro` central produit ces valeurs ; les appelants les traitent par **filtrage exhaustif** (le compilateur exige de couvrir chaque variante).

## Conséquences

- Un cas d'échec **ne peut plus être oublié** : le `sealed` force à le nommer. Un « la plateforme ne répond pas » n'est plus confondu avec un « pas de données ».
- Les gestes qui **détruisent** (reset, dépôt) peuvent **refuser en amont** quand la plateforme est injoignable, au lieu d'agir sur du vide.
- Piège connu, à documenter dans les tests : un **mock non stubé** d'un appel `ReponseApi` rend `null` (et non plus une liste vide) - chaque appel qu'un parcours déclenche doit être bouchonné explicitement.
- La suite de **contrat live** (hebdomadaire, lecture seule) rejoue les vraies réponses de la plateforme et fige le contrat après toute évolution du client.

## Alternatives écartées

- **Retour `null` / liste vide.** Efface la différence entre « rien » et « échec », et se propage en pannes silencieuses.
- **Exceptions par cas.** Déportent le traitement loin de l'appel et n'obligent pas à couvrir chaque issue ; le `sealed` rend l'exhaustivité vérifiable à la compilation.
