# Outillage de publication

`semantic-release` et ses greffons, **figés par lockfile** (#2738, lot 3 du chantier #2720).

## Pourquoi ce dossier existe

Le job de publication installait sa chaîne par `npx --yes -p semantic-release@24 …`, **au moment de
publier**, dans un job autorisé à écrire contenus, issues et pull requests. La résolution de versions
se faisait à chaque exécution : un greffon compromis entre deux runs se serait exécuté avec les droits
de publication **sans qu'aucun diff du dépôt ne l'ait montré**.

Le lockfile fige l'arbre entier, `npm ci` refuse d'installer autre chose que lui, et toute montée de
version passe désormais par une PR relue. Dependabot suit ce manifeste (`npm`, mensuel) : figer sans
surveiller échangerait un risque contre un autre.

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

## Ce que l'audit dit aujourd'hui

`npm audit` signale **7 paquets vulnérables** (2 hautes, 5 moyennes), contre **18** (15 hautes) avant
le passage en `semantic-release@25` (#3264). Ces vulnérabilités **existaient déjà** avec `npx --yes` ;
la différence est qu'elles sont désormais **visibles**, et c'était l'objet du lockfile.

Ce qui reste **ne se corrige pas ici**, à aucune version de `semantic-release` : les deux hautes
(`brace-expansion`, `ip-address`) vivent dans le `npm` que `semantic-release` **embarque**
(`node_modules/npm`, aujourd'hui 11.19.0). `npm audit` les annonce « corrigeables sans majeure », mais
`npm audit fix` répond lui-même `is a bundled dependency of npm@… · It cannot be fixed
automatically`. Elles partiront quand `npm` publiera une version qui les embarque corrigées, et que
`semantic-release` la reprendra.

⚠️ **Ne pas lire un nombre d'alertes Dependabot comme une mesure de l'exposition** : GitHub
**auto-écarte** les avis de portée `development`, ce qu'est tout cet arbre. Au 2026-08-04, quatre avis
(trois sur `brace-expansion`, un sur `picomatch`) ont été écartés ainsi, sans que le compte affiché
bouge. `npm audit`, lui, les voit. C'est `npm audit` qui fait foi ici.

## Pourquoi la version de Node est épinglée

Les workflows demandent `node-version: "24"` et non `lts/*`. Un lockfile fige l'arbre, mais `lts/*`
laissait flotter le **runtime qui l'exécute** : au prochain passage de majeure LTS, le job de
publication aurait changé de Node sans PR ni relecture. `semantic-release@25` exige d'ailleurs
`^22.14.0 || >= 24.10.0` - avec `lts/*`, la satisfaction de cette contrainte dépendait de ce que le
runner avait en cache ce jour-là.
