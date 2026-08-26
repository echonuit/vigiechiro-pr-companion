# Outillage de spécification vivante

`@fission-ai/openspec`, **figé par lockfile** (#4512, lot 1 du chantier #4511).

## Pourquoi ce dossier existe

Les six compétences OpenSpec de `.agents/skills/` déclarent `compatibility: Requires openspec CLI`,
et chacune de leurs étapes appelle `openspec status --json`, `openspec instructions <artefact> --json`
ou `openspec new change`. Sans cette ligne de commande, aucune ne fonctionne.

Elle n'existait nulle part dans le dépôt. Elle vivait en installation globale sur un seul poste, ce
qui est exactement ce dont `scripts/adr/2843-tiret-cadratin.py` se méfie dans ses propres termes :
« un garde qui ment selon la machine ne vaut rien ». Le cadre posé par #4355 n'a jamais servi pour
cette raison.

Le lockfile fige l'arbre entier, `npm ci` refuse d'installer autre chose que lui, et toute montée de
version passe par une PR relue. Le patron est celui de `.github/release/`, et les raisons sont les
mêmes.

## Le binaire doit s'appeler `openspec`

Les compétences déclarent `allowed-tools: Bash(openspec:*)`. C'est un motif **littéral** : il
autorise les commandes qui commencent par le mot `openspec`, et rien d'autre.
`npx @fission-ai/openspec …` ne lui correspond pas, et serait refusé.

Après `npm ci`, le lien `node_modules/.bin/openspec` existe. C'est lui qu'il faut exposer sur le
`PATH`, ce que le devcontainer fait par `remoteEnv`.

## Pourquoi cette version-là, et pas la dernière

Les douze fichiers d'OpenSpec présents dans le dépôt portent `generatedBy: "1.10.0"` dans leur
en-tête. Ils décrivent le contrat de la ligne de commande de cette version : ses sous-commandes, les
champs de son JSON, les états qu'elle rend. Une version installée qui ne serait pas celle-là ferait
décrire un contrat périmé par des fichiers qui se lisent comme vrais.

`scripts/methode/verifie-version-openspec.py` tient cette égalité et rougit sur l'écart. Il lit le
lockfile plutôt que d'invoquer la commande : c'est la version **du dépôt** qui fait foi, pas celle du
poste, et l'intégration continue n'a alors pas besoin d'installer l'outil pour répondre à la
question.

Une montée de version n'est donc pas un simple `npm update`. Elle demande de régénérer les douze
fichiers, ou de vérifier à la main ce que le nouveau contrat change.

## Vérifier en local

```bash
npm ci --prefix .github/openspec

./.github/openspec/node_modules/.bin/openspec --version      # 1.10.0
./.github/openspec/node_modules/.bin/openspec context --json # "role": "openspec_root"

python3 scripts/methode/verifie-version-openspec.py          # l'égalité tient
python3 scripts/methode/verifie-version-openspec.py --auto-test
```

`node_modules/` n'est pas versionné : seuls `package.json` et `package-lock.json` le sont.

## Ce que l'audit dit aujourd'hui

`npm audit` signale **0 paquet vulnérable** sur les 79 dépendances de l'arbre, au 26 août 2026.
Chiffre daté, qui se refait plutôt qu'il ne se croit.

Dependabot suit ce manifeste au même titre que `.github/release/` : figer sans surveiller
échangerait un risque contre un autre.
