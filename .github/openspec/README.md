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

Les douze fichiers d'OpenSpec présents dans le dépôt portent `generatedBy: "1.12.0"` dans leur
en-tête. Ils décrivent le contrat de la ligne de commande de cette version : ses sous-commandes, les
champs de son JSON, les états qu'elle rend. Une version installée qui ne serait pas celle-là ferait
décrire un contrat périmé par des fichiers qui se lisent comme vrais.

`scripts/methode/verifie-version-openspec.py` tient cette égalité et rougit sur l'écart. Il lit le
lockfile plutôt que d'invoquer la commande : c'est la version **du dépôt** qui fait foi, pas celle du
poste, et l'intégration continue n'a alors pas besoin d'installer l'outil pour répondre à la
question.

Une montée de version n'est donc pas un simple `npm update`. Elle demande de régénérer les douze
fichiers, ou de vérifier à la main ce que le nouveau contrat change.

**Comment le vérifier sans rien écraser.** Régénérer par-dessus effacerait la réécriture française
que veut l'[ADR 4515]. On engendre donc les deux versions **à côté**, et on confronte :

```bash
mkdir -p /tmp/av /tmp/ap
(cd /tmp/av && <ancienne>/openspec init . --tools claude --no-animation)
(cd /tmp/ap && <nouvelle>/openspec init . --tools claude --no-animation)
diff -r /tmp/av/.claude/skills /tmp/ap/.claude/skills
```

Seuls les écarts réels se portent ensuite dans la réécriture.

**Ce que 1.10.0 vers 1.12.0 a coûté**, mesuré ainsi le 2026-09-05 (#5291) : quatre compétences sur
six ne changeaient que leur `generatedBy` ; `openspec-propose` gagnait un bloc « inspecter le projet
avant de rédiger » ; `openspec-explore` gagnait 27 lignes, dont une règle de consentement qui reprend
la cérémonie du bloc de `CLAUDE.md`. Deux portages à la main, et rien d'autre.

[ADR 4515]: https://companion-dev.echonuit.fr/decisions/4515-les-competences-openspec-sont-reecrites/

## Vérifier en local

**Le crochet `post-checkout` l'a normalement déjà posé**, depuis #5406, et depuis #5775 il y arrive
même quand votre poste range `node` sous un gestionnaire de version : il demande au poste où vit son
outil au lieu de le chercher dans son propre `PATH`. Le contrôle tient en une ligne, et il vaut mieux
que la confiance :

```bash
ls .github/openspec/node_modules | wc -l    # 53 attendu
```

S'il rend zéro, relancez `python3 scripts/methode/prepare-l-environnement.py`, qui nomme ce qui manque
et sort **2** quand il n'a pas pu poser. Et si vous posez à la main, `node` doit être sur le `PATH`
**au moment où l'outil tourne**, pas seulement à l'installation : le lien installé est un script dont
le shebang le réclame.

```bash
npm ci --prefix .github/openspec

./.github/openspec/node_modules/.bin/openspec --version      # 1.12.0
./.github/openspec/node_modules/.bin/openspec context --json # "role": "openspec_root"

python3 scripts/methode/verifie-version-openspec.py          # l'égalité tient
python3 scripts/methode/verifie-version-openspec.py --auto-test
```

`node_modules/` n'est pas versionné : seuls `package.json` et `package-lock.json` le sont.

## Ce que l'audit dit aujourd'hui

`npm audit` signale **4 paquets**, tous au niveau élevé, sur les 80 dépendances de l'arbre, au
6 octobre 2026. Chiffre daté, qui se refait plutôt qu'il ne se croit : il valait 0 sur 79 le 26 août.

Un seul avis porte les quatre : `braces`, [GHSA-vfj7-8cjw-p6xm], un déni de service par motif
profondément imbriqué, publié le 18 septembre 2026, donc après la montée en 1.12.0. Il remonte par
`micromatch`, `fast-glob`, puis `@fission-ai/openspec` lui-même. **Monter de version ne le corrige
pas** : l'audit range toutes les versions depuis 0.18.0 dans la plage touchée, et le seul correctif
qu'il propose est un retour à 0.17.2. Ce que cet avis expose réellement ici n'a pas été évalué.

```bash
(cd .github/openspec && npm audit)
```

[GHSA-vfj7-8cjw-p6xm]: https://github.com/advisories/GHSA-vfj7-8cjw-p6xm

## Personne ne suit ce manifeste, et il faut le savoir

Cette page a écrit du 26 août au 6 octobre 2026 que Dependabot suivait ce manifeste au même titre
que `.github/release/`. C'était faux deux fois (#6084).

`.github/dependabot.yml` n'a jamais porté d'entrée pour ce dossier. Et en poser une n'aurait rien
changé : celle de `.github/release/` existe depuis #3252, et la forge refuse chacune des demandes que
Dependabot tente d'y ouvrir. Il trouve la version, soumet la demande, et reçoit
`dependency_file_not_supported`, « The request contains invalid or unauthorized changes ». Mesuré le
2026-10-06 sur les exécutions « Dependabot Updates » du dépôt : 39 sur 39 ont échoué depuis le
4 août, et sur 55 demandes ouvertes par Dependabot, aucune ne porte sur `npm`. L'échec se range dans
l'onglet Actions, sous un atelier que personne n'est tenu de regarder.

La cause n'est pas établie. Les deux manifestes vivent sous `.github/`, et c'est l'hypothèse à
éprouver en premier ; elle ne l'a pas été.

**L'épinglage vieillit donc sans que rien ne le dise.** `verifie-version-openspec.py` tient une
égalité, pas une fraîcheur : il reste vert sur une version dépassée. Au 2026-10-06, le dépôt épingle
1.12.0 et le registre rend 1.14.1. La question se pose à la main :

```bash
npm view @fission-ai/openspec version
```

et la montée, quand on la décide, se fait par le geste de la section « Pourquoi cette version-là, et
pas la dernière ».
