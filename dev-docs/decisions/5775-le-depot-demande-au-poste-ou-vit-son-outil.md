---
type: adr
title: "Le dépôt ne cherche pas un outil que le poste range où il veut : il demande au poste"
status: stable
article: A9
chantier: "#5775, lot du sous-chantier #5762 (12 arbres sur 23 nés sans leur outil)"
decided_at: 2026-10-03
verification: certaine
enforced_by:
  - "scripts/methode/prepare-l-environnement.py"
verification_note: "l auto-test éprouve le mécanisme par des bouchons : il voit qu aucun gestionnaire de version n est nommé, que `stdout` seul est lu, que le délai est transmis et que le poste n est interrogé qu après l échec du PATH. Ce qu il ne peut pas voir est un poste réel, puisque ce dispositif n a aucun gardien en CI, le runner installant tout lui-même. La promesse « un arbre neuf naît équipé » se vérifie donc à la main, en créant un arbre et en lisant son contenu"
relations:
  complete: ["5407-un-verdict-local-porte-sur-le-code-pas-sur-le-poste"]
verified:
  - by: machine:ci
    at: 2026-10-03
generated:
  by: "process:assistance-par-agents"
  at: 2026-10-03
---

# Le dépôt ne cherche pas un outil que le poste range où il veut : il demande au poste

## Contexte

L'ADR 5407 a décidé que le dépôt **pose** l'outil OpenSpec à la création d'un worktree, et son
`enforced_by` nomme `prepare-l-environnement.py`. Mesuré le 2026-10-03, sur les **23 arbres** du poste
de développement, **12 n'avaient aucun `node_modules`**. La décision était juste et inappliquée.

La cause n'était pas un oubli. Le crochet `post-checkout` lance bien ce script, qui tente bien
`npm ci`, et qui échoue : `npm` est introuvable dans le PATH d'un shell non interactif, parce que ce
poste range `node` sous un gestionnaire de version. L'échappatoire de l'ADR 5407, « chaque échec nomme
sa commande », tenait en lettre et c'est ce qui la rendait inopérante : la commande nommée était
`npm ci`, celle qui ne peut pas s'exécuter.

## Décision

**Le dépôt ne cherche pas l'outil, il demande au poste.** Quand le PATH ordinaire ne porte pas l'outil,
le script interroge le shell que le poste déclare, par `command -v`, et utilise le chemin absolu rendu.

Le poste **déclare déjà** où vit son outil, dans le profil de son propre shell, et cette déclaration
est tenue par son propriétaire. La mesure qui tranche : `/bin/sh -c` et `bash -lc` ne trouvent rien,
le second parce que le gestionnaire vit ici dans `.zshrc` ; le shell **déclaré par le poste**, en
interactif, rend le chemin absolu.

**Quatre propriétés font partie de la décision**, chacune avec son cas d'auto-test.

Le poste n'est interrogé **qu'après** l'échec du PATH ordinaire : le faire d'avance coûterait à chaque
arbre pour une question déjà résolue. **`stdout` seul** est lu, parce qu'un profil écrit sur l'erreur,
et celui de ce poste y écrit déjà une ligne. La ligne retenue est le **dernier chemin absolu
existant**, ce qui se vérifie au lieu de se supposer : un mot nu qui désigne un fichier du répertoire
courant est refusé. Et un **délai de garde** borne l'attente, parce qu'un profil interactif peut être
lent ou attendre un terminal, et qu'un crochet qui pend n'a pas de symptôme.

**Et le répertoire de l'outil résolu passe en tête du `PATH` de l'enfant**, parce que résoudre
l'outil ne suffit pas : `npm` est un script dont le shebang réclame `node`. Ce lot l'a appris en
échouant sur le poste réel, et c'est le défaut que #5774 venait de corriger côté message, qui mordait
ici le correctif. La prémisse est mesurée avant d'en dépendre : l'outil et son interprète sont voisins
dans le même `bin/`, ce que font les gestionnaires installant une distribution complète par version.
Un poste qui les séparerait obtiendrait l'échec nommé du lanceur, et non un silence.

## Ce que cette décision n'autorise pas

**Nommer un gestionnaire de version.** La liste est ouverte, et nommer l'un d'eux lierait le dépôt à un
poste. Un cas d'auto-test balaie le texte rendu contre cinq noms.

**Tenir une table de dispositions connues.** Elle vieillit, et le dépôt devrait la maintenir pour des
postes qu'il ne voit pas.

**Faire écrire un fichier non versionné** où le poste déclarerait ses outils. Ici il dupliquerait
une déclaration qui existe déjà, celle du profil du shell. **Le dépôt emploie pourtant cette forme
ailleurs**, et il faut le dire plutôt que de l'écarter : `.githooks/post-merge` lit
`graphify-out/.graphify_python`. La différence est que `graphify` n'est déclaré par aucun profil,
alors que `node` l'est par construction, puisque le gestionnaire de version qui l'installe écrit
dans ce profil. Le critère n'est donc pas la forme, c'est : **le poste déclare-t-il déjà cet outil
quelque part ?**

Avec `batterie.interprete()`, qui sonde une liste **fermée** de candidats connus du dépôt, cela fait
trois stratégies, et leur choix se décide par l'état de l'ensemble des emplacements plausibles :
fermé, on sonde ; déclaré par le poste, on demande ; ni l'un ni l'autre, le poste l'écrit.

## Conséquences

**Un compte juste cesse d'être jeté.** Ce script rendait toujours `0`, en se justifiant par « jamais un
motif de blocage ». La raison est bonne et ne couvre pas ce qu'elle justifiait : le crochet se protège
**déjà** lui-même, par `|| true` suivi d'un `exit 0`. La non-blocance est la propriété de l'appelant.
Le script rend donc `2` quand il n'a pas pu poser, ce que le dépôt distingue d'un `1`.

**Les modules déclarés n'entrent pas dans ce code** : un module absent de l'interprète courant n'est
pas un échec du dépôt à poser, et les compter ferait sortir `2` sur un poste sain.

**Ce que cette décision laisse ouvert.** Un poste dont le profil est muet sur son outil obtient un
refus qui le nomme, et rien de plus. C'est le bon comportement et ce n'est pas une pose : la famille
« non posable » de l'ADR 5407 reste ce qu'elle était.
