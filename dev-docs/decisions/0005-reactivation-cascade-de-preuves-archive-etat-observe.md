# ADR 0005 — La réactivation se vérifie par une cascade de preuves ; « archivé » est un état observé, pas un statut

- **Statut** : Accepté — rétroactif
- **Chantier** : EPIC #1297 (passage archivé) ; cascade #1309, empreinte #1299
- **Vérification** : certaine — `DecisionsRespecteesTest#archive_n_est_pas_un_statut_de_workflow`

## Contexte

Un passage dont l'audio a quitté le disque (place libérée, dépôt par ZIP - cf. [ADR 0006](0006-depot-zip-par-defaut-perte-audio-serveur-assumee.md)) n'est pas un passage **cassé**. On veut pouvoir le **réactiver** en réimportant les fichiers d'origine, sans jamais rebrancher sur le **mauvais** audio (un homonyme redécoupé produirait des observations scientifiquement fausses **et silencieuses**). Exiger une empreinte cryptographique réglerait l'identité, mais **condamnerait** tous les passages déjà archivés : ils n'en ont pas, et leurs fichiers sont partis.

## Décision

1. **« Archivé » est un état *observé*, pas un statut de workflow.** Aucune valeur ajoutée à `StatutWorkflow` (progression monotone, `DEPOSE` terminal). La disponibilité de l'audio est un **fait sur le système de fichiers** : elle change à tout moment (disque rebranché, sauvegarde restaurée) et peut être **partielle**. Même arbitrage que `EtatTraitement` (état serveur), délibérément distinct de `StatutWorkflow`.
2. **La réactivation se *vérifie*, mais pas forcément par un hash.** L'identité est établie par une **cascade** de preuves de force décroissante (#1309) : **empreinte** (#1299, CERTITUDE) → **structure** (nom horodaté, durée confrontée à l'en-tête, `source_offset_s`) → **acoustique** (instants et fréquences des cris des observations). Un passage **sans empreinte reste réactivable**, avec un **niveau de confiance affiché**. Exiger un hash, ce serait confondre une stratégie avec le besoin.

## Conséquences

- L'historique d'un passage (observations, vérifications) reste **consultable** même sans son audio ; seule l'**écoute** disparaît.
- « Supprimé exprès n'est pas cassé » : l'audit **informe** (INFO `AUDIO_ARCHIVE`) au lieu de crier `ERREUR DISQUE_MANQUANT` - généralisation du précédent `originauxPurges()`.
- La cascade est un **contrat extensible** : de nouveaux niveaux de preuve s'y insèrent sans renverser le principe. Elle a précisément permis de réactiver un passage **reconstruit** (aucune empreinte du tout) via la seule preuve structurelle - voir [ADR 0001](0001-reactivation-passage-reconstruit-identite-structurelle.md).

## Alternatives écartées

- **Une 7ᵉ valeur `ARCHIVE` dans `StatutWorkflow`.** Fige dans le workflow un fait qui vit sur le disque et qui peut être partiel : faux et rigide.
- **Exiger une empreinte pour réactiver.** Condamne tout le parc déjà archivé (pas d'empreinte, fichiers partis) alors que la base **regorge** d'autres preuves d'identité.
