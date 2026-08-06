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

⚠️ **Ne pas lire un nombre d'alertes Dependabot comme une mesure de l'exposition.** GitHub
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

⚠️ Écarter n'est pas ignorer : un avis **futur** sur ces paquets ouvrira une alerte neuve. Et si
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
