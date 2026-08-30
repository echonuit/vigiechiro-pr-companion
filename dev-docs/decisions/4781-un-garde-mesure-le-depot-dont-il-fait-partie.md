---
type: adr
title: "Un garde mesure le dépôt dont il fait partie, pas celui du répertoire courant"
status: stable
article: A3
chantier: "#4781 (EPIC #4803)"
decided_at: 2026-08-30
verification: certaine
enforced_by:
  - "scripts/adr/rapport.py"
  - "scripts/adr/verifie_corpus_declare.py"
verified:
  - by: machine:ci
    at: 2026-08-30
generated:
  by: "process:assistance-par-agents"
  at: 2026-08-30
---

# Un garde mesure le dépôt dont il fait partie, pas celui du répertoire courant

## Contexte

La compétence `worktree` interdit de travailler dans la copie principale, et le dépôt en porte une
dizaine d'exemplaires à la fois. Lancer un garde par son chemin absolu depuis un shell posté ailleurs
est donc le geste courant, pas un cas de bord.

## Le défaut, mesuré et non supposé

Trois constantes de chemin étaient écrites relativement et employées seules. Le verdict portait alors
sur le dépôt du shell, sans que rien ne le dise :

```
verifie_corpus_declare.py, lancé depuis un AUTRE worktree
  → code 0, « ADR 4586 | suspects=0 | verdict=ok »
```

Le cas où le garde ne trouve rien refuse franchement. C'est celui où il trouve **un autre dépôt** qui
trompe : les fichiers existent, la mesure aboutit, et le verdict est faux.

Plus grave, `rapport.py` lançait chaque garde par `subprocess.run` sans `cwd`, donc tous héritaient du
répertoire de l'appelant. Depuis `/tmp`, **cinq cliquets sur dix-huit** rendaient une autre valeur,
dont quatre tombaient à zéro faute de trouver quoi que ce soit. Et `resserre_cliquets.py` agit sur ces
verdicts et **écrit** : éprouvé, il a ramené quatre champs `ratchet:` à zéro dans les vraies ADR, en
annonçant un succès et en sortant 0.

## Décision

Un garde résout ses chemins depuis son propre emplacement, et un lanceur impose ce répertoire à ce
qu'il lance. Le chemin **affiché** reste relatif : c'est la recherche qui s'ancre, pas le rapport.

Une constante reste relative quand elle paraît dans un rapport, un chemin absolu y étant illisible et
différent d'une machine à l'autre. `PRODUCTION` et `TESTS` sont dans ce cas ; `DECISIONS` ne l'est
pas, et elle est ancrée d'emblée.

## Pourquoi le lanceur plutôt que chaque garde

Cinq corrections séparées auraient laissé passer la sixième. Une ligne dans `rapport.py` couvre les
cinq cliquets concernés et le garde qu'on écrira demain, là où l'énumération dérive - c'est la leçon
que l'ADR 4586 tire déjà pour les corpus.

## Ce que cette ADR ne mécanise pas

Rien ne refuse un garde lancé **à la main** depuis un autre répertoire s'il emploie une constante
relative sans l'ancrer. C'est la convention, écrite dans `_commun.py`, et non un garde. Et la portée
du garde du corpus s'arrête à `scripts/adr/` : sept sites recopient un corpus hors de lui, dont un
sans aucune ancre (#4836).
