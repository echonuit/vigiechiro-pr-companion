# ADR 0015 — Cartes SD de recette : specs déclaratives + générateur déterministe

- **Statut** : Accepté
- **Chantier** : P5 — générateur de cartes SD de recette (#1749, #1758, #1767, #1769 ; contribue à #1363)
- **Vérification** : certaine — `GenerationCartesSDCliquetTest#chaque_spec_produit_la_pathologie_attendue`

## Contexte

La recette d'acceptation a besoin de **cartes SD** matérialisées sur disque (arbres `LogPR<serie>.txt` + `PaRecPR<serie>_THLog.csv` + `bruts/*.wav`) : l'opérateur glisse le dossier dans l'assistant d'import. Faites à la main, elles pesaient plusieurs centaines de méga-octets (~530 Mo), donc **ni versionnables** (binaire lourd, dépôt public et forké) **ni rejouables** (rien ne garantissait de les reconstruire à l'identique). Or la recette est désormais versionnée in-repo et vise le **rejeu déterministe headless**.

## Décision

Chaque carte est décrite par une **spec YAML** de quelques kilo-octets sous `recette/fixtures/spec/`, matérialisée par un **générateur déterministe**. La spec est la source de vérité ; l'arbre SD n'en est qu'un artefact reconstructible **octet pour octet** (aucune date d'horloge, aucun octet aléatoire ; WAV RIFF minimaux mais valides à fréquence divisible par 10 - règle R10 -, journal au format attendu par `AnalyseurLogPR`, entrées ZIP triées à horodatage figé).

Trois choix structurants :

- **Portée test.** Le générateur vit en `src/test/…/recette/` ; l'application distribuée n'embarque pas la fabrique de fixtures. La matérialisation manuelle passe par un goal de confort `exec:java@generer-sd` (non lié à une phase).
- **Contrat co-localisé, vérifié contre le code réel.** Chaque spec porte un bloc `attendu` (mélange, incohérence, nuits, état de nommage, rejets…). Un **garde-fou** (esprit cliquet) génère la carte puis confronte ce contrat à l'**inspection** et à l'**import réels** (`InspecteurDossier`, `ServiceImport`, `ExtracteurZip`), pas à une liste centrale tenue à la main.
- **Premier rejeu déterministe.** Un test **CLI-golden** enchaîne générateur → commande `vigiechiro importer` réelle → golden ApprovalTests, amorçant le rejeu headless du fond fonctionnel.

## Conséquences

- Les fixtures deviennent **versionnables** (quelques Ko) et **reconstructibles à l'identique** ; ~530 Mo de binaires sortent du périmètre.
- Les détecteurs d'import sont **amarrés** : le garde-fou rougit si le générateur dérive **ou** si un détecteur change de comportement.
- La recette gagne un **rejeu déterministe** (CLI-golden), socle des rejeux plus larges (qualifier, déposer…).
- L'application distribuée reste **exempte d'outillage QA** ; le coût est une dépendance de test (SnakeYAML) et un goal exec de confort.

## Alternatives écartées

- **Committer les binaires.** Dépôt lourd, public et forké ; et rien ne garantit la reproductibilité - l'inverse du besoin.
- **Générateur en sous-commande CLI** (`vigiechiro recette generer-sd`). Discoverable, mais embarque un outil de QA dans le binaire distribué ; les tests fabriquent déjà WAV/ZIP, la portée test suffit.
- **Garde-fou sur une liste d'attendus centralisée.** Une table nom→signal tenue à la main dérive en silence ; le contrat co-localisé confronté au code réel ne le peut pas.
