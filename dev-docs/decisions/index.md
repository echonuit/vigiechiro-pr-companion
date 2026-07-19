# Décisions d'architecture (ADR)

Ce journal consigne les **décisions structurantes** du projet : les choix qui engagent l'architecture ou le domaine sur la durée, et surtout **pourquoi** ils ont été faits. Une décision oubliée se re-débat ; écrite, elle se relit.

## Ce qu'est (et n'est pas) une ADR

Une **ADR** (Architecture Decision Record) décrit **une** décision : son contexte, ce qui a été tranché, ses conséquences, et les pistes écartées. Elle est **immuable** une fois acceptée : on ne la réécrit pas, on en écrit une nouvelle qui la remplace (statut « Remplacée par ADR NNNN »).

Une ADR n'est **pas** :

- un compte rendu de chantier (ça, c'est le **bilan**, déposé dans le corps de l'EPIC à sa clôture) ;
- une description de l'implémentation (ça, c'est le code et sa Javadoc, ou [Patterns et principes](../patterns.md)) ;
- une note de rappel opérationnelle (« attention au piège X ») : ces notes vivent ailleurs.

On écrit une ADR quand un chantier prend une décision qu'un développeur futur **pourrait raisonnablement remettre en cause** faute d'en connaître les raisons : « pourquoi ne pas simplement comparer l'empreinte ? », « pourquoi une fenêtre courte plutôt qu'une moyenne ? ».

## Quand en écrire une

Au fil d'un chantier, à la **passe 3 (doc développeur)** de sa [clôture](../cycle-de-chantier.md) : chaque décision structurante prise pendant le chantier donne une ADR. La **passe 10 (bilan)** s'y réfère plutôt que de dupliquer le raisonnement.

## Format

Copier le squelette suivant dans `NNNN-titre-court.md` (numéro à 4 chiffres, incrémental) :

```markdown
# ADR NNNN — La décision, formulée comme une affirmation

- **Statut** : Accepté — AAAA-MM-JJ
- **Chantier** : EPIC #NNNN (titre court)

## Contexte
Les forces en présence, le problème, ce qui contraint.

## Décision
Ce qui est tranché, à l'impératif.

## Conséquences
Ce que cela implique, en bien comme en moins bien.

## Alternatives écartées
Les autres pistes, et pourquoi elles ont perdu.
```

## Journal

Les premières entrées sont **rétroactives** : elles consignent, à partir des bilans de chantier, des décisions structurantes prises avant l'ouverture du journal.

