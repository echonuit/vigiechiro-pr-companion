---
name: ouvrir-une-pr
description: Use when the work is ready and a pull request is about to be opened. Covers the battery that must run locally because CI cannot be its own first reader, the title and body traps that only the forge refuses, and the watch that starts the moment the branch is pushed.
license: GPL-3.0-or-later
metadata:
  langue: fr
  origine: dev-docs/cycle-de-chantier.md
---

# Ouvrir une pull request

## Loi d'airain

```
UNE PR EN VOL SE SURVEILLE, OU ELLE N'EST PAS OUVERTE
```

Dire « la PR est en vol » sans moniteur, c'est attendre en espérant. Le fil se poursuit, le verdict
tombe sans lecteur, et la session suivante hérite d'une branche dont personne ne sait ce qu'elle vaut.

Ce n'est pas un défaut rare : les sessions **disent** attendre la CI et ne le font pas.

## Annoncer

« J'utilise la compétence ouvrir-une-pr pour ouvrir la PR de #N et la surveiller. »

## Fonction de garde

```
1. METTRE AU NET le corps de la PR, ICI. Celui de l issue attend la fusion.
2. LANCER   la batterie locale, selon ce qui a ete touche.
3. REBASER  sur `origin/main`, puis RELANCER ce que le rebase peut avoir perime.
4. EPROUVER le titre AVANT `gh pr create`.
5. ECRIRE   le corps en evitant les quatre refus que seule la forge rend.
6. POUSSER, ouvrir, puis LANCER LE MONITEUR dans le meme geste.
```

L'étape 6 est un seul geste, pas deux. Une PR ouverte sans moniteur est le cas que cette compétence
existe pour empêcher.

**L'étape 1 ne délègue pas** (#4731). Elle disait « les deux corps, par `clore-une-issue` », qui
s'emploie **après** la fusion depuis #4722 : la première étape de l'ouverture envoyait à la
compétence de la fermeture. Les deux se renvoyaient alors le corps de la PR, chacune désignant
l'autre, si bien que personne ne le mettait au net. Il se met au net ici, avec le titre ; le corps de
l'**issue** attend `clore-une-pr` puis `clore-une-issue`.

Un garde le tient désormais : `scripts/methode/etapes-sans-renvoi-aval.py` refuse qu'une **étape
numérotée** nomme une compétence située plus loin dans le cycle. La prose reste libre de nommer la
suite - c'est même ce qu'on attend d'elle.

## La batterie locale, et pourquoi elle ne se devine pas

Quatre gardes rougissent en CI alors que la compilation, le format et `scripts/adr/rapport.py`
passent tous en local. Chacun se lance selon ce qu'on a touché, et aucun ne se lance tout seul.

| À lancer | Quand |
|---|---|
| `python3 scripts/methode/matrice-constitution.py --verifie` | dès qu'une **ADR** est écrite ou modifiée |
| `python3 scripts/methode/couverture-relecture.py --marque <fichier>` | dès qu'une **javadoc** est touchée, tests compris |
| `./mvnw -B -o test-compile pmd:pmd` **puis** `python3 scripts/adr/4617-code-mort-et-zone-de-test.py` | dès qu'on **ajoute du code** : le cliquet refuse si `target/pmd.xml` manque, donc il ne dit rien en local |
| `./mvnw -o test -Dtest=DocumentationAJourTest` | dès qu'un **chiffre** change dans une ADR ou une doc |

**Les planchers vivent dans trois endroits chacun** : l'en-tête `floor:`, la balise du corps de l'ADR,
et celle du journal. N'en tenir qu'un laisse les deux autres mentir.

**Un plancher qui dit « à relever » sort en `0`.** On lit ce que les gardes écrivent, on ne compte pas
leurs codes de sortie.

## Le rebase périme une partie de la batterie

`main` bouge pendant qu'on travaille. Après un rebase, les mesures qui dépendent du dépôt entier -
planchers, cliquets, matrices - **se relancent**, et le `target/` nettoyé par les hooks fait rougir le
cliquet 4617 tant que PMD n'a pas retourné.

Un conflit sur un inventaire ne se résout pas « en prenant le plus grand ». Un **cliquet** qui descend
est un gain que quelqu'un a payé, donc on prend le sien ; un **plancher** prend la mesure réelle de la
branche fusionnée.

## Le titre se vérifie avant l'ouverture, pas après

`titre-pr.yml` refuse un titre non conforme, et il le fait bien : les 100 dernières PR fusionnées
suivent toutes la convention. Il ne peut mordre qu'une fois la PR ouverte, et cela coûte une PR à
ré-éditer puis une vérification à relancer. Le même script tourne en local, sur la chaîne que vous
vous apprêtez à taper :

