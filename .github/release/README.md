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

`npm audit` signale **18 paquets vulnérables** (15 hautes, 3 moyennes) dans l'arbre transitif, pour
l'essentiel le `npm` que `semantic-release` embarque (`@npmcli/arborist`, `pacote`, `sigstore`…). Ces
vulnérabilités **existaient déjà** avec `npx --yes` ; la différence est qu'elles sont désormais
**visibles**. Elles ne sont pas corrigées ici : `npm audit fix --force` changerait de majeure, et le
traitement des alertes de dépendances est l'objet de #2740.
