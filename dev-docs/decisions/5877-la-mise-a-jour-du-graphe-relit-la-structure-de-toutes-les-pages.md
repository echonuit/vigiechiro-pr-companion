---
type: adr
title: "La mise à jour du graphe relit la structure de toutes les pages, et dit ce qu'elle retire"
status: stable
article: A3
chantier: "#5877 (la structure d'une page couverte ne se relisait plus), lot 6 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/rebuild.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5790-le-graphe-lit-la-prose-d-un-perimetre-declare"]
  prolonge: ["5813-une-hyperarete-porte-un-identifiant-et-une-mise-a-jour-declare-ce-qu-elle-lache"]
generated:
  by: "process:assistance-par-agents"
---

# La mise à jour du graphe relit la structure de toutes les pages, et dit ce qu'elle retire

## Le contexte

L'ADR 5790 a posé une couche sémantique sur les pages du dépôt, et comptait sur `graphify update .`
pour tenir leur structure à jour. Le moteur ne le fait pas : il écarte de sa lecture tout document
qui porte déjà, dans le graphe, un nœud d'une autre couche. C'est délibéré, pour ne pas représenter
deux fois un même document.

Une page couverte garde donc les titres qu'elle avait le jour où elle a reçu sa couche. Un titre
ajouté depuis n'entre pas dans le graphe, un titre retiré n'en sort pas, et les fiches données aux
agents décrivent une page qui a changé.

Le défaut est sorti de #5868 : cinq pages de la racine avaient perdu leur nœud de page, et aucune
commande ne pouvait le leur rendre.

## Ce qui a été mesuré

Le 5 octobre, à `b40107ddd`, sur une copie du graphe de référence :

| | |
|---|---:|
| pages qui portent une couche | 586 |
| pages dont les titres dataient | 6 |
| extraction des 758 pages `.md` suivies | 3,8 s |
| fusion de la relecture, contre 16,8 s à vide | 19,0 s |
| titres retirés, sur une page | 4 |
| nœuds sémantiques perdus | 0 |
| arêtes sémantiques qui tombent, sur la même page | 10 |

Après un `graphify update .` complet, le titre « Les champs, exactement » d'une page couverte était
sur le disque et absent du graphe. Une page neuve, sans couche, y était entrée.

## La décision

**`rebuild.py --mets-a-jour` relit la structure de toutes les pages `.md` que le graphe porte, après
`graphify update .` et avant les ponts, et dit ce qu'elle retire.**

Toutes les pages, et non les seules pages couvertes. Les reconnaître, ce serait recopier dans le
dépôt la règle par laquelle le moteur les écarte. Relire tout rend le même graphe, pour une seconde
de plus.

Un titre que le disque ne porte plus sort du graphe, et les arêtes sémantiques qui y étaient
ancrées tombent avec lui. La commande dit combien, et sur quelles pages. Le concept reste, sans son
ancrage : la page est de celles que `a-reextraire` rend, son empreinte ayant changé. C'est la règle
de l'ADR 5813, étendue à la structure : une mise à jour déclare ce qu'elle lâche.

Une page que l'extraction ne rend pas garde sa structure. La fusion ne remplace que ce qu'elle
reçoit.

## Ce que cela ne couvre pas

Le crochet de commit appelle `rebuild.py` avec les fichiers modifiés, et ne relit que le code. Les
titres d'une page ne suivent qu'à `--mets-a-jour`.

Le moteur retire aussi des titres par dédoublonnage : ceux de `CHANGELOG.md`, et ceux qui portent
le nom de leur page ou d'un autre titre du même fichier. Il les retire même quand on les lui
redonne, 550 identifiants le 5 octobre, et se stabilise après une fusion. La relecture n'y change
rien.

Un nœud de pont de type document figerait une page de la même façon. `pont_doc_code.py` n'en
fabrique plus depuis #5868.

## Les alternatives écartées

- **Ne relire que les pages modifiées.** L'économie ne se mesure pas, et il faudrait un registre de
  plus pour savoir lesquelles.
- **Refuser quand une arête sémantique tomberait.** La structure resterait fausse jusqu'à une
  réextraction sémantique, qui demande une session.
- **Ne relire que les pages couvertes.** C'est la population exacte du défaut, et la reconnaître
  demande de recopier la règle du moteur, qui peut changer sans prévenir.
- **Corriger le moteur.** Il fait ce qu'il annonce, et il vit hors du dépôt.

## Comment on le sait

L'auto-test de `scripts/graphify/rebuild.py`, lancé par `lint.yml`, tient trois choses sans le
moteur : la mise à jour relit la structure entre l'outil et les ponts, le choix des pages retient
toute page du graphe encore sur le disque, et le bilan compte les arêtes sémantiques d'un titre
disparu sans compter celles d'un titre gardé.

Avec le moteur, absent du runner, la mesure se joue à la main : chaque identifiant que l'extraction
fraîche rend et que le graphe n'a pas doit être un identifiant que le moteur retire même redonné.
Elle rend 12 identifiants hors de cet ensemble sur le graphe d'avant, et aucun sur le graphe mis à
jour.
