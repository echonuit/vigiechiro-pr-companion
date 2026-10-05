---
type: adr
title: "Le crochet de commit relit la structure des pages, en une extraction avec le code"
status: stable
article: A3
chantier: "#5941 (le crochet ne relisait pas les titres d'une page modifiée), lot 12 de #5812"
decided_at: 2026-10-05
verification: certaine
enforced_by:
  - "scripts/graphify/rebuild.py"
verified:
  - by: machine:ci
    at: 2026-10-05
relations:
  complete: ["5877-la-mise-a-jour-du-graphe-relit-la-structure-de-toutes-les-pages"]
generated:
  by: "process:assistance-par-agents"
---

# Le crochet de commit relit la structure des pages, en une extraction avec le code

## Le contexte

L'ADR 5877 a fait relire la structure de toutes les pages par `rebuild.py --mets-a-jour`, et lui
a fait dire ce qu'elle retire. Elle laissait un trou, qu'elle nommait : le chemin du crochet de
commit, `rebuild.py <fichiers>`, ne relisait que le code.

Une page dont un titre changeait gardait donc ses titres d'avant jusqu'à la prochaine mise à
jour complète, que personne ne lance à chaque commit. Le crochet se contentait de poser un
drapeau.

## Ce qui a été vu

Le 5 octobre 2026, sur une copie du graphe de référence : un titre renommé dans `docs/faq.md` et
une méthode ajoutée dans une classe, passés ensemble au chemin du crochet. La colonne « après »
a été jouée avec le moteur. La colonne « avant » est ce que les cas de l'auto-test ont montré
rouge : la page n'était pas extraite.

| | Avant | Après |
|---|---|---|
| le titre neuf est dans le graphe | non | oui |
| le titre d'avant y est encore | oui | non |
| la méthode ajoutée y est | oui | oui |
| énoncés de prose de la page | 29 | 29 |

## La décision

**Le chemin du crochet relit la structure des pages qu'on lui nomme, et d'elles seules.** Il dit
ce qu'il retire comme la mise à jour le dit : les titres partis, et les arêtes sémantiques qui y
étaient ancrées.

**Le code et les pages passent par une seule extraction.** La fusion part du graphe du disque et
écrit l'extrait que les ponts liront. Deux fusions de suite partiraient chacune du même graphe,
et la seconde effacerait ce que la première venait d'y mettre.

Le drapeau reste posé. Les titres d'une page sont à jour, sa prose ne l'est pas : elle demande
un lecteur, et `a-reextraire` continue de la nommer.

## Ce que cela ne couvre pas

Les pages que le commit ne touche pas. Leurs titres ne bougent pas non plus.

Le crochet est sur option, par `VIGIECHIRO_GRAPHIFY=1`. Sans elle, rien ne se met à jour au
commit, et la mise à jour complète reste le geste.

## Les alternatives écartées

- **Appeler la relecture complète depuis le crochet.** Elle relit 776 pages à chaque commit pour
  en mettre une à jour.
- **Deux extractions, le code puis les pages.** C'est la forme qui vient d'abord, et elle perd le
  code relu : la raison est dite plus haut.

## Comment on le sait

L'auto-test de `scripts/graphify/rebuild.py`, lancé par `lint.yml`, joue le chemin avec un faux
moteur : le code et la page partent dans la même extraction, une page seule fait relire sa
structure, et le journal dit le titre retiré et les deux arêtes qui tombent avec lui. Une page
que l'extraction rend sans qu'on la lui ait nommée ne compte pas.

Ce que le moteur fait de cette extraction, absent du runner, se rejoue à la main sur une copie
du graphe.
