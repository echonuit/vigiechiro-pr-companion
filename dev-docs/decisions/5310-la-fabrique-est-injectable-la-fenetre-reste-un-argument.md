---
type: adr
title: "La fabrique de désignation est injectable, et la fenêtre reste un argument de méthode"
status: stable
article: A9
chantier: "#5282 (convergence des deux bancs filmés), lot #5310"
decided_at: 2026-09-06
verification: certaine
enforced_by:
  - "scripts/adr/5307-designation-hors-fabrique.py"
verified:
  - by: machine:ci
    at: 2026-09-06
generated:
  by: "process:assistance-par-agents"
---

# La fabrique de désignation est injectable, et la fenêtre reste un argument de méthode

## Contexte

L'[ADR 5307](5307-le-dispositif-de-designation-se-choisit-en-un-endroit.md) a posé `Selecteurs`, le
seul endroit où le dispositif de désignation se construit, en écrivant « une fabrique, et non une
liaison dans l'injecteur », parce que l'injecteur ne connaît pas la fenêtre de l'écran.

Le réglage qui tranche vit dans `Reglages`, au-dessus de `app_setting`. La fabrique doit donc le
lire, et elle était **statique**. Mesure du 2026-09-06 : **aucun** des douze écrans appelants n'a
`Reglages` injecté. Le leur passer aurait élargi douze constructeurs pour une préférence qu'aucun n'a
à connaître : ils demandent un sélecteur, pas un réglage.

## La première conception, et les 293 tests qui l'ont refusée

Ce lot a d'abord donné `Reglages` à la fabrique. Cela compilait, et **293 tests dans 27 classes sont
devenus rouges** : `Reglages` vient de `PersistenceModule`, donc construire un écran exigeait une base
de données. Les tests de vue bâtissent un injecteur MINIMAL, et aucun n'avait de persistance.

Le couplage ne se voyait ni à la compilation ni en relecture : la fabrique recevait **un**
collaborateur, ce qui paraît anodin, mais celui-ci traîne une table SQL derrière lui.

**Le remède n'était pas de corriger 27 classes**, qui disaient que la dépendance est au mauvais
endroit. La fabrique reçoit [PreferenceDesignation], un port portant **la seule chose que la vue a
besoin de savoir**, avec un défaut `@ImplementedBy` constructible sans rien - le patron de
`DepotDispositionColonnes`, employé huit fois ici. Les 88 tests des trois classes les plus touchées
repassent au vert **sans qu'aucune n'ait été modifiée**.

## Décision

**La fabrique devient un objet injectable. Sa fenêtre reste un argument de méthode.**

`Selecteurs` reçoit [PreferenceDesignation] par `@Inject` ; `pour(fenetre)` garde sa signature.

**Cette décision ne contredit pas l'ADR 5307, elle la précise.** Ce que la 5307 refuse de lier, c'est
[SelecteurFichier], dont le choix dépend d'une fenêtre inconnue de l'injecteur. La fabrique, elle,
reçoit cette fenêtre par sa **méthode**. La raison de 5307 tient donc mot pour mot, et c'est ce qui
permet d'y ajouter sans la défaire.

Une ADR ne se réécrit pas. Celle-ci existe pour que le lecteur qui trouve les deux côte à côte ne
conclue pas à une contradiction.

## Le réglage se lit au moment de DÉSIGNER, pas au moment de construire

Première version : `pour(fenetre)` résolvait le dispositif tout de suite, alors que les écrans
appellent la fabrique dans leur **constructeur**. Deux défauts d'un geste : basculer l'interrupteur ne
changeait rien avant un redémarrage, et construire un écran touchait `app_setting`.

C'est `ordre-alternatif` qui l'a dit, pas la batterie locale : le test qui rougit ne cite aucune
classe du lot, il monte l'injecteur **de production**. Un lot qui change une liaison Guice se mesure
donc sur ce corpus-là, et non sur les classes qu'il touche.

La résolution vit maintenant dans `SelecteurSelonLaPreference`, et **le garde de la 5307 l'a suivie
sans s'élargir** : l'endroit permis a changé, il n'y en a pas deux. Autoriser les deux aurait fait du
garde une liste, et une liste s'allonge.

## Conséquences

**Ce qu'on gagne.** Le réglage se lit à un endroit, et aucun écran ne connaît `Reglages`. Le garde de
l'ADR 5307 tient sans changement : il refuse toujours `new SelecteurFichierJavaFx` hors de la
fabrique, et le câblage l'alimente au lieu de le contourner.

**Ce qu'on paie.** Le paramètre traverse **deux niveaux** avant d'atteindre un `@Inject`, par
`PorteurSauvegarde` et `GestesEmportQualification`, construits à la main. Trente fichiers touchés.

**Une mesure qui s'est révélée fausse.** Le coût avait d'abord été estimé en comptant les appelants
**directs** : un chacune, donc « aucune cascade ». Rassurant, et faux. La propagation d'un paramètre de
constructeur se mesure en remontant jusqu'à un `@Inject`, seul endroit où la chaîne s'arrête.

**Le défaut vit à côté de la clé** : `ReglageDesignation.CLE` et `ReglageDesignation.DEFAUT`, comme
`ReglageConservationOriginaux` déjà. Un défaut recopié dans l'écran des réglages et dans le lecteur
est une divergence qui attend son tour, et rien ne signale l'oubli.

**Une vue ne demande pas « les réglages », elle demande UNE préférence.** C'est la règle que cet
échec a fait écrire, et `PreferenceConservation` la suivait déjà pour l'import. Elle est désormais
gardée par ArchUnit : aucune classe de `..view..` ne dépend de `commun.model.Reglages`. La règle
partait de **zéro** une fois ce lot corrigé - une seule classe de vue l'enfreignait, la mienne.

**Pourquoi pas une règle transitive.** La règle existante, `view_sans_jdbc`, interdit à la vue de
toucher `model.dao` ou `java.sql` - en DIRECT. Mon défaut était transitif d'un cran, et la rendre
transitive la ferait rougir partout : un écran dépend d'un ViewModel, qui dépend d'un service, qui
dépend d'un DAO, et c'est toute l'architecture. Une règle qui rougit sur tout n'apprend rien. La
règle utile ne porte donc pas sur la persistance mais sur le **service de réglages**.
