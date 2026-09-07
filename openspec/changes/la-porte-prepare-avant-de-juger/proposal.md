## Why

La porte locale `scripts/batterie.py` juge un dépôt sur un poste qu'elle ne prépare pas. Plusieurs
gardes refusent alors de conclure faute d'un prérequis, et ce refus **ressemble à un défaut du
changement en cours**.

Ce n'est pas une hypothèse : `pyproject.toml` est né de ce défaut exact pour les modules Python
(#5008), et son en-tête l'écrit - « en local, six des neuf plantaient nu sur `ModuleNotFoundError`,
erreur qui ressemble à un défaut du changement en cours ». Le même mécanisme joue aujourd'hui sur les
**outils**, que rien ne déclare.

**Mesuré le 2026-09-06**, sur ce poste et dans un worktree neuf créé sur `origin/main` :

| prérequis | état | commande | durée mesurée |
|---|---|---|---|
| `.github/openspec/node_modules` | absent de tout worktree neuf | `npm ci --prefix .github/openspec` | **2 s** |
| `ruff` | absent du poste | venv + `ruff==0.16.5` | **4 s** |
| `graphifyy` | absent du poste **et** du runner | `uv tool install graphifyy` | non mesurée |
| `target/pmd.xml` | absent tant que PMD n'a pas tourné | `./mvnw -B -o test-compile pmd:pmd` | **20 s** (9 s à chaud) |

Les durées sont des mesures `date +%s` autour de la commande, cache chaud, pas des estimations.

**Le savoir existe déjà et ne suffit pas.** La compétence `ouvrir-une-pr` donne les trois premières
commandes, ligne par ligne, version épinglée comprise. Elles n'ont pas été appliquées de la session du
2026-09-06, où `ruff` a laissé passer deux reproches jusqu'en CI. Un geste écrit qu'on saute quand ça
va vite demande un dispositif, pas une phrase de plus.

**Et un nom trompe.** Le dépôt prescrit `graphify` dans `AGENTS.md`, `CONTRIBUTING.md` et
`dev-docs/chercher-dans-le-depot.md`, sans nommer nulle part la distribution `graphifyy`. Or
`pip install graphify` n'existe pas, et `npm i -g graphify` installe **un autre paquet** - un
générateur de graphes aléatoires publié par un tiers. Le piège est le même que `PyYAML` / `yaml`, que
`pyproject.toml` documente déjà et qu'un garde lit.

Le « pourquoi » public vit dans #4849, ouverte le 2026-08-30 et jamais prise.

## What Changes

**La porte prépare ce qui est déclaré, puis juge.** Un prérequis posable par une commande cesse d'être
un refus à interpréter.

Trois familles, et elles n'appellent pas le même geste :

- **déclaré et bon marché** (openspec, ruff) : posé sans discussion, 6 secondes au total ;
- **déclaré et cher** (`target/pmd.xml`, 20 secondes sur un arbre neuf, 9 à chaud) : posé **seulement si le diff touche du
  `.java`** - la porte le sait déjà, c'est sa raison d'être ;
- **irréductible** (un paquet réel, une carte SD montée) : inchangé. La ligne de refus reste le bon
  geste, et la porte la rend déjà bien.

**Les outils du dépôt se déclarent, comme ses modules.** `graphifyy` entre dans `pyproject.toml`, et
la correspondance distribution -> commande se déclare là où `PyYAML -> yaml` l'est déjà. La méthode
cesse de prescrire un nom qui installe autre chose.

**Ce que ce changement ne fait pas.** Il ne partage aucune installation entre worktrees : #4849
appelait cette piste « la plus tentante et probablement la mauvaise », parce qu'une branche qui change
la version épinglée serait éprouvée contre celle d'une autre. La mesure la clôt - elle échangeait ce
risque contre 2 secondes.

Il ne touche pas non plus à la CI, qui installe déjà tout. **Ce dispositif n'a donc aucun gardien en
CI**, et c'est sa difficulté propre : son auto-test porte seul.

## Capabilities

### New Capabilities
- `outillage/preparation-de-l-environnement`: ce qu'un poste doit avoir pour qu'un verdict local vaille, ce que le dépôt pose lui-même, et ce qu'il refuse de poser.

### Modified Capabilities
