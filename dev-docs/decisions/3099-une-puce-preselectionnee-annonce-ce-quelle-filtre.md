# ADR 3099 - Deux puces filtrent dès leur ajout, et c'est **acceptable parce qu'elles l'annoncent**

- **Statut** : Accepté - 2026-08-04
- **Chantier** : #3099, palier 4 du chantier #3092
- **Vérification** : humaine - la capture `apercu-sons-validation-filtres.png` montre la puce « Taxon parent » portant sa valeur (« Chiroptères ») sans qu'on la déplie ; c'est ce que l'argument repose sur, et aucun test ne peut l'établir à sa place.

## Contexte

Le socle des filtres pose une règle en premier : **rien de coché n'écarte rien**. Une puce fraîchement
ajoutée ne doit pas vider la vue avant qu'on ait choisi.

Sons & validation y déroge deux fois. « Statut » s'ouvre sur « À revoir », « Taxon parent » sur
« Chiroptères » quand ce groupe est présent. Le code assume l'entorse depuis #471 :

> seule entorse au principe « une puce ajoutée n'écarte rien » : c'est le geste même de l'écran, et
> s'ouvrir sur tout obligerait à filtrer avant de commencer.

L'inquiétude, ouverte pendant l'audit qui a lancé le chantier #3092, portait sur la **prévisibilité** :
ailleurs, une puce d'apparence identique reste neutre. On ajoute un critère et, selon lequel, la table
change ou ne change pas. Le nombre de lignes bouge sans qu'on ait rien choisi.

L'arbitrage a été **volontairement différé à la passe 8**, la revue visuelle, parce que la question
porte sur ce qui se voit et qu'un test ne pouvait pas y répondre.

## Ce que la revue visuelle a établi

La capture montre la puce ainsi :

```
[ Taxon parent | Chiroptères ▾ ]  ✕
```

**La valeur se lit sans déplier la puce.** Ce n'est donc pas le filtre qui est invisible : il est
annoncé, en toutes lettres, à l'endroit même où on l'a posé.

L'inquiétude visait la bonne chose - un filtre appliqué sans qu'on l'ait demandé - mais se trompait
sur ce qui manquait. Ce qui n'est pas dit, c'est que la valeur vient d'un **défaut** plutôt que d'un
choix ; or l'utilisateur qui vient d'ajouter la puce sait qu'il n'a rien choisi, et il lit ce qu'elle
applique.

## Décision

**Aucun changement de code.** Les deux présélections restent, et l'écart au principe est **assumé et
écrit** plutôt que corrigé.

Deux options ont été écartées :

- **signaler l'automatisme** (marquer la puce tant que la valeur vient du défaut) ajouterait un signal
  visuel à lire sur un écran déjà dense, pour désambiguïser une situation que l'utilisateur vient de
  créer lui-même ;
- **supprimer la présélection** imposerait de choisir « À revoir » puis « Chiroptères » à la main à
  chaque session, alors que c'est le geste le plus fréquent de l'écran. Uniformiser le code coûterait
  ici à l'usage.

## Conséquences

- La règle du socle reste **« rien de coché n'écarte rien »**, avec **deux exceptions nommées**, toutes
  deux sur Sons & validation, toutes deux justifiées par le geste de revue (#471) ;
- une puce présélectionnée **doit afficher sa valeur** : c'est la condition qui rend l'exception
  acceptable. Un critère qui filtrerait d'emblée **sans le montrer** serait, lui, un défaut ;
- les critères **booléens** ne relèvent pas de cette exception. Leur seule présence filtre parce qu'ils
  n'ont pas d'état neutre à offrir, et leur libellé le dit (`CritereBooleen`) ;
- un futur écran qui voudrait présélectionner devra justifier le geste **et** vérifier que sa puce
  annonce ce qu'elle applique.

## Ce que cette décision doit à la méthode

Elle a été prise sur une **capture**, pas sur une lecture de code. Le raisonnement d'origine était
défendable et menait à corriger quelque chose qui n'avait pas besoin de l'être : différer l'arbitrage
jusqu'à voir l'écran a évité un changement inutile sur le geste le plus fréquent de l'application.
