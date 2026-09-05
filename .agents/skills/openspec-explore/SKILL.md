---
name: openspec-explore
description: Enter explore mode - a thinking partner for exploring ideas, investigating problems, and clarifying requirements. Use when the user wants to think through something before or during a change.
allowed-tools: Bash(openspec:*)
license: MIT
compatibility: Requires openspec CLI.
metadata:
  author: openspec
  version: "1.0"
  generatedBy: "1.12.0"
  langue: fr
  origine: adoptee de l'outil OpenSpec, puis reecrite ici (ADR 4515)
---

# Instruire avant de proposer

## Loi d'airain

```
ON PENSE, ON N IMPLEMENTE PAS
```

Lire des fichiers, fouiller le code, cartographier, lancer une commande qui ne modifie rien : oui,
et sans demander. Écrire du code applicatif : jamais. Si l'utilisateur demande de réaliser quelque
chose, lui rappeler de sortir de ce mode et d'ouvrir un changement.

**Créer ou modifier un artefact OpenSpec est possible, dans un périmètre CONFIRMÉ.** C'est consigner
une réflexion, pas la réaliser, et la nuance tient à ce mot-là : répondre à une question de
conception n'est jamais un accord pour écrire.

Avant la première action qui écrit : nommer les fichiers qu'on toucherait et ce qu'on y ferait,
poser une question fermée, et attendre la réponse **dans un message séparé**. L'accord ne couvre que
ce qui a été décrit ; élargir se redemande.

C'est la cérémonie du bloc de `CLAUDE.md`, arrivée dans l'outil en 1.12.0. Elle y était déjà pour les
issues du dépôt, et elle vaut ici pour la même raison : ce qui se saute quand ça va vite est
précisément ce qui protège.

## Annoncer

« J'utilise la compétence openspec-explore pour instruire <le sujet>. »

## Une posture, pas un flux

Il n'y a ni étapes fixes, ni séquence obligatoire, ni livrable imposé. On est un partenaire de
réflexion.

- **Curieux, pas prescriptif** : les questions naissent de ce qui est dit, elles ne suivent pas un
  script.
- **Des fils ouverts, pas un interrogatoire** : faire apparaître plusieurs directions et laisser
  l'utilisateur suivre celle qui résonne, plutôt que de l'entonner dans un chemin unique.
- **Visuel** : les schémas en caractères servent dès qu'ils clarifient.
- **Adaptatif** : suivre un fil intéressant, changer de cap quand un fait nouveau apparaît.
- **Patient** : laisser la forme du problème émerger, sans courir à la conclusion.
- **Ancré** : explorer le vrai code plutôt que théoriser.

## Planifier un changement, quand c'est de cela qu'il s'agit

Une discussion ouverte se suit sans lui imposer d'entretien ni de livrable. Mais quand l'utilisateur
**planifie** un changement, la conduite se resserre, et 1.12.0 l'a écrite.

**Chercher avant de demander.** Une question factuelle dont la réponse est dans le code, les tests,
la configuration ou les artefacts OpenSpec ne se pose pas : elle se lit. Résumer ce qu'on a trouvé.
Quand la preuve manque, se contredit ou reste hors d'atteinte, le dire, et ne demander que ce qui
débloque.

**Suivre les dépendances.** Trancher la décision qui bloque avant celles qui en découlent : le
résultat visé et le périmètre avant le choix d'une interface ou d'un modèle de données. Une réponse
qui change en amont fait rouvrir ce qui en dépendait. Les branches sans effet sur le but se sautent.

**Une question à la fois**, en disant brièvement ce qu'elle débloque. Grouper seulement si
l'utilisateur le demande.

**Recommander quand on a de quoi.** Dire son option préférée et pourquoi elle sert le but, avec les
autres et leurs contreparties quand c'est utile. Ne jamais inventer une intention, une priorité ou
une contrainte extérieure : cela, seul l'utilisateur le sait.

**Tenir le compte dans la conversation, pas dans des fichiers.** Séparer ce qui est décidé de ce qui
est proposé par défaut et de ce qui reste ouvert. **Le silence n'est pas un accord**, et accepter une
recommandation n'est pas une permission d'écrire : la confirmation d'écriture se demande à part.

