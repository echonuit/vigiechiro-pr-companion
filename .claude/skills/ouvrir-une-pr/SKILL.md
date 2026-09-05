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

**Trente gardes jugent le dépôt en CI hors de ce que `scripts/adr/rapport.py` balaie.** Il en
lance deux familles de son répertoire, `[0-9]*.py` et `loupe-*.py` ; tout le reste se lance à la
main, selon ce qu'on a touché, et rien ne se lance tout seul.

Le tableau ci-dessous ne prétend pas les nommer tous, et `scripts/methode/verifie-batterie-locale.py`
compte ceux qui manquent encore : son cliquet descend à mesure que cette page les rattrape. Ce que le
tableau doit, c'est la commande à lancer, pas l'inventaire.

| À lancer | Quand |
|---|---|
| `ruff format scripts .github/scripts .github/assets icone` **puis** `ruff check` sur les mêmes dossiers | dès qu'on touche du **Python**. « Le format » n'est pas une seule chose : `./mvnw spotless:apply` tient le Java, `ruff` tient le Python, et la version est épinglée dans `pyproject.toml` (`ruff==0.16.5`). Sans `ruff` installé, ce garde est le seul du tableau qu'on ne peut pas éprouver en local |
| `python3 scripts/adr/rapport.py` | **toujours, et en premier.** Tout le reste de ce tableau est son complément, et il se lit comme tel : le 2026-09-05, j'ai lancé le complément en sautant la base, et le cliquet de l'ADR 4477 a rougi en CI sur une ADR trop longue que la boucle aurait vue |
| `python3 scripts/adr/verifie_*.py` | dès qu'on touche une **ADR**, un **garde** ou une **javadoc** : douze gardes que `rapport.py` ne balaie pas, dont `verifie_contrats_tiennent.py`, qui confronte le seuil qu'un garde déclare **sur lui-même** au cliquet de son ADR |
| `python3 scripts/methode/matrice-constitution.py --verifie` **et** `python3 scripts/methode/matrice-ergonomie.py --verifie` | dès qu'une **ADR** est écrite ou modifiée. Il y a **deux** matrices engendrées depuis les en-têtes : la constitution et les heuristiques. Écrire une ADR qui porte un `nielsen-N` périme la seconde sans toucher la première |
| `python3 scripts/methode/couverture-relecture.py --marque <fichier>` | dès qu'une **javadoc** est touchée, tests compris |
| `./mvnw -B -o test-compile pmd:pmd` **puis** `python3 scripts/adr/4617-code-mort-et-zone-de-test.py` | dès qu'on **ajoute du code** : le cliquet refuse si `target/pmd.xml` manque, donc il ne dit rien en local |
| `./mvnw test -Dtest=DocumentationAJourTest` | dès qu'une **ADR** est écrite ou modifiée, et dès qu'un **chiffre** change dans une doc. Le déclencheur disait « un chiffre » seul jusqu'au 2026-09-05, où une ADR neuve a rougi en CI sur `l_entete_d_une_adr_porte_son_titre` sans qu'aucun chiffre ait bougé : ce test tient vingt et un invariants de documentation, pas un seul |

**Et selon ce qu'on a touché d'autre.** Ces gardes-là ne tiennent pas dans un tableau sans le rendre
illisible, et ils se déclenchent aussi nettement.

Si vous avez touché une **compétence** ou une page de `dev-docs/`, cinq gardes confrontent ce que la
prose prescrit à ce qui existe : `scripts/methode/concordances-du-cycle.py` relie commandes, passes
et compétences ; `scripts/methode/passes-citees-existent.py` et `scripts/methode/tests-cites-existent.py` refusent qu'une page cite
une passe ou un test absent ; `scripts/methode/verifie-renvois-competences.py` tient les renvois barre-oblique ;
`scripts/methode/mesure-registre.py` recompte les motifs éditoriaux. Et si la compétence vit dans `.agents/skills/`,
`scripts/methode/synchronise-adaptateurs.py` doit recopier la version canonique vers `.claude/skills/`, sans quoi les
deux divergent. Un garde de méthode écrit ou modifié appelle en plus
`scripts/methode/temoins-de-methode-non-decoratifs.py`, qui refuse un auto-test qui ne prouve rien.

