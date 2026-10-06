# Outillage de publication

`semantic-release` et ses greffons, **figés par lockfile** (#2738, lot 3 du chantier #2720).

## Pourquoi ce dossier existe

Le job de publication installait sa chaîne par `npx --yes -p semantic-release@24 …`, **au moment de
publier**, dans un job autorisé à écrire contenus, issues et pull requests. La résolution de versions
se faisait à chaque exécution : un greffon compromis entre deux runs se serait exécuté avec les droits
de publication **sans qu'aucun diff du dépôt ne l'ait montré**.

Le lockfile fige l'arbre entier, `npm ci` refuse d'installer autre chose que lui, et toute montée de
version passe désormais par une PR relue. `.github/dependabot.yml` porte une entrée pour ce manifeste
(`npm`, mensuel), posée parce que figer sans surveiller échangerait un risque contre un autre.

**Cette entrée n'a jamais ouvert de demande** (#6084). Dependabot trouve les versions, soumet, et la
forge refuse : `dependency_file_not_supported`, « The request contains invalid or unauthorized
changes ». Mesuré le 2026-10-06 : 39 exécutions `npm` sur 39 en échec depuis le 4 août, les montées
de version comme les correctifs de sécurité, et aucune demande `npm` parmi les 55 que Dependabot a
ouvertes ici. Ce jour-là, `@semantic-release/changelog` est épinglé à 6.0.3 quand le registre rend
7.0.0, et `@semantic-release/git` à 10.0.1 quand il rend 11.0.1. La cause du refus n'est pas établie ;
ce manifeste vit sous `.github/`, et c'est l'hypothèse à éprouver en premier. D'ici là, la fraîcheur
de cet outillage se demande à la main, paquet par paquet :

```bash
npm view semantic-release version
npm view @semantic-release/changelog version
npm view @semantic-release/git version
```

`npm outdated` ne convient pas ici : sans `node_modules/`, que ce dossier n'a que sur le runner, il ne
rend rien et sort en 0, ce qui se lit « tout est à jour ».

## Les deux configurations, et pourquoi elles diffèrent

| Fichier | Greffons | Qui l'utilise |
|---|---|---|
| `.releaserc.json` (racine) | les 5 : analyse, notes, changelog, github, git | la **publication**, lancée depuis la racine |
| `release.config.js` (ici) | les 2 de **calcul** seulement | la **répétition à blanc** des PR |

La configuration d'analyse **dérive** de celle du dépôt : elle en importe le contenu et n'en garde que
les greffons qui lisent. Elle ne recopie donc pas les `parserOpts` - ceux qui tolèrent l'espace avant
les deux-points, « `fix(ci) : sujet` », usage typographique français. Une copie divergerait, et la
version calculée en vérification ne serait plus celle que la publication calculera. Le job
`outillage-release` de `lint.yml` vérifie cette dérivation.

## Vérifier en local

```bash
npm ci --prefix .github/release

# Répétition à blanc (n'écrit rien, aucun greffon d'écriture chargé) :
cd .github/release && ./node_modules/.bin/semantic-release --dry-run

# Ce que fera réellement la publication (5 greffons, lancé depuis la RACINE) :
./.github/release/node_modules/.bin/semantic-release --dry-run
```

`node_modules/` n'est pas versionné : seul le lockfile l'est.

**Cet arbre n'est pas posé par le crochet, et c'est une décision.** L'ADR 5407 range un prérequis
selon qu'un garde en dépend : celui dont un garde dépend se **pose** à la création du worktree, celui
dont aucun ne dépend se **propose**, parce que « la poser coûterait à chaque worktree pour un outil
qu'on ouvre rarement ». Mesuré le 2026-10-04 : **aucun garde local ne lit
`.github/release/node_modules`**, les deux scripts qui citent `.github/release` ne parlent que de
`release.config.js` et de portées, la porte ne l'engage pas, et son lockfile porte **476 paquets**.
Il reste donc à poser à la main, dans chaque arbre où l'on veut vérifier la publication, à la
différence de l'arbre d'OpenSpec, dont un garde dépend et que le crochet pose depuis #5775.

**Et `node` doit être sur le `PATH`, pas seulement `npm`.** C'est le second arbre de dépendances npm
du dépôt, et il porte le même piège que celui d'OpenSpec : si votre poste range `node` sous un
gestionnaire de version (nvm, asdf, volta, fnm), il est absent du `PATH` d'un shell non interactif,
donc `npm ci` échoue par `npm: command not found`. Et il doit y **rester** : les binaires installés
sous `node_modules/.bin/` sont des scripts dont le shebang le réclame au moment où ils tournent.

Le piège a coûté huit corps de demande sur l'arbre d'OpenSpec, deux sessions les ayant classés
« environnementaux » après avoir vérifié qu'ils rougissaient aussi sur `main` (#5774). **Celui-ci
n'est pas posé par le crochet `post-checkout`**, contrairement à l'arbre d'OpenSpec depuis #5775 :
il reste à poser à la main, dans chaque arbre de travail où l'on veut vérifier la publication.

## Ce que l'audit dit aujourd'hui

`npm audit` signalait **7 paquets vulnérables** (2 hautes, 5 moyennes) au passage en
`semantic-release@25` (#3264), contre **18** (15 hautes) avant lui. **Refait le 2026-10-06 sur le même
lockfile : 17 paquets, 16 hautes et 1 moyenne.** La base des avis a grandi, l'arbre n'a pas bougé, et
ce qui suit décrit l'état d'août : la relecture de ces dix-sept n'est pas faite (#5290). Ces vulnérabilités **existaient déjà** avec `npx --yes` ;
la différence est qu'elles sont désormais **visibles**, et c'était l'objet du lockfile.

Ce qui reste **ne se corrige pas ici**, à aucune version de `semantic-release` : les deux hautes
(`brace-expansion`, `ip-address`) vivent dans le `npm` que `semantic-release` **embarque**
(`node_modules/npm`, aujourd'hui 11.19.0). `npm audit` les annonce « corrigeables sans majeure », mais
`npm audit fix` répond lui-même `is a bundled dependency of npm@… · It cannot be fixed
automatically`. Elles partiront quand `npm` publiera une version qui les embarque corrigées, et que
`semantic-release` la reprendra.

**Ne pas lire un nombre d'alertes Dependabot comme une mesure de l'exposition.** GitHub
**auto-écarte** les avis de portée `development`, ce qu'est tout cet arbre : au 2026-08-04, quatre avis
ont été écartés ainsi sans que le compte affiché bouge. Et la montée en `semantic-release@25` a fait
passer `npm audit` de 18 paquets à 7, **sans changer le compte d'alertes** (6 avant, 6 après) - seule
la composition et la gravité avaient bougé (4 hautes → 1). C'est `npm audit` qui fait foi ici, pas le
compteur.

## Les alertes de cet arbre sont écartées, et c'est vérifiable (#3390)

Les six alertes Dependabot restantes (`undici` ×3, `ip-address` ×3) ont été **écartées** au motif
`not_used` - « le code vulnérable n'est pas réellement utilisé ». Ce n'est pas une commodité, c'est un
fait qui se vérifie en trois points :

- `.releaserc.json` liste **cinq** greffons, et `@semantic-release/npm` n'en fait pas partie ;
- `release.config.js` non plus : **zéro** occurrence dans les deux fichiers ;
- la répétition à blanc charge ses greffons **un par un** dans son journal, et aucun ne vient de ce
  paquet.

Le CLI `npm` que `semantic-release` embarque est donc **installé et jamais invoqué**. `undici` et
`ip-address`, qui vivent dedans, ne sont jamais chargés.

Écarter n'est pas ignorer : un avis **futur** sur ces paquets ouvrira une alerte neuve. Et si
`@semantic-release/npm` entrait un jour dans la configuration, la justification tomberait avec - c'est
la première chose à rouvrir dans ce cas.

### Les deux pistes qui ne marchent pas, mesurées plutôt que supposées

**Forcer une `npm` plus récente** (`overrides`) : essayé avec la dernière publiée, `12.0.2`.
L'arbre se résout, et l'audit rend **exactement les mêmes 7 paquets vulnérables**. `npm` embarque ses
propres dépendances : en changer la version ne change pas ce qu'elle transporte.

**Retirer `@semantic-release/npm`** : impossible proprement. C'est une dépendance **directe** de
`semantic-release` (`^13.1.1`), qui tire `npm@^11.6.2` - donc tout le sous-arbre. `npm` ne sait pas
supprimer une dépendance, seulement en forcer la version, et l'aliaser vers un paquet inerte est
exactement le genre d'astuce qui casse une chaîne de publication en silence.

## Pourquoi la version de Node est épinglée

Les workflows demandent `node-version: "24"` et non `lts/*`. Un lockfile fige l'arbre, mais `lts/*`
laissait flotter le **runtime qui l'exécute** : au prochain passage de majeure LTS, le job de
publication aurait changé de Node sans PR ni relecture. `semantic-release@25` exige d'ailleurs
`^22.14.0 || >= 24.10.0` - avec `lts/*`, la satisfaction de cette contrainte dépendait de ce que le
runner avait en cache ce jour-là.