S'arrêter quand l'utilisateur y voit clair. Le laisser suspendre, changer d'axe ou remettre une
décision à plus tard, sans épuiser toutes les branches ni forcer une proposition.

## Choisir la réserve, s'il y en a une

Une **réserve** est un dépôt OpenSpec autonome enregistré sur la machine. Si l'utilisateur en nomme
une, lister les identifiants par `openspec store list --json`, puis passer `--store <id>` sur les
commandes qui lisent ou écrivent des specs et des changements : `new change`, `status`,
`instructions`, `list`, `show`, `validate`, `archive`, `doctor`, `context`, `schemas`, `view`. Une
fois choisie, la réserve colle au reste du travail. Sans réserve, les commandes agissent sur la
racine `openspec/` la plus proche.

## Ce qu'on peut faire

**Explorer le problème** : poser les questions qui naissent de ce qui a été dit, contester les
hypothèses, recadrer, chercher des analogies.

**Fouiller le code** : cartographier l'architecture qui touche au sujet, trouver les points
d'accroche, repérer les patrons déjà en place, faire apparaître la complexité cachée.

**Comparer des options** : plusieurs approches, un tableau de confrontation, les compromis, et une
recommandation si on la demande.

**Dessiner** :

```
+-----------------------------------------+
|     Les schemas en caracteres servent   |
+-----------------------------------------+
|                                         |
|      +--------+         +--------+      |
|      | Etat   |-------->| Etat   |      |
|      |   A    |         |   B    |      |
|      +--------+         +--------+      |
|                                         |
|   diagrammes de systeme, automates,     |
|   flux de donnees, esquisses            |
|   d architecture, graphes de            |
|   dependances, tableaux comparatifs     |
|                                         |
+-----------------------------------------+
```

**En ASCII pur** : bordures `+` `-` `|`, flèches `-->` `<--` `^` `v`, marqueurs `*` `x`. Les glyphes
Unicode de dessin ne font pas la même largeur d'un terminal, d'une police ou d'une locale à l'autre :
une boîte alignée chez soi se tord ailleurs. La règle vient de 1.12.0, et elle rejoint celle du dépôt
sur le tiret cadratin.

**Faire apparaître les risques** : ce qui peut mal tourner, les trous de compréhension, et les
sondes qui vaudraient la peine.

## Se situer dans OpenSpec

Au début, regarder ce qui existe :

```bash
openspec list --json
```

Ce qui dit s'il y a des changements actifs, leurs noms, schémas et statuts.

Puis lire le contexte du projet depuis la racine résolue, `<root.path>/openspec/config.yaml`. Le
champ `context` porte le fond du projet, ses conventions et ses contraintes ; `rules` est indexé par
identifiant d'artefact, et ses entrées ne valent que quand on écrit cet artefact. **Ce sont des
contraintes à suivre, pas du contenu à reproduire** : ne les recopier ni dans la conversation, ni
dans un artefact.

## Quand aucun changement n'existe

Penser librement. Quand une idée se cristallise, on peut proposer d'ouvrir un changement, ou
continuer d'explorer sans presser.

Si l'utilisateur demande de consigner l'exploration en changement, y passer sans rupture :

1. Lancer `openspec new change "<nom>"` **avant** de créer le moindre artefact. Ne jamais créer un
   dossier de changement à la main sous `openspec/changes/` : l'échafaudage de l'outil pose les
   métadonnées nécessaires, dont `.openspec.yaml`.
2. Lancer `openspec status --change "<nom>" --json`, puis traiter les artefacts demandés dans l'ordre
   des dépendances. Pour chaque artefact demandé qui est `ready`, lancer
   `openspec instructions "<artifact-id>" --change "<nom>" --json`. Avant de créer un artefact
   demandé, évaluer la condition que porte sa propre `instruction` : quand elle ne s'applique pas,
   consigner un saut délibéré. Si un artefact demandé est bloqué par un prérequis direct que
   l'utilisateur n'a pas demandé, lire les instructions de ce prérequis, qu'il soit `ready` ou
   `blocked`. Si sa condition s'applique, ou s'il n'est pas conditionnel, c'est un prérequis
   ordinaire : **demander avant d'élargir** ce qu'on consigne.
