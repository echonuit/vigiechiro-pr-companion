## Purpose

Ce qu'un poste doit avoir pour qu'un verdict local vaille quelque chose, ce que le dépôt pose
lui-même avant de juger, et ce qu'il refuse de poser en le disant. Couvre la porte locale et la
déclaration des outils dont les gardes se servent.

## ADDED Requirements

### Requirement: La porte pose les prérequis déclarés avant de juger

La porte locale SHALL poser les prérequis **déclarés** du dépôt avant de lancer le moindre garde, et
SHALL dire ce qu'elle a posé.

Un prérequis posable par une commande n'est pas un verdict à interpréter. Aujourd'hui son absence rend
un refus qui ressemble à un défaut du changement en cours, ce que `pyproject.toml` a déjà constaté
pour les modules Python (#5008).

*Vérifié par* : l'auto-test de la porte, sur un cas qui lui donne un prérequis absent et constate
qu'il est présent après. **Aucun dispositif de CI ne peut le tenir** : le runner installe tout
lui-même et ne joue jamais ce chemin. L'auto-test porte donc seul, et c'est la difficulté propre de ce
changement.

#### Scenario: un worktree neuf devient jugeable
- **WHEN** la porte est lancée dans un worktree neuf, où `.github/openspec/node_modules` est absent
- **THEN** elle pose l'installation, l'annonce, et les gardes OpenSpec rendent un verdict au lieu de refuser

#### Scenario: ce qui est déjà là n'est pas reposé
- **WHEN** la porte est lancée deux fois de suite
- **THEN** la seconde ne repose rien et le dit, plutôt que de repayer le coût

### Requirement: Un prérequis cher n'est posé que si le diff l'engage

La porte SHALL décider de poser un prérequis coûteux d'après ce que le diff touche, et non
systématiquement.

`target/pmd.xml` demande une à deux minutes, contre deux à six secondes pour les autres. L'imposer à
un lot qui ne touche que de la prose serait le contraire du service rendu. La porte sait déjà dériver
ce qu'un diff engage : c'est sa raison d'être ([ADR 5340]).

*Vérifié par* : l'auto-test de la porte, deux cas - un diff de prose n'engage pas PMD, un diff de
`.java` l'engage.

#### Scenario: un lot de prose ne paie pas PMD
- **WHEN** le diff ne touche aucun fichier `.java`
- **THEN** la porte ne lance pas PMD, et le garde qui en dépend n'est pas engagé non plus

#### Scenario: un lot de code le paie
- **WHEN** le diff touche au moins un fichier `.java`
- **THEN** la porte produit le rapport PMD avant de lancer le cliquet qui le lit

### Requirement: Un prérequis que le dépôt ne peut pas poser reste un refus lisible

La porte ne SHALL PAS tenter de poser un prérequis qui dépend d'une installation réelle, d'un
périphérique monté ou d'un accès qu'elle n'a pas. Elle SHALL rendre la ligne de refus du garde, qui
dit quoi faire.

C'est le comportement actuel, et il est juste : il se conserve tel quel plutôt que d'être remplacé.
Une porte qui tenterait tout deviendrait une porte à qui l'on ne fait plus confiance.

*Vérifié par* : l'auto-test de la porte, sur un garde qui refuse pour une cause non posable.

#### Scenario: la ligne de refus survit à la préparation
- **WHEN** un garde refuse faute d'un paquet réel ou d'une carte montée
- **THEN** la porte rend sa ligne de refus telle quelle, sans la masquer ni tenter de l'installer

### Requirement: La préparation ne fait pas passer une absence pour un succès

Quand un prérequis ne peut pas être posé - réseau absent, registre injoignable, outil qui échoue - la
porte SHALL le dire et poursuivre, et le garde concerné SHALL refuser comme aujourd'hui.

Une préparation muette qui échoue rendrait la porte **moins** sûre qu'avant : le lecteur croirait
l'environnement complet.

*Vérifié par* : l'auto-test de la porte, sur une commande de préparation qui échoue exprès.

#### Scenario: sans réseau, la porte le dit
- **WHEN** l'installation d'un prérequis échoue
- **THEN** la porte nomme la commande qui a échoué, poursuit son travail, et le garde qui en dépend refuse

### Requirement: Un outil du dépôt se déclare avec sa distribution ET sa commande

Tout outil externe que la méthode prescrit SHALL être déclaré au dépôt avec le nom sous lequel il
**s'installe** et le nom sous lequel il **s'appelle**, quand les deux diffèrent.

`graphifyy` s'installe, `graphify` s'appelle. Le dépôt prescrit aujourd'hui `graphify` dans
`AGENTS.md`, `CONTRIBUTING.md` et `dev-docs/chercher-dans-le-depot.md` sans nommer la distribution une
seule fois. Or `pip install graphify` n'existe pas, et `npm i -g graphify` installe **un autre
paquet**, publié par un tiers. Le piège est celui que `pyproject.toml` documente déjà pour
`PyYAML` / `yaml`.

*Vérifié par* : `scripts/methode/verifie-dependances-declarees.py`, qui lit déjà la table
`[tool.vigiechiro.modules]`. Son extension aux **commandes** est à écrire ; sans elle, cette exigence
n'a pas de gardien.

#### Scenario: la méthode nomme ce qui s'installe
- **WHEN** un lecteur suit la prescription d'un outil dans la méthode
- **THEN** il trouve le nom exact de la distribution, et ne peut pas installer un homonyme par erreur

### Requirement: Le dépôt propose l'enregistrement d'un outil, il ne l'impose pas

Le dépôt SHALL nommer la commande qui installe un outil prescrit, et ne SHALL PAS la lancer à la place
de son utilisateur ni l'exiger de lui.

**Mesuré le 2026-09-06**, en lançant `graphify install --project` dans un worktree propre. Elle ne
dépose pas seulement une compétence :

| ce qu'elle écrit | ce que ça fait |
|---|---|
| 10 lignes dans `CLAUDE.md` | un fichier de méthode écrit à la main, et le texte ajouté **existe déjà** dans `AGENTS.md` |
| `.claude/skills/graphify/` | fait **rougir** `synchronise-adaptateurs.py` : « orphelin : aucune source sous `.agents/skills` » |
| `.claude/CLAUDE.md` | un second fichier de méthode |
| des hooks `PreToolUse` dans `.claude/settings.json` | lancent `graphify hook-guard` avant **chaque** Bash, Grep, Read et Glob de l'agent |

Le dernier point décide. Changer ce qu'un agent exécute à chaque appel d'outil n'est pas une
déclaration de dépendance : c'est un **choix individuel**, qui appartient à qui travaille sur le dépôt
et non au dépôt lui-même. Un contributeur qui n'en veut pas doit pouvoir servir le projet sans.

Cela vaut pour tout outil : le dépôt dit **quoi installer et comment**, puis s'arrête.

*Vérifié par* : la relecture des surfaces de méthode - aucune ne prescrit un enregistrement comme un
geste dû. **Aucun garde ne peut le tenir** : c'est une règle sur ce que la prose demande, pas sur un
état du dépôt, et le dire vaut mieux que laisser croire à une couverture.

#### Scenario: un contributeur choisit
- **WHEN** un contributeur lit la prescription d'un outil dans la méthode
- **THEN** il trouve la commande qui l'installe, et rien ne le contraint à en enregistrer l'intégration

#### Scenario: le dépôt reste servable sans
- **WHEN** un contributeur installe l'outil sans enregistrer ses crochets
- **THEN** aucun garde du dépôt ne rougit de cette absence
