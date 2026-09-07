---
name: trier-les-issues
description: Use before opening a chantier or claiming an issue, to decide whether there is anything to open at all. Covers sweeping open issues by concept rather than keyword, finding the live EPIC that already covers the need, checking what is already claimed, and querying the repository knowledge graph for what grep cannot relate.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Triage

## Loi d'airain

```
UN AUDIT PRODUIT UN COMPTAGE, PAS UNE LECTURE
```

Un comptage exact peut mélanger deux problèmes de profondeur différente dont l'un a déjà son
analyse ailleurs. Avant d'ouvrir quoi que ce soit, on cherche ce qui existe déjà.

## Annoncer

« J'utilise la compétence trier-les-issues pour décider s'il y a lieu d'ouvrir sur <le sujet>. »

## Pourquoi cette étape existe

Une passe de clôture a compté 28 endroits écrivant leur sévérité dans du texte, et en a fait une
issue. Une autre issue couvrait déjà **six des huit cas les plus profonds**, avec un remède plus
juste, et son prérequis a fusionné pendant que le doublon s'écrivait. Le recoupement n'a été vu
qu'en lisant un commit apparu sur `main`.

**Rien ne garantit qu'une issue soit rattachée au bon chantier.** Elles naissent une par une, avec
le vocabulaire du chantier qui les a trouvées plutôt que celui du problème qu'elles décrivent. Deux
issues sur le même sujet, écrites depuis deux angles, ne se ressemblent pas.

## Fonction de garde

```
1. BALAYER   les issues ouvertes, pas seulement celles qu on croit concernees.
             Le tri se fait par CONCEPT, pas par mot-cle.
2. CHERCHER  les EPIC vivants qui couvriraient deja le besoin, et les issues FERMEES
             qui l ont differe. « differe de #N » signale un parent dont la moitie
             restante n a plus de toit.
3. VERIFIER  ce qui est deja pris, par TROIS signaux qui ne repondent pas a la meme
             question. Aucun ne suffit, et les confondre fait prendre une issue tenue :
             - `gh issue list --assignee "*"` : dit qu une issue est prise, JAMAIS par qui.
             - `git worktree list` : dit qu une SESSION de ce poste la tient, et laquelle.
             - demander aux pairs : le seul qui traverse les machines.
             Une revendication ANCIENNE se verifie au lieu de se croire.
4. DECIDER   du rattachement : une issue appartient au chantier qui traite sa CAUSE,
             pas a celui qui a remarque son symptome.
5. RECADRER  titre ET corps des issues deplacees. Un recadrage laisse en commentaire
             sous un corps perime ne recadre rien.
6. DIMENSIONNER chaque lot : combien de PR ? Plus de deux, c est un CHANTIER, donc
             le lot s ouvre en SOUS-CHANTIER et non sous une case a cocher.
```

Quand deux chantiers se recoupent, **découper le périmètre explicitement** et l'écrire dans les
deux, plutôt que de laisser la fusion arbitrer.

## Le palier du sous-chantier, à l'étape 6

**Plus de deux PR cohérentes, c'est un chantier.** Un lot qui portera plusieurs issues et plusieurs
PR s'ouvre en **sous-chantier**, pas sous une case à cocher.

La règle, sa mesure et son piège vivent dans la compétence `ouvrir-un-chantier`, qui porte le geste
du découpage. Elle est rappelée ici parce que cette compétence s'ouvre aux **deux** moments où des
lots se créent : au découpage d'un chantier, et à la passe 9 d'une clôture, où les suites deviennent
des issues.

## Interroger le graphe, pour ce que `grep` ne relie pas

Le tri par concept se heurte à un outil qui ne cherche que des chaînes.

```bash
graphify query "<question>" --budget 2500
graphify path "A" "B"
graphify explain "<concept>"
```

Ce qu'il donne et que `grep` ne peut pas :

- les arêtes `calls` descendent à la **méthode** : qui appelle réellement ceci, et ce code sert-il
  encore ;
- les arêtes `conceptually_related_to` répondent à « **qui d'autre fait X ?** » quand X est une
  idée et non un identifiant ;
- il **traverse les corpus** : quelles maquettes décrivent ce composant, quelles pages documentent
  cet écran, quelles ADR citent ce workflow.

Trois moments l'appellent, et ce sont les trois où l'on croit déjà savoir : l'**ouverture**,
l'**investigation d'un défaut**, l'**audit global**.

