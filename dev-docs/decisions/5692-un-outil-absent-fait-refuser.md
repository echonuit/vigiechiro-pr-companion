---
type: adr
title: "Un outil externe absent fait refuser, il ne fait pas lever"
status: stable
article: A13
heuristiques:
  - "nielsen-9"
chantier: "#5584, lot #5692 ; étendue à git par #6213, lot #6058"
decided_at: 2026-10-14
verification: probable
enforced_by:
  - "scripts/adr/verifie_outils_absents_refusent.py"
ratchet: 61
verified:
  - by: machine:suspects
    at: 2026-10-14
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-14
---

# Un outil externe absent fait refuser, il ne fait pas lever

## Contexte

`subprocess.run(["gh", ...], check=False)` ne couvre pas un exécutable introuvable. `check=False`
parle du code de sortie ; quand l'outil n'est pas dans le `PATH`, `subprocess.run` lève un
`FileNotFoundError` avant qu'il y ait un code. Le dispositif s'arrête alors sur une trace de pile au
lieu de nommer une panne d'installation.

Ce dépôt distingue partout « je refuse de conclure », qui nomme sa cause et son geste, d'un plantage
qui nomme une ligne de Python. Le premier dit au lecteur que le refus ne parle pas de son diff ; le
second lui fait chercher un bug dans le garde.

L'issue #5692 l'a mesuré sur `gh` : six appels levaient, dont `_forge.liste_issues`, qui documentait
« gh absent est un REFUS et non une liste vide » et levait avant d'atteindre son propre test. Elle les
a corrigés et a livré le garde `verifie_outils_absents_refusent.py`, qui lit l'arbre de chaque fichier
suivi et refuse tout appel sans filet.

La décision n'a pourtant jamais été écrite ici. Trois scripts citaient « l'ADR 5692 », et le lien
menait à une page absente.

Et le garde ne regardait que `gh`, au motif que c'était le seul outil appelé avec un premier argument
littéral. C'était faux. Mesuré le 14 octobre 2026 sur `31cc02d7b4`, avec le prédicat du garde,
appel par appel : `gh` n'a plus aucun appel nu, `git` en a **61**, dans 25 fichiers (#6058).

## Décision

1. **Un outil externe absent fait refuser, il ne fait pas lever.** Un appel à un outil externe est
   protégé par un `try` qui attrape `OSError` (ou plus large) et rend un refus nommé, ou par une sonde
   `shutil.which` qui nomme l'absence avant d'essayer. Une sonde extraite dans une fonction appelée
   compte, un cran d'indirection et pas plus.
2. **La décision vaut pour `gh` et pour `git`**, les deux outils que le dépôt appelle avec un premier
   argument littéral. Un outil de plus entre dans `OUTILS` avec sa raison.
3. **`gh` est un invariant, `git` est tenu par un cliquet.** `gh` n'a aucun appel nu, et le premier
   refuse. Les 61 appels nus à `git` ne refusent pas d'un coup : le compte ne peut que descendre, et
   chaque lot qui touche un de ces fichiers peut le faire baisser.

Deux autres formes ont été écartées pour `git`. **Une liste nommée d'exemptions** aurait demandé de
justifier 25 fichiers un par un, et une liste vieillit sans le dire. **Une campagne** qui corrige les
61 appels aurait touché d'un coup `scripts/batterie.py`, que toutes les sessions lancent. Le porteur
a retenu le cliquet, comme le veut l'article A9.

## Conséquences

Un appel nu de plus à `git` fait rougir le garde : le cliquet monte seulement par décision, écrite
ici. Un appel protégé le fait descendre, et le garde demande alors de resserrer.

Un appel par couture, dont le premier argument n'est pas une liste littérale, n'entre pas dans la
population : la protection appartient au lanceur réel. `_portee.git()` en est un cas, compté une fois
à son site, quel que soit le nombre de ses appelants.

Les 61 appels se répartissent surtout dans `scripts/batterie.py` (9), `scripts/adr/verifie_gages_joues.py`
(7) et les deux outils de rendu de `.github/assets/` (11). Le garde lui-même en porte un, dans la
fonction qui liste les fichiers suivis.

## Vérification

Probable : le garde liste les appels nus à `git` et les confronte au cliquet de cette ADR, et il
refuse tout appel nu à `gh`. Son auto-test joue les deux régimes sur le verdict et non sur le calcul :
`git` au cliquet passe, un cran au-delà rougit, un seul `gh` nu rougit même quand `git` tient son
cliquet.

```bash
python3 scripts/adr/verifie_outils_absents_refusent.py
python3 scripts/adr/verifie_outils_absents_refusent.py --auto-test
```
