# ADR 2745 - Une vue riche se découpe en **sous-vues**, et la sous-vue reçoit son modèle

- **Statut** : Accepté - 2026-08-05
- **Chantier** : #2745, lot 4 (#2724) du chantier de dette #2720
- **Vérification** : certaine - `DecisionsRespecteesTest#une_sous_vue_ne_se_procure_pas_ce_qui_doit_etre_unique`

## Contexte

`SonsValidationController` vivait à **199 de NCSS pour un plafond de 200**. Une instruction de plus
dans n'importe quel ajout faisait rougir `lint.yml`, sur une PR qui n'aurait rien fait de mal.

Le réflexe du dépôt est l'*Extract Class* : sortir une unité cohésive de logique. Il avait d'ailleurs
déjà beaucoup servi ici (`MenuCertitude`, `PanneauDiscussion`, `SelectionTableAudio`,
`FiltresVuesAudio`, `PanneauEcouteAudio`, `ActionsRevueAudio`, `MenuAudio`, `MessagesEcranAudio`,
`EncartsEcouteAudio`), au point que trois commentaires de la classe disaient déjà « ce contrôleur est
au plafond de NcssCount ».

**Ce réflexe ne pouvait plus rien ici, et la mesure le dit.** `NcssCount` compte des **instructions**,
et une déclaration de champ en est une. Or la classe portait **82 champs `@FXML`** pour 23 méthodes,
et son `initialize` ne contenait que 29 instructions. En retirant tour à tour chaque partie et en
remesurant :

| Ce qu'on retire | NCSS | Gain |
|---|---|---|
| rien (référence) | 199 | |
| `initialize` **et** `configurerColonnes` entièrement vidés | 167 | 32 |
| les 82 champs `@FXML` | 132 | **67** |

La deuxième ligne est une borne haute irréaliste : elle suppose les deux méthodes réduites à des
coquilles vides. Un regroupement réaliste des appels d'installation aurait rendu **10 à 15 points**.
Le poids était dans les champs, que rien de ce qu'on fait aux méthodes ne déplace.

## Décision

**Une vue trop riche se découpe en sous-vues**, `fx:include` plus contrôleur dédié, et non en
extractions de méthodes. C'est le premier `fx:include` du dépôt : les 24 FXML étaient jusqu'ici
monolithiques.

`TableObservations.fxml` emporte la `TableView`, ses 23 colonnes et le message d'état vide, soit
**25 champs**. Résultat mesuré : `SonsValidationController` passe de **199 à 163** (marge 37) et la
sous-vue s'installe à **61**.

Le critère de découpe est la **cohésion de la vue**, pas le nombre de champs : on coupe là où le FXML
coupait déjà. Ici, le `SplitPane` séparait la table du panneau d'écoute ; la frontière existait, elle
n'a pas été inventée pour l'occasion.

Ce qui a besoin de la sous-vue **et** d'un nœud du parent (panneau d'écoute, menu principal (☰), barre de filtres,
gestionnaire de colonnes) reste câblé par le parent, qui obtient ce dont il a besoin par des
accesseurs. Une sous-vue n'est pas une frontière étanche : c'est un regroupement de nœuds.

## La conséquence qui n'était pas prévue, et qu'il faut connaître

⚠️ **Une sous-vue ne se procure rien de ce qui doit être unique : elle le reçoit de son parent.**

