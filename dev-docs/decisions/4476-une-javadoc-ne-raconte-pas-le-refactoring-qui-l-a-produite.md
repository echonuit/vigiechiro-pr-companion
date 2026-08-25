---
type: adr
title: "Une javadoc ne raconte pas le refactoring qui l'a produite"
status: stable
article: A30
chantier: "#4476 (report d outillage, chantier #4462)"
decided_at: 2026-08-25
verification: probable
enforced_by:
  - "scripts/adr/4476-javadoc-raconte-son-extraction.py"
ratchet: 62
verified:
  - by: machine:ci
    at: 2026-08-25
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-25
---

# Une javadoc ne raconte pas le refactoring qui l'a produite

## Contexte

Le dépôt sort régulièrement une classe d'un contrôleur devenu trop gros. La classe naît, et sa
javadoc dit d'où elle vient :

```
/// Extrait de [QualificationController] pour le garder sous le plafond de taille (PMD `NcssCount`).
```

C'est tout ce qu'elle dit. Le lecteur apprend l'histoire d'un refactoring ; il n'apprend pas ce que
la classe fait, ni quand l'appeler, ni ce qu'elle garantit.

Le motif 30 de la grille `humaniseur` nomme déjà le défaut - « la version précédente racontée » - et
lui donne ses lieux : l'historique de version, le journal des changements, la section des
alternatives écartées d'une ADR. La javadoc n'en fait pas partie.

## Le défaut, mesuré

Quarante-huit blocs de `src/main/java` portent, dans une même phrase, un verbe d'extraction et un
nom d'outil de mesure. Sur dix tirés au hasard, **neuf** racontent le refactoring et **un** énonce
une contrainte encore vivante.

La relecture exhaustive de la javadoc en a retiré huit et laissé les autres, faute d'un critère
écrit. Une règle appliquée sur le delta et pas sur le reste n'est pas une règle : c'est un goût qui
a frappé là où le regard passait.

## Décision

**Une javadoc dit ce que sa classe est, pas le geste qui l'a détachée.** Le plafond qui a motivé
l'extraction se retrouve dans l'historique du fichier ; ce que la classe fait ne se retrouve nulle
part ailleurs.

Le critère qui sépare le récit de la contrainte tient au temps du verbe :

| Forme | Ce qu'elle dit | Verdict |
|---|---|---|
| « Extrait de X **pour le garder sous** le plafond » | un refactoring passé | à réécrire |
| « Ce contrôleur **est** au plafond, vingt colonnes de plus le feraient dépasser » | l'état du jour, et une contrainte pour le prochain auteur | à garder |

Le second cas n'est pas une tolérance : c'est une information qu'aucun autre endroit ne porte, et
que le prochain auteur doit avoir avant d'ajouter une méthode.

**Le geste attendu n'est pas de supprimer la phrase.** Une classe dont on retire la seule phrase de
javadoc se retrouve muette, ce qui est pire. Le geste est de la remplacer par le contrat.

## Conséquences

Le cliquet a été posé à **48**, puis descendu à **0** dans la foulée : les quarante-huit blocs ont
été réécrits, chacun disant désormais ce que sa classe est. Le cliquet est donc un refus.

**Ce n'était pas le plan, et l'écart mérite d'être dit.** Le régime attendu était celui de l'ADR
[« Une convention typographique se tient par un cliquet, pas par un nettoyage »](2843-typographie-cliquet-plutot-que-nettoyage.md) :
une résorption par tranches, parce qu'un correctif d'un seul tenant rend illisible le `git blame` de
quarante-six fichiers. Deux faits ont fait pencher autrement. Ces fichiers venaient d'être touchés
par la relecture, donc leur `git blame` portait déjà un commit de ce chantier - la seconde passe ne
coûtait rien de plus. Et le geste n'est pas mécanique : chaque bloc demande de **lire la classe**
pour dire ce qu'elle fait, ce qui est le même travail que la relecture, sur les mêmes fichiers.

Le niveau reste `probable`, et il l'est par mesure et non par prudence : sur dix cas, un était une
contrainte légitime que le motif ne sait pas distinguer. Les deux cas rencontrés
(`ClientVigieChiro`, `OngletReglagesEmplacements`) ont été réécrits sans rien perdre - « une
constante plutôt que trois littéraux » dit la même chose que « extraite parce que PMD compte les
littéraux », sans raconter. Un humain tranche, extrait en main.

**Ce que le cliquet ne voit pas.** Le même récit sans nom d'outil - « extrait de `X` pour la
cohésion » - passe sous le motif. Il a été essayé : le verbe seul décrit aussi des gestes du
domaine (« le taxon extrait de la ligne Tadarida ») et rendait le relevé inexploitable. Le cliquet
tient donc la forme la plus fréquente, pas toutes.

## Alternatives écartées

**Interdire toute mention d'un outil de mesure en javadoc.** Elle ferait disparaître les contraintes
vivantes en même temps que les récits, et le prochain auteur n'aurait plus rien pour savoir que la
classe qu'il complète est au plafond.

**Compter le bloc entier plutôt que la phrase.** Mesuré : 58 blocs au lieu de 48, dont ceux qui
citent l'outil dix lignes plus bas pour dire où en est la classe aujourd'hui. Le cliquet punirait
alors la mention utile avec le récit.
