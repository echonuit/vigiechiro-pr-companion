---
type: adr
title: "La page qu'on lit avant de pousser nomme ce qu'il faut lancer, ou elle donne une fausse fin"
status: stable
article: A1
chantier: "#5006 (un garde ne déclare rien de ce qu'il est), lot #5258"
decided_at: 2026-09-05
verification: probable
enforced_by:
  - "scripts/methode/verifie-batterie-locale.py"
ratchet: 0
verified:
  - by: machine:suspects
    at: 2026-09-05
generated:
  by: "process:assistance-par-agents"
---

# La page qu'on lit avant de pousser nomme ce qu'il faut lancer

## Le contexte

La compétence `ouvrir-une-pr` porte une section « La batterie locale, et pourquoi elle ne se devine
pas ». C'est la page qu'on ouvre quand on croit avoir fini, et c'est sur elle qu'on s'appuie pour
décider qu'on peut pousser.

Sa phrase d'ouverture annonçait : « **Quatre** gardes rougissent en CI alors que la compilation, le
format et `scripts/adr/rapport.py` passent tous en local. » Mesuré le 2026-09-05 : **trente** gardes
jugent le dépôt en CI hors de ce que `rapport.py` balaie, et la page en nommait **trois**.

## Ce que l'écart a coûté

Deux allers-retours de CI dans la même demande, la PR #5255, qui closait le chantier #5065.

Le cliquet de l'ADR 4359 y descendait de 742 à 741. Les quatre sites connus corrigés, la boucle
`[0-9]*.py` et les loupes lancées, la matrice de la constitution vérifiée : tout vert.
`verifie_contrats_tiennent.py` a rougi en CI sur un **cinquième** site, le contrat que le garde
`scripts/adr/4359-javadoc-narratif.py` déclare sur lui-même.

Corrigé, repoussé. La même clôture écrivait une ADR portant `nielsen-1`, et la ligne du tableau qui
dit quoi lancer quand une ADR bouge ne nomme que `matrice-constitution.py`. Il y a **deux** matrices
engendrées depuis les en-têtes d'ADR : `matrice-ergonomie.py --verifie` a rougi à son tour.

Aucun des deux ne demandait de connaître le dépôt. Le second demandait seulement d'avoir écrit une
ADR.

## La décision

**Un garde que la CI lance pour juger le dépôt est nommé par la page de la batterie locale**, ou son
absence est comptée par un cliquet qui descend.

Le cliquet est posé à **zéro**, et le lot qui écrit cette décision l'y amène : les trente sont
nommés. Un cliquet plutôt qu'un invariant, parce que la différence n'est pas dans le nombre mais dans
ce qu'on doit faire pour le dépasser. Un garde qu'on ajouterait sans pouvoir le nommer ici resterait
possible, en **relevant le cliquet dans cette ADR**, c'est-à-dire en écrivant pourquoi. C'est le
contraire d'une exception qui passe en silence.

**Nommer par glob compte comme nommer.** La page écrit `scripts/adr/verifie_*.py` plutôt que douze
lignes : ce qui est dû au lecteur est la commande à lancer, pas l'inventaire.

**Et nommer se fait en prose autant qu'en tableau.** Onze des trente ne tenaient pas dans une cellule
sans la rendre illisible ; ils sont nommés dans les paragraphes qui suivent le tableau, groupés par ce
qui les déclenche. Le garde lit la page entière, parce que c'est ainsi qu'on la lit.

**La population s'arrête à `scripts/`, et la frontière est mesurée.** `.github/scripts/` porte
trente et un scripts lancés par les ateliers, dont `verifie_permissions.py`, `verifie_epinglage.py`
et `verifie_inventaires_ci.py`. Un seul y déclare un `CONTRAT`. Étendre la population appliquerait la
règle du contrat à un arbre qui ne la suit pas encore, et trente gardes en seraient exclus sans un
mot : le faux vert même que cette décision combat. La frontière tombera quand cet arbre déclarera,
ce qui est l'objet du chantier #5006.

**Le cliquet ne porte pas d'`inv_key`, et c'est délibéré.** Treize ADR en déclarent une, qui miroite
leur chiffre dans une balise du registre éditorial. Ce registre tient des **motifs d'écriture**, et ce
cliquet n'en est pas un. Surtout, la clôture qui a produit cette décision a passé sa matinée à
poursuivre le même chiffre dans cinq endroits : un nombre qui vit à un seul endroit ne dérive pas.

## Ce que la décision ne dit pas

**Elle ne construit pas la porte d'entrée unique.** L'EPIC #5006 mesure qu'il n'y en a aucune : ni
`Makefile`, ni `justfile`, et le `pre-commit` ne fait que Spotless. Une commande unique rendrait ce
garde presque vide, et c'est peut-être la bonne suite. Ce n'est pas cette décision-ci : celle-ci rend
la dérive **visible**, ce qui est le préalable pour savoir ce que la porte économiserait.

**Elle ne juge pas ce qui ne juge pas.** Trois règles dérivées bornent la population, et elles vivent
dans la docstring du garde plutôt qu'ici : une invocation `--auto-test` éprouve le garde et ne juge
pas le dépôt ; un script sans `CONTRAT` produit sans juger ; et ce que `rapport.py` balaie est déjà
couvert par la page, qui dit de le lancer.

Ces règles sont **dérivées et non énumérées**, parce qu'une liste d'exemptions à tenir à la main
serait exactement le défaut que cette décision corrige.

## Les alternatives écartées

**Corriger le compte à la main.** C'est ce qui a produit l'écart : le tableau était juste le jour où
il a été écrit. Il redeviendrait faux au prochain garde ajouté, et personne ne le saurait.

**Un tableau exhaustif.** Trente lignes de « à lancer / quand » ne se lisent pas, et une page qu'on
ne lit plus ne vaut pas mieux qu'une page fausse. D'où le glob et la prose, qui gardent la page à
hauteur de lecteur sans laisser d'angle mort à la machine.

**Écrire les noms sans leur chemin.** Le premier jet de cette page disait `tests-cites-existent.py`,
que le garde n'a pas reconnu. Il avait raison de ne pas le reconnaître : une page dont le travail est
de donner la commande à lancer doit la donner **copiable**, et un lecteur qui doit reconstituer
`scripts/methode/` n'est pas servi.

## Pourquoi l'article A1

« Aucune affirmation d'achèvement sans preuve fraîche. » Une page qui dit ce qu'il reste à lancer
**affirme un achèvement** : la lire jusqu'au bout, c'est conclure qu'on peut pousser. Cette
affirmation-là n'avait aucune preuve, et elle en a une désormais.

## La parenté

Sœur de l'ADR 4745, que `scripts/methode/tests-cites-existent.py` tient : là, une page de méthode
cite un test qui n'existe pas et met en main une commande verte sans avoir jugé. Ici, une page de
méthode omet un garde et donne une fin qui n'en est pas une. Les deux disent que la prose de méthode
est de la **prescription**, et qu'une prescription se confronte à la machine.