```bash
./.github/scripts/verifie-titre-pr.sh "fix(passage): le pivot se relit"
```

Le défaut n'entre pas au commit. Les quatre PR rouges du 2026-08-26, #4570, #4588, #4589 et #4591,
partaient toutes d'une branche aux sujets de commit conformes, et 286 des 297 sujets de branche
hors `main` le sont aussi. Trois de ces quatre titres sont le sujet du commit retapé avec ses
accents, le quatrième est le titre de l'issue #4574 recopié tel quel. Le défaut entre à la frappe
du titre : en écrivant du français correct, la main applique la typographie française, et l'espace
avant le deux-points que Conventional Commits interdit vient avec.

Ce qui décide de la frappe est le nombre de commits. `gh pr create --fill` reprend le sujet quand la
branche n'en porte qu'un, et titre la PR avec le **nom de branche** au-delà, forme que le garde
refuse. Les quatre PR rouges avaient toutes trois commits ou plus : leur titre ne pouvait pas être
rempli, il devait être écrit.

La règle est dans `CONTRIBUTING.md` depuis longtemps, avec sa raison et son coût. Les quatre PR
l'ont manquée le même jour. Le script, lui, rend un verdict.

Le 2026-08-28 a ajouté une cinquième forme, du même geste : une **élision sans apostrophe**,
`d accuser` pour `d'accuser`. La main qui évite l'espace avant le deux-points peut encore buter sur
l'apostrophe, et le script les refuse toutes les deux.

## Les quatre refus que seule la forge rend

Aucune batterie locale ne lit le corps ni le titre d'une PR : ils ne sont dans aucun fichier. Le
workflow `corps-pr.yml` les refuse, et c'est trop tard pour les découvrir.

| Refus | Ce qu'il faut écrire |
|---|---|
| fermeture en français | `Closes #N`, jamais « Ferme #N » |
| tiret cadratin | un trait d'union, jamais `—` |
| apostrophe courbe | l'apostrophe droite |
| élision sans apostrophe | `d'accuser`, jamais `d accuser` |

**Le premier est le plus coûteux, et il ne se voit pas.** Une fermeture écrite en français ne ferme
rien et ne signale rien : la PR fusionne **verte** et l'issue reste **ouverte**. Vécu sur #4660.

Pour renvoyer sans clore - un lot dans un EPIC - `Refs #N` ou « Rattaché à #N », qui ne prétendent
rien.

## Le moniteur, dans le même geste que l'ouverture

Ce qu'il surveille : un rouge, et la fin des vérifications. Ce qu'il ne fait pas : conclure à votre
place.

Deux pièges vécus :

- `gh pr checks` **sort non nul** dès qu'une vérification échoue. Un moniteur qui s'arrête sur ce code
  meurt à chaque rouge, c'est-à-dire exactement quand il servait ;
- l'état `skipping` n'est **pas** un échec. Un filtre qui le compte comme tel crie sur des PR saines.

On ne surveille que `FAILURE`, `CANCELLED` et `TIMED_OUT`, et on sort en `0` quel que soit le verdict :
c'est le message qui informe, pas le code de sortie du moniteur.

Quand le verdict tombe, [`clore-une-pr`](../clore-une-pr/SKILL.md) prend la suite : ce qui juge
vraiment ce changement, ce qu'un rouge vaut, et l'issue mère qui ne se ferme pas toute seule.

## Signaux d'alerte : on s'arrête

| Pensée | Réalité |
|---|---|
| « La PR est en vol, j'attends » | Attendre sans moniteur, c'est espérer. Le verdict tombe sans lecteur |
| « Les tests passent en local, la CI passera » | Quatre gardes ne tournent qu'en CI, et deux ne lisent que le corps de la PR |
| « Le garde est sorti en 0, tout va bien » | Un plancher qui dit « à relever » sort en 0 |
| « Le titre me semble bon » | Il devient la ligne du CHANGELOG. Le script rend un verdict, la relecture rend un avis |
| « Je relis le titre, ça suffit » | Quatre titres relus ont rougi le même jour |
| « La typographie du corps, ça n'engage rien » | `corps-pr.yml` refuse quatre formes, et ce corps est publié dès qu'il part |
| « J'ai rebasé, la batterie de tout à l'heure vaut encore » | Le rebase périme les mesures qui dépendent du dépôt entier |
| « `Ferme #N` ferme l'issue » | Elle reste ouverte, et la PR fusionne verte |
