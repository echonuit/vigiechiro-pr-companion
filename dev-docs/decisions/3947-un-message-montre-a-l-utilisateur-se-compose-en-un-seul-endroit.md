# ADR 3947 - Un message montré à l'utilisateur se compose en un seul endroit, et ce n'est pas une vue

- **Statut** : Accepté - 2026-08-18
- **Chantier** : #3947, passe 7 de la clôture des finitions de recette (#3424)
- **Prolonge** : [ADR 3470](3470-un-message-d-erreur-ne-montre-jamais-le-nom-de-son-enveloppe.md)
- **Vérification** : probable - `scripts/adr/3947-message-enveloppe.py` (cliquet : 2)

## Contexte

L'[ADR 3470](3470-un-message-d-erreur-ne-montre-jamais-le-nom-de-son-enveloppe.md) a posé qu'un message
d'erreur ne montre jamais le nom de son enveloppe, et a écrit `CauseLisible` pour le tenir. Elle n'a
aligné qu'**un** appelant : le filet global de l'IHM.

L'audit de la passe 7 en a trouvé **huit autres**, en trois formes, dont le filet global de la ligne de
commande. Aucun ne parcourt la chaîne de causes, si bien que le message **fabriqué** par une enveloppe
y passe tel quel.

## Ce que la mesure a démenti, et qui a changé la forme du remède

L'obstacle supposé était une règle d'architecture : `CauseLisible` vivait dans `commun.view`, donc la
ligne de commande ne pouvait pas l'appeler.

⚠️ **C'était faux.** La règle `ArchitectureTest.pas_de_dependance_inter_feature_vers_la_vue` **exclut
explicitement le socle `commun`**. Rien n'aurait rougi.

Ce qui faisait tenir le montage n'était pas une propriété d'architecture, c'était un **détail de
compilation** : `ActionOuvrirJournaux.LIBELLE` étant une constante `static final` de type `String`,
javac l'**inline** sur son site d'appel. `CauseLisible` ne chargeait donc jamais la classe de vue à
l'exécution. Retirer le `final`, ou remplacer le littéral par un appel, aurait fait charger JavaFX
depuis la CLI - sans que rien ne le signale.

Le déplacement reste juste, pour cette raison-là et non pour celle qu'on croyait.

## Décision

**1. `CauseLisible` vit dans `commun.model`.** Elle ne dépend d'aucun nœud JavaFX ; c'est une règle de
composition de texte, pas une vue.

**2. La constante du libellé de menu s'inverse : elle vit côté modèle, la vue la cite.** C'est
l'inverse de ce que #3470 avait posé, et c'est le point qui rend le déplacement tenable. L'ADR 3854
(« un message qui renvoie vers une entrée de menu doit la nommer telle qu'elle s'écrit ») tient
toujours par construction, puisque la citation reste un lien de compilation.

**3. ⚠️ Le repli « où regarder » est un paramètre, pas une constante.**

C'est la décision qui a le plus failli manquer. Le repli de #3470 dit :

> …dans le journal de l'application (**menu principal > Ouvrir le dossier des journaux**).

Une ligne de commande n'a pas de menu principal. Faire appeler `messageDe` par la CLI sans toucher à ce
repli aurait mis une consigne **inapplicable** dans une sortie de terminal, et **le défaut n'aurait
rien fait rougir** : le message serait resté non vide, donc d'apparence correcte.

C'est exactement la forme du défaut que l'ADR 3470 combat - un texte exact et sans valeur - déplacée
d'un cran par sa propre correction. Chaque surface passe donc son `OU_REGARDER_*`.

**4. Le compte se cliquette sur ce qui est décidable, pas sur tout.**

Le script compte les **trois formes** que l'ADR 3470 nomme, et elles seules : repli sur `toString()`,
repli sur le nom de classe, déroulement d'un seul cran. Un `"Échec : " + echec.getMessage()` **nu** est
tout aussi fautif, mais il est indiscernable d'un refus métier légitime, dont le message est écrit par
nous et n'est jamais nul.

Compter la forme nue aurait rendu un chiffre que personne ne sait faire descendre, c'est-à-dire un
cliquet qu'on apprend à ignorer.

## Conséquences

- Huit sites composaient leur message à la main ; **six sont alignés**, et les deux restants
  (`RestaurationBase:172`, `MoteurTraitementGroupe:116`) sont **comptés par le cliquet** plutôt que
  corrigés en silence hors du périmètre soumis.
- ⚠️ Le cliquet **ne peut pas voir** la forme nue. Son en-tête le dit, plutôt que d'emprunter la
  solidité du voisin ([ADR 3540](3540-un-cliquet-qui-compte-n-est-pas-la-preuve-de-la-regle.md)).
- Le script s'**exclut de son corpus** : `CauseLisible` porte les trois motifs dans sa documentation et
  se compterait elle-même, défaut mesuré par l'[ADR 3645](3645-un-detecteur-textuel-s-exclut-de-son-corpus.md).

## Alternatives écartées

- **Laisser `CauseLisible` dans `commun.view` et l'appeler depuis la CLI.** Aucune règle ne l'interdit,
  et ça marcherait aujourd'hui. Mais la raison pour laquelle ça marche est invisible, et sa
  contradiction ne produirait pas une erreur de compilation : elle chargerait JavaFX dans un processus
  headless, à l'exécution, au pire moment.
- **Un seul repli, celui de l'IHM, pour les deux surfaces.** Voir le point 3 : un message qui désigne un
  menu à qui n'en a pas est un texte exact et sans valeur.
- **Compter toutes les compositions à la main.** Voir le point 4 : un chiffre qu'on ne sait pas faire
  descendre n'est pas un cliquet.
