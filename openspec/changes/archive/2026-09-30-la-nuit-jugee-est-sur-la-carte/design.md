## Context

Voir `proposal.md`. L'état du code, relevé sur `main` à `21bb03eef` :

- `CompteRenduDInspection.identiteNuit` rend **une** identité, `(série, journal.dateDebut())`, repli
  sur les noms de WAV sans journal. `dateDebut` est la **première ligne horodatée** du fichier
  (`AnalyseurLogPR`, l. 100).
- Trois consommateurs la lisent : `passagesDeLaNuit` (avertissement d'en-tête et, via
  `rafraichirNuitExistante`, la question de confirmation de `ImportationController.importer`), et
  `ControleNumeroPassage` (supplier `inspection::identiteNuit`).
- La table des nuits (`peuplerNuits`) est juste : elle part de `partitionNuits()`, tirée des noms des
  WAV, et interroge `nuitDejaImportee` nuit par nuit. La série y vient déjà du journal, sinon des noms.
- `AnalyseCoherence.dateIncoherente` compare les dates des WAV à `[dateDebut, dateDebut + 1]`, et
  exempte les cartes à plusieurs nuits. `RapportInspection.coherence()` a sous la main
  `cyclesJournal`, un cycle d'acquisition par nuit racontée (`CycleAcquisition.dateNuit()`), déjà
  calculé pour la complétude (#4990).
- `ServiceImport` a déjà fait le même choix pour dater un passage : la partition des WAV fait foi, le
  journal n'est qu'un repli (commentaire l. 447-454).

## Goals / Non-Goals

**Goals :**

- Une seule source pour « les nuits de la carte » : la table des nuits.
- La confirmation et le contrôle du n° de passage suivent les cases cochées.
- Un contrôle de cohérence qui juge le journal sur les nuits qu'il raconte.

**Non-Goals :**

- Changer la table des nuits ou la complétude (#5504).
- Changer ce que l'import enregistre : `ServiceImport` date déjà d'après les WAV, et l'import par
  référence construit son journal depuis les noms (#5630, fermé, prémisse fausse).

## Decisions

### 1. L'identité devient une liste, tirée de la table des nuits

`identiteNuit` (une) cède la place aux identités des **nuits de la table** : `(série de la carte,
date de la nuit)` pour chaque `NuitVM`, la série venant de `serieDeLaCarte` comme aujourd'hui. Les
consommateurs reçoivent les nuits **cochées** : l'avertissement d'inspection les juge toutes (toutes
sont cochées par défaut), la confirmation et le contrôle du n° relisent les cases au moment où ils
jugent.

Options écartées :

- **Garder une identité, mais prendre la première nuit de la table.** Juste sur une carte mono-nuit,
  faux dès qu'une nuit présente autre que la première a déjà été importée.
- **Filtrer les résultats de `dateDebut` par la table** (juger la date du journal seulement si elle
  est dans la table). Cela tait le défaut sans répondre à la question posée : il resterait une seule
  nuit jugée sur une carte qui en porte trois.

### 2. Le texte multi-nuits nomme chaque nuit

Mono-nuit : le texte actuel ne change pas (« Cette nuit a déjà été importée : l'importer créera un
nouveau passage. »). Plusieurs nuits concernées : le fait dit combien, et chaque détail porte la date
de la nuit devant le passage existant, dans le format de date déjà employé par
`AvertissementsInspection.dates`. La rédaction reste à un seul endroit (`libelle`, #2050).

### 3. La cohérence lit les nuits racontées dans les cycles

`AnalyseCoherence` reçoit les nuits racontées par le journal, celles de `cyclesJournal`. La date est
incohérente si ces nuits existent et qu'**aucune** ne correspond à une nuit des WAV (même tolérance
soir → matin qu'aujourd'hui). Sans cycle lisible, repli sur `dateDebut`, comportement actuel.
L'exemption multi-nuits devient inutile, mais reste tant qu'elle ne contredit rien : la retirer est
une harmonisation, pas ce lot.

Option écartée : **tirer les nuits des configurations** (`JournalParse.configurations`). Une
configuration décrit un réglage, pas une nuit : rien ne garantit qu'il y en ait une par nuit, et je ne
l'ai pas mesuré. Les cycles, eux, sont déjà la notion de « nuit racontée » que le dépôt emploie, un
par nuit, pour la complétude (#4990) : ne pas en inventer une seconde.

### 4. Le pourquoi durable va en ADR à la clôture

« Les nuits d'une carte sont celles de ses enregistrements ; le journal circulaire n'en dit que la
série et ce qu'il raconte », avec `ServiceImport` pour précédent.

## Risks / Trade-offs

- [Carte sans aucun WAV horodaté] → Pas de nuit dans la table, donc rien à juger : c'est déjà le cas
  de l'import, qui n'a alors rien à importer.
- [Journal sans cycle lisible] → Le contrôle de cohérence retombe sur `dateDebut`, comme aujourd'hui.
- [Le constructeur de `ControleNumeroPassage` change de signature] → Un seul site de construction
  (`ImportationViewModel`, l. 148) ; les tests le construisent par le view model.