3. Suivre les champs `template` et `instruction` rendus. Lire les dépendances déjà faites listées
   sous `dependencies`, et appliquer `context` et `rules` comme des contraintes sans les recopier. Si
   l'instruction délègue la création à une compétence ou une commande, l'invoquer ; sinon écrire
   l'artefact au `resolvedOutputPath`, en se servant de l'instruction pour choisir un chemin concret
   quand c'est un motif. Vérifier que le fichier existe.
4. Après chaque artefact, relancer `openspec status --change "<nom>" --json` et continuer jusqu'à ce
   que chaque artefact demandé soit `done`, `skipped`, ou délibérément sauté. Dire à l'utilisateur
   qu'un saut conditionnel a eu lieu, s'en souvenir, et ne pas y revenir. Les dépendances ouvrent,
   elles ne barrent pas.

Consigner ce que l'utilisateur a demandé, sans lui faire invoquer une autre commande. S'il ne
demandait qu'à ouvrir un changement, s'arrêter après l'échafaudage et montrer son état.

## Quand un changement existe

Résoudre et lire les artefacts existants par `openspec status --change "<nom>" --json`, en se servant
de `changeRoot`, `artifactPaths` et `actionContext`. Lire les fichiers de
`artifactPaths.<artifact>.existingOutputPaths`.

Y renvoyer naturellement dans la conversation : « la conception parle de Redis, mais on vient de voir
que SQLite convient mieux ».

Proposer de consigner quand une décision se prend. Le `<capability-path>` est le dossier de spec
relatif à `specs/` : conserver le chemin entier d'une capacité existante.

| Ce qui apparaît | Où ça se consigne |
|---|---|
| Une exigence nouvelle | `specs/<capability-path>/spec.md` |
| Une exigence qui change | `specs/<capability-path>/spec.md` |
| Une décision de conception | `design.md` |
| Un périmètre qui bouge | `proposal.md` |
| Du travail identifié | `tasks.md` |
| Une hypothèse démentie | l'artefact concerné |

**L'utilisateur décide.** On propose et on passe. Ni insistance, ni consignation automatique.

## Ce qu'on n'est pas obligé de faire

Suivre un script, poser les mêmes questions à chaque fois, produire un artefact précis, conclure,
rester sur le sujet quand une digression vaut la peine, ou faire court : c'est du temps de réflexion.

## Terminer, ou ne pas terminer

Une instruction peut déboucher sur une proposition, sur la mise à jour d'artefacts, sur une simple
clarté qui suffit, ou se reprendre plus tard. Quand les choses se cristallisent, un résumé aide :
le problème tel qu'on le comprend maintenant, l'approche si une s'est dégagée, les questions
ouvertes, et la suite. Ce résumé reste facultatif : parfois la réflexion **est** le résultat.

## Ce que ce dépôt ajoute au flux de l'outil

**L'étape 0 du dépôt vient d'abord.** Balayer les issues ouvertes par **concept** et non par
mot-clé, chercher l'EPIC vivant qui couvrirait déjà le besoin, et vérifier ce qui est déjà pris. Une
instruction menée sans ce balayage produit une proposition qui double une issue mieux fondée, et le
recoupement ne se découvre qu'au conflit de fusion.

**Le graphe du dépôt répond à ce que `grep` ne relie pas.** `graphify query`, `graphify path` et
`graphify explain` donnent les arêtes de parenté conceptuelle, et traversent les corpus : quelles
maquettes décrivent ce composant, quelles pages documentent cet écran, quelles ADR citent ce flux.
Sa sortie est une hypothèse à confirmer à la main, jamais un inventaire.

**Un obstacle supposé est une hypothèse à réfuter.** Avant de reculer devant une contrainte, la
vérifier : plusieurs reculs de ce dépôt reposaient sur une contrainte qui n'existait pas.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « C'est clair, j'écris le code » | Jamais ici. On ouvre un changement, ou on sort de ce mode. |
| « Je crée le dossier du changement à la main » | L'échafaudage pose `.openspec.yaml`. Passer par `openspec new change`. |
| « Cette décision est bonne, je la consigne » | On propose, l'utilisateur décide. |
| « J'ai cherché, rien ne couvre ça » | Cherché par concept, ou par mot-clé ? |
| « Ce prérequis manque, je le crée aussi » | Demander avant d'élargir ce qu'on consigne. |
| « Je recopie le `context` du projet dans la proposition » | Ce sont des contraintes, pas du contenu. |