Si vous avez touché un **changement OpenSpec**, quatre gardes le tiennent :
`scripts/methode/verifie-specs-valides.py` valide le corpus, `scripts/methode/verifie-adoption-openspec.py` refuse la
régénération accidentelle des compétences adoptées, `scripts/methode/verifie-sous-commandes-openspec.py` vérifie que
les invocations citées existent, et `scripts/methode/verifie-version-openspec.py` que la ligne de commande épinglée
est bien celle que les compétences déclarent. Les deux derniers ont besoin de l'outil épinglé :
`npm ci --prefix .github/openspec` d'abord, sinon ils refusent en le disant.

Si vous avez écrit ou modifié un **garde**, `scripts/methode/verifie-dependances-declarees.py` exige
qu'il déclare ce dont il a besoin. Si vous avez ajouté un **test qui écrit sur disque**,
`scripts/methode/compte-les-reliquats.py` compte ce que la suite laisse dans le dossier temporaire. Et avant
d'ouvrir la demande, `scripts/methode/verifie-controle-du-titre.py` éprouve le contrôle local du titre, celui-là même
qui vous évitera de la rouvrir.

**Les planchers vivent dans trois endroits chacun** : l'en-tête `floor:`, la balise du corps de l'ADR,
et celle du journal. N'en tenir qu'un laisse les deux autres mentir, et c'est arrivé :
`DocumentationAJourTest` a rougi sous #4646 sur deux balises restées en arrière, parce que le chiffre
y porte une espace insécable que le remplacement littéral manquait. `python3
scripts/methode/releve-les-planchers.py --ecrire` les tient d'un seul geste : il lit le verdict du
garde plutôt que de recopier une mesure, et préserve le séparateur qu'il trouve. Son jumeau pour les cliquets est
`python3 scripts/adr/resserre_cliquets.py`, et il se lance quand un cliquet **descend** : une marge
regagnée qu'on ne consigne pas se reperd en silence, et le chiffre vit dans plusieurs endroits à la
fois.

**Un plancher périmé refuse** (#4683). `a-relever` rend `1`, comme `perte` : la boucle des cliquets
de la CI l'attrape, et ne pas relever fait rougir. Ce n'est pas un oubli silencieux, c'est un refus.

**Un code de sortie ne suffit pourtant pas à lire un garde.** Les cinq loupes rendent `0` en
signalant, parce qu'elles observent sans juger ; et `rapport.py` nomme séparément les scripts dont il
n'a pas su lire le verdict. On lit donc ce qu'ils écrivent, pour ce qu'un code ne dit pas.

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
python3 .github/scripts/verifie_titre_pr.py "fix(passage): le pivot se relit"
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

## Si le chantier porte un changement OpenSpec, le corps nomme sa tâche

Un chantier qui a tranché sa spécification à l'ouverture porte un `tasks.md`, et ce lot en réalise
une ligne. **Le corps de la demande la nomme**, parce que c'est là qu'elle se relit : le fichier vit
dans la branche, la demande vit sur la forge, et c'est la forge qu'on interroge pour savoir ce qui a
été fait.

```
Réalise la tâche 3 du changement `emporter-une-nuit`.
```

`openspec status --change "<nom>" --json` rend les tâches et leur état quand on ne les a plus en tête.

**Et la tâche se coche dans le même commit que le travail.** C'est ce qui s'est produit pour
`emporter-une-nuit`, dont les dix-sept tâches ont été cochées par les commits qui les réalisaient,
sans que rien ne le demande. Un cochage différé est un cochage qui n'a pas lieu, et l'écart ne se
découvre alors qu'à l'archivage, quand `tasks.md` décrit un plan que le code a déjà dépassé.

**Sans changement actif, il n'y a rien à nommer**, et ce n'est pas un manque : la plupart des lots du
dépôt n'en portent pas.

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
| « Le garde est sorti en 0, tout va bien » | Les cinq loupes signalent en rendant `0` : elles observent sans juger |
| « Le titre me semble bon » | Il devient la ligne du CHANGELOG. Le script rend un verdict, la relecture rend un avis |
| « Je relis le titre, ça suffit » | Quatre titres relus ont rougi le même jour |
| « La typographie du corps, ça n'engage rien » | `corps-pr.yml` refuse quatre formes, et ce corps est publié dès qu'il part |
| « J'ai rebasé, la batterie de tout à l'heure vaut encore » | Le rebase périme les mesures qui dépendent du dépôt entier |
| « `Ferme #N` ferme l'issue » | Elle reste ouverte, et la PR fusionne verte |