### Ce qu'il a trouvé et que les `grep` avaient manqué

| Question | Ce que `grep` donnait | Ce que le graphe a montré |
|---|---|---|
| Qui dérive un département ? | deux écritures de la règle | une **troisième**, qui ne cite aucun des noms cherchés, et sans aucun appelant |
| Qui écrit dans `sauvegardes/` ? | le service cité par l'issue | **deux** autres sources d'accumulation |
| Que fait `release.yml` ? | l'en-tête du fichier | un **troisième** job absent de l'en-tête, et deux ADR le référençant |

### Deux limites, et la seconde décide de la lecture

- **Il ne modélise que notre code.** Aucun nœud pour le JDK ni les bibliothèques : demander qui
  appelle `Files.readAllLines` rend zéro, et un zéro se lit comme une absence. Sur ces
  questions-là, `grep` est le bon outil.
- **Sa sortie est une hypothèse, jamais un inventaire.** Il photographie un commit, donc il
  vieillit, et une part de ses arêtes est inférée. Il **oriente** la recherche, il ne remplace pas
  la lecture. Un zéro se confirme à la main.

## Les trois signaux, et ce que chacun répond

Plusieurs sessions travaillent souvent ce dépôt en même temps. « Cette issue est-elle prise ? » et
« est-elle prise **par moi** ? » sont deux questions, et un seul des trois signaux répond à la seconde
sans sortir de la machine.

| Signal | Répond « c'est pris » ? | Répond « par qui » ? |
|---|---|---|
| `gh issue list --assignee "*"` | oui | **non** |
| `git worktree list` | oui, sur ce poste | **oui**, par le nom de branche |
| demander aux pairs | oui | oui, et **hors de ce poste** |

**L'assignee ne peut pas départager deux sessions.** Toutes écrivent sous le même compte : relevé le
2026-09-07, les issues assignées du dépôt le sont **toutes au même nom**. Une issue prise par un pair
et une prise par soi rendent la même ligne. C'est le même fait que l'ADR 5414 nomme : la forge
enregistre le compte, jamais la session.

**Le worktree, lui, distingue - et il est sous-employé.** Relevé le même jour : **cinquante
worktrees, dont trente-neuf portent une branche nommée par son numéro d'issue**. Le travail en cours
d'un pair y est lisible, avec son sujet, sans rien demander à personne.

```bash
git worktree list | grep -oE '\[[^]]+\]'     # les branches, donc les numeros d issue
```

Ce qu'il ne voit pas, et qu'il faut savoir avant de s'y fier : il ne montre que **cette machine**, et
qu'une session ayant **déjà créé** son worktree. Dans la collision du 6 septembre, où deux blocs ont
été déposés à vingt-trois secondes d'écart, il n'aurait probablement rien montré.

**Demander aux pairs est le seul signal qui traverse les machines**, et c'est celui que
[`ouvrir-une-issue`](../ouvrir-une-issue/SKILL.md) institue à la prise. Ici il sert en amont : avant
de décider qu'il y a lieu d'ouvrir ou de prendre, on peut déjà savoir qui travaille quoi.

**Aucun garde ne vérifiera que vous avez regardé.** Un signal consulté ne laisse aucune trace, et
l'ADR 5414 en fait une décision plutôt qu'un oubli : une règle que rien ne peut garder se déclare.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « J'ai cherché, rien ne couvre ça » | Avez-vous cherché par concept, ou par mot-clé ? |
| « Le graphe rend zéro, donc ça n'existe pas » | Zéro sur du code externe est une absence de modèle |
| « Cette issue est libre, personne n'est assigné » | L'assignation est muette. Lire `git worktree list` |
| « Elle est revendiquée depuis longtemps » | Vérifier : branche vivante ? PR ouverte ? |
| « L'assignee dit que c'est pris, donc je sais par qui » | Il dit le **compte**, jamais la session. Toutes écrivent sous le même |
| « `ListAgents` me montre mes pairs, je saurai » | Il dit qui est occupé **maintenant**, pas qui détient une issue. Et il ne rend pas la même population selon l'endroit d'où on l'interroge |
| « J'ai compté 28 occurrences » | Un comptage n'est pas une lecture |
| « Ce lot tiendra bien sous une case à cocher » | Combien de PR ? Plus de deux, il lui faut un sous-chantier |
| « Les autres EPIC font comme ça » | La forme observée enseigne l'erreur : #4511 porte sept lots et zéro sous-chantier |
