---
type: adr
title: "Bash disparaît du dépôt, et une tolérance est un délai daté"
status: stable
article: A3
chantier: "#5188 (chantier #5102)"
decided_at: 2026-09-03
verification: probable
enforced_by:
  - "scripts/adr/5188-corpus-shell.py"
ratchet: 50
inv_key: cliquet-corpus-shell
verified:
  - by: machine:suspects
    at: 2026-09-03
generated:
  by: "process:assistance-par-agents"
  at: 2026-09-03
---

# Bash disparaît du dépôt, et une tolérance est un délai daté

## Contexte

Le dépôt vise **deux langages principaux** : Java pour le code de production et son outillage
associé, Python pour l'outillage du projet. La dispersion n'aide personne : elle ajoute de la
complexité et rend impossible une surveillance uniforme.

**Cette règle n'existait dans aucun fichier.** Mesuré le 2026-09-03, en cadrant le dernier lot de
#5102 :

```
$ grep -rln "deux langages" --include='*.md' --include='*.yml' .   # hors node_modules
(aucun résultat)
```

Elle vivait dans le **corps de l'EPIC #5102**, et nulle part ailleurs : ni ADR, ni page de
`dev-docs/`, ni `AGENTS.md`, ni `CONTRIBUTING.md`. Un corps d'EPIC se ferme et devient de l'archive,
qu'on ne relit que si l'on sait déjà qu'elle existe.

Ce n'est pas un défaut de rangement : cette règle interdit d'écrire un contrat en shell, engage
tout auteur de garde futur, et porte une **condition de levée** qu'une règle introuvable ne lèvera
jamais. C'est la catégorie que le dépôt oublie le plus, parce qu'elle ne laisse pas de code derrière
elle : une **décision de ne pas faire**.

## Ce que le corpus pesait quand la décision a été écrite

Le lot #5187 a mesuré, en lançant plutôt qu'en lisant :

| | |
|---|---:|
| fichiers `.sh` que git suit | **50** |
| vus par le `find` du job `lint` | **50** |
| écart, comparé dans les deux sens | **0** |
| **gardes**, qui distinguent `--auto-test` d'un drapeau quelconque | 45 |
| **outils**, qui rendent la même chose sous les deux drapeaux | 5 |

La page [CI/CD et release](../ci-cd-release.md) annonçait alors **42**, juste le 2026-08-25 et
périmé depuis sans que personne le voie.

## Décision

**Bash disparaît du dépôt. Ce n'est pas négociable.**

Trois conséquences, et la troisième est celle qui se perd si on ne l'écrit pas.

**1. Les scripts shell se convertissent, ils ne se documentent pas.** Le format `--contrat` est
éprouvé en shell par #5009 et garde sa valeur pour l'état transitoire ; **y étendre le corpus serait
investir dans ce qui doit disparaître.** Un garde shell ne reçoit donc pas de contrat, ce qui a borné
le lot #5187.

**2. Le langage d'arrivée se choisit à la conversion**, entre Java et Python selon ce que le script
outille. Ce choix-là reste ouvert ; celui de rester en shell ne l'est pas.

**3. Une tolérance est un délai daté, jamais une exemption.** Là où le shell est toléré, il doit
**aussi** être remplacé. Une tolérance porte donc trois choses : ce qu'elle couvre, la condition de
sa levée, et le fait que sa levée **déclenche** la conversion plutôt que de la rendre possible.

Le dépôt en compte une : `.github/scripts/lance-test-filme.sh`, 1 295 lignes d'orchestration,
toléré **tant que le banc Java n'est pas définitivement validé**.

## Ce qui la tient

Un cliquet, `scripts/adr/5188-corpus-shell.py`, calé sur la mesure du jour : **50, polarité
descendante**.

**Il empêche la seule chose qui rendrait la cible inatteignable : qu'on ajoute du shell.** Un dépôt
qui convertit vingt scripts et en écrit vingt-deux n'avance pas, et rien ne le disait avant.

**Le script toléré est COMPTÉ**, pas retiré du cliquet. C'est la conséquence directe du point 3 : le
retirer ferait croire à une dispense. Sa condition de levée voyage avec lui, dans la sortie du garde.

**La cible est zéro**, ce qui distingue ce cliquet des autres : ceux-là bornent une dette qu'on
tolère, celui-ci compte une population qui doit disparaître entièrement.

Le niveau est `probable` et non `certaine` parce qu'aucun compte ne prouve qu'un script a été
**converti** plutôt que supprimé. Le cliquet borne la dette et rend la cible opposable ; il ne juge
aucune conversion, et c'est une relecture qui trie.

## Ce que cette ADR ne tranche pas

**La faille du bloc `run:`.** Rien n'empêche de déplacer du shell vers un bloc `run:` de workflow,
où ce cliquet ne le voit pas. Elle est nommée ici plutôt que recouverte, et se refermera si on la
constate : un garde écrit contre un abus qui n'a pas eu lieu se règle sur ce qu'on imagine.

**Le calendrier.** Le cliquet dit que le corpus ne remonte pas ; il ne dit pas à quelle vitesse il
descend.