> *Règle élargie le 2026-08-05 (#3335).* Elle a d'abord été écrite « une sous-vue ne doit pas injecter
> son **ViewModel** », d'après le seul cas rencontré. Le ViewModel n'en est qu'un ; le **porteur de
> dialogue** en est un autre, et il est plus piégeux (voir plus bas).

Le premier découpage donnait au sous-contrôleur un constructeur `@Inject` prenant `AudioViewModel`,
par symétrie avec son parent. `FXMLLoader` propage bien la `controllerFactory` Guice aux inclusions,
donc **cela a fonctionné** : la classe s'est construite, la vue s'est chargée, l'écran s'est affiché.

Sauf que `AudioViewModel` est délibérément **non-singleton** (`AudioModule` : « un VM frais par
chargement d'écran, pour éviter les états rémanents »). Le sous-contrôleur recevait donc un
**second** modèle, vide, et liait la table à celui-là.

Le résultat est le pire des deux mondes : rien ne rougissait à la compilation, rien ne levait à
l'exécution, l'écran s'ouvrait normalement. **La table était simplement vide, et les actions ne
portaient sur rien.** Ce sont les 65 TestFX de la vue qui l'ont vu, avec 28 échecs dont le premier
disait « Expected size: 2 but was: 0 ».

La règle qui en découle vaut pour toute sous-vue à venir : **le parent appelle
`sousControleur.installer(monModele, ...)` depuis son propre `initialize()`**, et le câblage de la
sous-vue vit dans cette méthode plutôt que dans un `initialize()`. L'ordre le permet : JavaFX charge
les inclusions et appelle leurs `initialize()` **avant** celui du parent, si bien que le champ
`<fx:id>Controller` est disponible quand le parent s'initialise.

### Le second cas, plus piégeux : les porteurs de l'ADR 0010

[L'ADR 0010](0010-dialogues-bloquants-sont-des-ports.md) fait des dialogues bloquants des **ports
injectables**, pour qu'un test les remplace. Ce qu'elle ne disait pas, la question ne se posant pas
avant les sous-vues : **un port n'est un point de substitution que s'il est unique.**

Un test parent écrit `controleur.confirmateur().definir(stub)`. Si une sous-vue avait le sien, le
double ne s'y appliquerait pas : sous TestFX headless, le `showAndWait()` figerait le test, ou celui-ci
passerait en ne vérifiant rien.

Et ce cas **échappe à la détection par `@Inject`** : le dépôt ne fabrique pas ces porteurs par
injection mais en initialiseur de champ, `new ConfirmateurModifiable()`. C'est pourquoi la garde
cherche les deux formes.

### Pourquoi c'est la panne de l'ADR 3018, à un autre étage

[L'ADR 3018](3018-un-outil-compose-depuis-la-racine.md) constate qu'« un injecteur amputé et une
fonctionnalité désactivée produisent le même écran ». La forme est identique ici : un composant **se
procure** localement ce qu'il aurait dû **recevoir**, l'injecteur satisfait la demande sans broncher,
et le résultat n'a pas l'air cassé - il a l'air d'un produit configuré autrement.

3018 en tire que ce genre de règle « ne peut pas tenir par la vigilance ». C'est la raison pour
laquelle celle-ci est gardée plutôt qu'écrite.

## Le prix

- **Une indirection de plus** pour lire l'écran : la table ne se trouve plus dans le FXML parent.
  L'inclusion porte un commentaire qui dit où elle est et pourquoi.
- **`fx:include` court-circuite [ChargeurFxml]** : une inclusion introuvable redonne le
  `IllegalStateException: Location is not set` opaque que ce point d'entrée existe pour éviter. Le cas
  ne s'est pas présenté (la ressource est à côté de son contrôleur, recopiée par Maven comme les
  autres), mais il est à connaître.
- **Le nom du champ est imposé** : JavaFX concatène le `fx:id` de l'inclusion et le suffixe
  `Controller`. `fx:id="tableau"` donne `tableauController`, et rien d'autre ne marche.

## Alternatives écartées

- **Regrouper l'assemblage** (un `TableObservationsAudio.installer(...)` statique) : mesuré à 10-15
  points, pour une marge finale d'une quinzaine. Honnête mais transitoire : on repassait.
- **Relever le plafond `NcssCount`** pour les contrôleurs de vue. `pmd-ruleset.xml` l'autorise
  explicitement (« si un seuil est vraiment inadapté, c'est le seuil qu'on rediscute ici, avec sa
  justification »), et la mesure en fournissait une réelle : le plafond pénalise la richesse d'une vue
  autant que la complexité d'un code. Écarté parce que ce plafond est le **seul** garde-fou God-class
  du dépôt, et que le relever n'aurait rendu aucune classe plus lisible.
- **Rendre `AudioViewModel` singleton** pour que l'injection dans la sous-vue soit correcte : c'était
  résoudre un problème d'assemblage en défaisant une décision de conception documentée.