| # | Décision | Chantier |
|---|---|---|
| [0001](0001-reactivation-passage-reconstruit-identite-structurelle.md) | Identité d'un passage reconstruit : régénération structurelle, l'acoustique en indice | #1653 |
| [0002](0002-detection-acoustique-energie-de-pointe.md) | Détection acoustique par énergie de pointe, pas par moyenne globale | #1653 |
| [0003](0003-feature-plugin-desactivable-ports-optionnels.md) | Une feature est un plugin désactivable ; dépendances entre features = ports optionnels | #923, #1057 |
| [0004](0004-cross-feature-sans-cycle-ports-commun.md) | Pas de cycle entre features : les ponts passent par un port dans `commun` | ArchUnit |
| [0005](0005-reactivation-cascade-de-preuves-archive-etat-observe.md) | Réactivation par cascade de preuves ; « archivé » est un état observé | #1297 |
| [0006](0006-depot-zip-par-defaut-perte-audio-serveur-assumee.md) | Le dépôt par ZIP est le mode par défaut ; la perte de l'audio serveur est assumée | #984, #1297 |
| [0007](0007-retours-http-type-scelle-reponse-api.md) | Les retours de l'API sont un type scellé `ReponseApi` | #1284 |
| [0008](0008-aucun-echec-silencieux-severite-a-l-emission.md) | Aucun échec silencieux ; la sévérité de journalisation se décide à l'émission | #1523 |
| [0009](0009-la-nuit-est-l-unite-bornee-a-midi.md) | La nuit (soir → matin, bornée à midi) est l'unité de traitement | #664, #1696 |
| [0010](0010-dialogues-bloquants-sont-des-ports.md) | Les dialogues bloquants (confirmation, compte rendu) sont des ports injectables | #789, #1405 |
| [0011](0011-transformation-audio-pilotee-par-le-log.md) | La transformation audio est pilotée par le log (fréquence réelle), pas par l'en-tête | import Tadarida |
| [0012](0012-audit-coherence-tout-ecart-visible-etat-normal-silencieux.md) | L'audit rend tout écart visible, mais un état normal ne crie pas | #1154 |
| [0013](0013-ancrage-passage-relie-a-sa-participation.md) | Un passage local est ancré à sa participation serveur (lien explicite) | #720 |
| [0014](0014-parite-cli-ihm.md) | Toute capacité métier est offerte aussi en CLI (parité CLI ↔ IHM) | #619, #1304 |
| [0015](0015-generateur-deterministe-cartes-sd-recette.md) | Cartes SD de recette : specs déclaratives + générateur déterministe | #1749, #1769 |
| [0016](0016-synchro-rapatrie-des-squelettes-hydrates-a-la-demande.md) | La synchro rapatrie les nuits en squelettes, hydratés à la demande | EPIC #1662 |
| [0017](0017-origine-d-un-point-etat-porte-pas-deduit.md) | L'origine d'un point (rapatrié vs manuel) est un état porté, pas déduit | #1738 |
| [0018](0018-la-synchro-rapatrie-l-identite-de-la-nuit.md) | La synchro rapatrie aussi l'identité de la nuit (amende 0016) | #1814 |
| [0019](0019-ancrage-acquis-quand-il-sert.md) | L'ancrage s'acquiert quand il sert, pas à un moment décrété (amende 0016) | #1838 |
| [0020](0020-ecrire-sur-la-plateforme-ne-rien-inventer-ni-effacer.md) | Écrire sur la plateforme : ne rien inventer, ne rien effacer, parler la langue du lecteur | #1828, #1844 |
| [0021](0021-double-clic-miroir-qui-rend-compte.md) | Le double-clic est le miroir de l'action principale, et il rend compte quand il n'aboutit pas | EPIC #1792 |
| [0022](0022-le-verbe-dit-le-sens-de-l-echange.md) | Le verbe d'un geste dit le sens réel de l'échange | #1855, #1866 |
| [0023](0023-rendre-compte-bandeau-par-defaut-modal-si-irreversible.md) | Rendre compte se fait au bandeau ; le modal est réservé à l'irréversible | EPIC #1870 |
| [0024](0024-les-heures-d-une-nuit-viennent-de-ses-preuves.md) | Les heures d'une nuit viennent de ses preuves ; de l'utilisateur seulement à défaut | #1860, #1878, #1892 |
| [0025](0025-une-capture-passe-par-le-code-de-production.md) | Une capture passe par le code de production, elle ne le reconstruit pas | #1468, #1865 |
| [0026](0026-le-nommage-des-tranches-est-une-etape-du-pipeline.md) | Le nommage des tranches est une étape du pipeline, pas un détail de la découpe | EPIC #1944 |
| [0027](0027-une-attente-porte-toujours-un-nom.md) | Une attente porte toujours un nom, et c'est l'étape qui va attendre qui le pose | #1931, #1951, #1959 |
| [0028](0028-un-etat-n-est-pas-un-compte-rendu.md) | Un état n'est pas un compte rendu, et ils ne partagent pas de canal | EPIC #1870 |
| [0031](0031-un-retour-n-est-pas-un-compte-rendu.md) | Un retour d'opération n'est pas un compte rendu : le mot « compte rendu » se libère pour l'extensible | EPIC #1990 |
| [0035](0035-un-pictogramme-est-une-icone-pas-un-caractere.md) | Un pictogramme d'IHM est une icône ; un caractère dans une phrase reste un caractère | #1933, #700 |
