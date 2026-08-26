---
type: adr
title: "Un canal de distribution ne dépend pas d'un geste qu'on peut oublier"
status: stable
article: A20
chantier: "#4103, suite de #4071"
decided_at: 2026-08-21
verification: certaine
enforced_by:
  - ".github/scripts/verifie-conditions-de-job.sh"
verified:
  - by: machine:ci
    at: 2026-08-21
relations:
  prolonge: ["2744"]
---

# Un canal de distribution ne dépend pas d'un geste qu'on peut oublier

## Contexte

Le Flatpak était publié par un workflow **manuel**. Rien ne réclamait ce geste, donc personne ne le
faisait à la publication, et le paquet retardait sur les releases.

Mesuré le 2026-08-21, en vérifiant sur un poste que la ligne de commande de #4071 fonctionnait : le
manifeste extrayait encore le `.deb` **2.185.0** alors que la **2.187.0** était publiée depuis la
veille. La commande n'existait donc pas dans le paquet installé, pendant que la documentation
utilisateur l'annonçait.

**Le retard n'est pas le défaut, il en est le symptôme.** Le défaut est qu'un canal de distribution
dépende d'un geste que rien ne réclame : une publication réussie qui laisse le Flatpak où il était est
un état parfaitement **vert**. Aucun dispositif ne pouvait rougir, parce qu'aucun ne regardait là.

## Ce que l'ADR 2744 avait tranché, et pourquoi on la dépasse

Elle avait examiné cette question exacte, et conclu **de ne rien faire** :

> Un argument tombe pour les canaux de packaging, mais rien n'y change aujourd'hui. […] Il n'y a donc
> rien à rebrancher, ni maintenant ni probablement plus tard.

Deux choses la rouvrent, et aucune ne la contredit :

- **Elle raisonnait à moyens constants.** Sa raison principale tient toujours : un `release: released`
  produit au `GITHUB_TOKEN` ne déclenche jamais rien. Mais `workflow_call` n'est **pas un
  événement** - un workflow appelé s'exécute dans le run de l'appelant - et cette voie n'apparaît nulle
  part dans l'ADR. Ce n'est pas une décision qui change, c'est un moyen qui manquait.
- **Le coût de ne rien faire est maintenant mesuré.** L'ADR l'estimait nul ; deux versions de retard et
  une promesse fausse dans la documentation disent le contraire.

Son second argument - « `flatpak.yml` reste le mécanisme qui produit **et** vérifie chaque
publication » - est **tenu** : c'est ce workflow-là qu'on appelle, avec ses contrôles, dont celui de la
ligne de commande ajouté par #4100.

## Décision

**`release.yml` appelle `flatpak.yml` après avoir retiré le brouillon de la Release.** Le paquet part
avec le train, et le déclenchement manuel reste pour les montées de version détectées hors train.

### 1. `needs: publish`, jamais `installers`

Le `flatpak-external-data-checker` interroge `releases/latest`, qui **ignore les brouillons**. Dépendre
de l'étape qui téléverse les assets ne suffit donc pas : il faut celle qui sort la Release du
brouillon. Une dépendance qui a l'air juste et qui rendrait le manifeste inchangé, sans erreur.

### 2. La condition nomme l'état qu'elle attend

`!cancelled() && needs.publish.result == 'success'`. Sans cela, le « sauté » de
`contournement-declare` se propagerait jusqu'ici et le train rendrait vert **en n'ayant rien
publié** - le défaut que l'[ADR 4079](4079-une-condition-de-job-nomme-l-etat-qu-elle-attend.md) a
nommé, reproduit par le correctif d'un autre.

### 3. Un échec Flatpak ne compromet pas la Release

Elle est déjà publiée à ce stade. Ce qui change est qu'un paquet non publiable devient **visible dans
le run de publication**, au lieu de rester un silence.

## Ce que la décision NE couvre pas

**`winget.yml`**, manuel pour les mêmes raisons. Son coût n'a **pas** été mesuré : personne n'a
constaté qu'un paquet winget en retard ait trompé quelqu'un. L'aligner par symétrie serait une
supposition - exactement ce que ce chantier reproche à l'existant. À rouvrir le jour où la mesure
existe, pas avant.

## Conséquences

- **Premier `workflow_call` du dépôt.** Les dix-huit autres workflows restent indépendants.
- **Le train s'allonge** d'une quinzaine de minutes le mercredi.
- **Le chaînage ne s'observe qu'au premier train** : `release.yml` ne s'exécute pas sur les PR, donc
  aucune PR ne peut prouver que l'appel part. Ce qui est vérifié avant fusion se limite à la validité
  du montage - conditions de job, butoirs, renvois, YAML. Le reste se constate mercredi.

## Alternatives écartées

- **Un second cron décalé de deux heures.** Il repose sur une supposition de durée du train plutôt que
  sur une dépendance déclarée : la figure que ce dépôt refuse ailleurs, et qui casserait le jour où la
  publication déborde.
- **Un PAT ou une GitHub App** pour re-déclencher `release: released`. Fonctionnerait, au prix d'un
  secret de plus à faire vivre - pour un résultat que `workflow_call` obtient sans rien ajouter.
