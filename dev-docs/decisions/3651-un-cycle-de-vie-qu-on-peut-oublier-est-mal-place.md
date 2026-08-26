---
type: adr
title: "Un cycle de vie qu'on peut oublier est mal placé"
status: stable
article: A22
chantier: "#3651, passe 7 de la clôture du lot 4 (#3580)"
decided_at: 2026-08-13
verification: certaine
enforced_by:
  - "NavigateurTest#relibeller_ne_reabonne_pas"
verified:
  - by: machine:ci
    at: 2026-08-13
relations:
  prolonge: ["3580"]
---

# Un cycle de vie qu'on peut oublier est mal placé

## Contexte

Le lot 4 a branché cinq écrans sur la révision des données. Chacun portait le même triplet : un
champ `ChangeListener`, un `addListener` dans `initialize()`, un `removeListener` dans
`auDepartEcran()`.

La duplication n'était pas le problème - quinze lignes sur cinq fichiers se supportent très bien.
Le problème est que **les trois lignes ne se valent pas** :

- l'écouteur en champ et la pose sont **visibles** : les oublier, c'est un écran qui ne se
  rafraîchit jamais, et le premier essai le montre ;
- le retrait est **invisible** : l'oublier ne casse rien de perceptible. `RevisionDonnees` est un
  singleton, un écran ne l'est pas ; l'écoute survit à la vue et fait recharger un écran que plus
  personne ne regarde. Sur dix ouvertures, dix rechargements par mutation, dont neuf pour personne.

C'est une obligation dont l'oubli ne produit **aucun signal** dans le cas nominal, et dont le coût
croît avec l'usage. Écrite cinq fois à la main, elle sera écrite une sixième par quelqu'un qui
copiera les deux premières lignes et pas la troisième.

## Décision

**Le `Navigateur` porte le cycle de l'abonnement ; l'écran ne déclare que son intention.**

Un contrat de plus, `SuitLaRevision`, dérivé par `instanceof` comme les quatre autres contrats
optionnels. Le `Navigateur` pose l'abonnement quand l'étape entre dans l'historique et le rend quand
elle en sort, dans le **même** écouteur de liste qui appelait déjà `auDepartEcran()`.

Un écran fournit `rafraichirDepuisLaDonnee()` et rien d'autre : ni champ, ni écouteur, ni
`RevisionDonnees` au constructeur.

Le raisonnement n'est pas « factoriser cinq copies » mais **« placer une obligation là où on ne peut
pas l'oublier »**. Le chrome possédait déjà le cycle de vie des écrans ; celui-ci lui revenait.

## Le piège, qui n'est pas évident

L'abonnement se repère par la **vue**, jamais par l'étape.

`EtapeNavigation` est un `record` remplaçable : `actualiserLibelleCourant` (#1213) remplace l'étape
courante par sa jumelle relibellée quand un chargement asynchrone livre enfin le numéro du passage,
et un `setAll` retire puis replace l'accueil. Dans les deux cas, `wasRemoved()` **et** `wasAdded()`
sont vrais alors que **l'écran n'a pas bougé**.

D'où deux propriétés qui ne sont pas défensives mais nécessaires :

- la pose est **idempotente** : sans elle, relibeller ré-abonne, et un seul import provoque deux
  relectures de la base (`NavigateurTest#relibeller_ne_reabonne_pas` mesure exactement ça, « expected:
  1 but was: 2 ») ;
- le retrait ne se déclenche que si **plus aucune étape** ne porte cette vue - la garde
  `vuePresente` que le hook `AuDepartEcran` utilisait déjà pour la même raison.

## Conséquences

- `RevisionDonnees` **sort d'`AppuisPassage`**, où #3626 venait de la faire entrer pour tenir le
  plafond `ExcessiveParameterList` de `PassageController`. La marge d'arité reprise là est rendue.
- **Les tests d'écran doivent empiler leur vue dans un `Navigateur`** pour que l'abonnement existe.
  C'est plus lourd, et c'est le prix juste : un garde qui appelait `controleur.auDepartEcran()` à la
  main vérifiait que la méthode fait ce qu'elle dit, pas que quelqu'un l'appelle.
- Corollaire mesuré à la même occasion : un écran **quitté** sort du graphe de scène, donc
  `robot.lookup` ne le voit plus. Un test qui interroge la fenêtre après un départ compte zéro nœud
  et se croit vert pour la mauvaise raison. Les requêtes partent de la vue (`robot.from(vue)`).
- Un écran **masqué mais toujours dans l'historique** reste abonné, et se recharge donc pendant
  qu'on ne le regarde pas. C'est voulu : c'est ce qui le rend juste au retour, et cela ne coûte que
  ce que coûte sa relecture.
- Le coût d'ajout d'un écran réactif tombe à **une interface et une méthode**, ce qui rend
  abordables les neuf écrans que #3644 recense comme exposés sans rien déclarer.
