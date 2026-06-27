# Patterns et principes

L'architecture (cf. [Architecture](architecture.md)) n'est pas un assemblage ad hoc : elle applique
des **patrons de conception** connus, chacun choisi pour une raison précise et pour faire respecter
les principes **SOLID** (et quelques autres : DRY, faible couplage, IoC).

Cette page liste, pour chaque patron : **ce que c'est**, **pourquoi il est pertinent ici**, et **les
principes qu'il sert**.

!!! abstract "Rappel SOLID"
    **S**RP (responsabilité unique) · **O**CP (ouvert/fermé) · **L**SP (substitution de Liskov) ·
    **I**SP (ségrégation des interfaces) · **D**IP (inversion des dépendances).

## Patrons architecturaux

### MVVM (Model-View-ViewModel)

**Quoi.** Trois couches : le `model` (métier), le `viewmodel` (état observable + logique de
présentation), la `view` (FXML + controller) qui **observe** le viewmodel par *data binding*.

**Ici.** C'est le squelette de chaque feature. La vue ne contient aucune règle ; le viewmodel ne
connaît aucun widget ; le modèle ignore l'IHM. On peut tester la présentation **sans fenêtre** et
réutiliser le métier **sans JavaFX**.

**Principes.** **SRP** (chaque couche une responsabilité), **DIP** (la vue dépend d'abstractions
observables, pas de logique concrète). Frontières **garanties par ArchUnit**.

### Package-by-feature (tranches verticales)

**Quoi.** Organiser le code **par fonctionnalité** (sites, passage, …), chacune contenant ses 4
couches, plutôt que par couche technique transverse.

**Ici.** Une nuit de capture se traite écran par écran ; chaque écran est un paquet autonome. On
ouvre, modifie ou supprime une feature **sans naviguer dans tout le code**.

**Principes.** **Forte cohésion / faible couplage** ; **OCP** à l'échelle du produit (ajouter une
feature ≈ ajouter un paquet, sans toucher aux autres — garanti par la règle ArchUnit
`pas_de_dependance_inter_feature_vers_la_vue`).

## Couplage et extension

### Injection de dépendances + Composition Root

**Quoi.** Les objets reçoivent leurs dépendances (au lieu de les créer) ; un **unique** point,
[`RacineInjecteur`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/di), assemble le graphe (Guice).

**Ici.** Aucun `new` métier dispersé : services, DAO, ViewModels et même les controllers FXML sont
**injectés** (cf. [Injection](injection.md)). Les tests substituent une base jetable ou un service
mocké sans changer le code de production.

**Principes.** **DIP** (on dépend d'abstractions, le câblage est externalisé) et **IoC** (« ne nous
appelez pas, nous vous appellerons » : c'est le conteneur qui instancie).

### Separated Interface (contrats `Ouvrir*`)

**Quoi.** Publier une **interface dans le socle**, l'implémenter ailleurs : l'appelant dépend de
l'abstraction, jamais de l'implémentation concrète.

**Ici.** `sites` ouvre M-Passage via `commun.view.OuvrirPassage`, implémenté par `passage`. La
dépendance est **inversée** : ni `sites` ni `passage` ne se voient (cf.
[Navigation](navigation.md#ouvrir-une-autre-feature-sans-en-dependre)).

**Principes.** **DIP** (les deux features dépendent du contrat, pas l'une de l'autre) et **OCP**
(brancher une nouvelle implémentation sans modifier l'appelant).

### Plugin / Extension (Multibinder)

**Quoi.** Le socle agrège un `Set<T>` que **chaque feature alimente** par multibinding, sans que le
socle connaisse les contributeurs.

**Ici.** L'accueil collecte les `ActiviteAccueil` (cartes) et `IndicateurAccueil` (compteurs)
publiés par les features (cf. [Injection](injection.md)). Ajouter une carte d'accueil **ne modifie
pas** le `MainController`.

**Principes.** **OCP** par excellence : le chrome est *fermé à la modification* mais *ouvert à
l'extension*.

### Interfaces de rôle fines (ISP)

**Quoi.** De petites interfaces **optionnelles** à responsabilité unique, qu'un écran implémente
**seulement si** la capacité le concerne :
[`GardeQuitter`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/GardeQuitter.java),
[`EmplacementNavigation`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/EmplacementNavigation.java),
[`RafraichirAuRetour`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/RafraichirAuRetour.java).

**Ici.** Le `Navigateur` les détecte par `instanceof` sur le controller. Un écran sans saisie
n'implémente pas `GardeQuitter` ; un écran sans données mutables n'implémente pas `RafraichirAuRetour`.

**Principes.** **ISP** (aucun écran n'est forcé d'implémenter ce qu'il n'utilise pas) et **OCP** (le
Navigateur honore de nouvelles capacités sans connaître les écrans).

## Accès aux données

### DAO (Data Access Object)

**Quoi.** Isoler l'accès SQL derrière des objets dédiés ; le reste du code ignore JDBC.

**Ici.** Chaque entité a son DAO (`*/model/dao/`). La règle ArchUnit `view_sans_jdbc` interdit à
l'IHM de parler à la base : tout passe par les services/VM.

**Principes.** **SRP** (la persistance est une responsabilité à part) et **DIP** (le métier dépend
d'abstractions de données, pas de l'API JDBC).

### Template Method (`DaoGenerique`)

**Quoi.** Une classe de base définit le **squelette** d'un algorithme et délègue les détails variables
à des méthodes abstraites que les sous-classes remplissent.

**Ici.** [`DaoGenerique`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/DaoGenerique.java)
offre `findAll`/`findById`/`delete` **gratuitement** ; un DAO concret fournit seulement `table()`,
`colonneCle()` et son `RowMapper`.

**Principes.** **DRY** (la boucle `ResultSet` n'est écrite qu'une fois), **OCP** (un nouveau DAO
**étend** sans modifier la base) et **LSP** (tout `DaoGenerique` concret est substituable).

### Strategy (`RowMapper`, génération de sélection)

**Quoi.** Encapsuler un algorithme interchangeable derrière une interface, pour le faire varier sans
toucher au client.

**Ici.** Deux exemples :

- [`RowMapper<T>`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/RowMapper.java)
  (`@FunctionalInterface`) : « comment transformer **une** ligne en entité » varie par DAO, l'itération
  reste dans `DaoGenerique`.
- [`GenerateurSelection`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/qualification/model/GenerateurSelection.java)
  + `MethodeSelection` : « comment choisir les séquences à écouter » (répartition temporelle vs
  aléatoire) varie indépendamment du service.

**Principes.** **OCP** (ajouter une stratégie sans modifier l'appelant), **SRP** (chaque stratégie est
une règle pure, testable sans base ni IHM).

### Unit of Work (`UniteDeTravail`)

**Quoi.** Regrouper plusieurs écritures dans **une transaction** : tout réussit, ou tout est annulé.

**Ici.** [`UniteDeTravail`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/UniteDeTravail.java)
encadre par exemple « créer un passage **et** sa session » : une exception déclenche un *rollback*, la
base reste cohérente.

**Principes.** **SRP** (la gestion transactionnelle est isolée des DAO) et **cohésion** d'une opération
métier atomique (intégrité O7).

## Comportement et présentation

### Observer (propriétés et *binding* JavaFX)

**Quoi.** Des observateurs réagissent aux changements d'un sujet observable, sans que le sujet les
connaisse.

**Ici.** Le viewmodel expose des `Property`/`ObservableList` ; la vue s'y **lie**. Quand l'état change,
l'IHM se met à jour **toute seule** : la vue *observe*, elle ne *tire* pas.

**Principes.** **Faible couplage** View↔ViewModel et **DIP** (la vue dépend d'abstractions
observables). C'est le moteur de MVVM.

### Factory (`controllerFactory`)

**Quoi.** Déléguer la **création** d'objets à une fabrique plutôt que de les instancier soi-même.

**Ici.** `FXMLLoader.setControllerFactory(injector::getInstance)` : les controllers FXML sont créés
**par Guice**, donc reçoivent leurs ViewModels/services par injection (cf.
[Injection](injection.md#des-controllers-fxml-injectes)).

**Principes.** **DIP** (le controller ne construit pas ses dépendances) et **IoC**.

### Machine à états (`MoteurWorkflowPassage`)

**Quoi.** Centraliser les **transitions autorisées** d'un objet selon son état courant.

**Ici.** [`MoteurWorkflowPassage`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/model/MoteurWorkflowPassage.java)
encode le workflow linéaire `Importé → … → Déposé` : une seule transition (le **successeur immédiat**)
est permise — interdit de sauter une étape ou de revenir en arrière. Logique **isolée** de l'énum
[`StatutWorkflow`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/StatutWorkflow.java)
(qui reste un simple porteur de libellés).

**Principes.** **SRP** (les règles de transition ne polluent pas l'énum ni les services) et un point
de vérité unique pour l'avancement d'une nuit.

## Synthèse : où vit chaque principe SOLID

| Principe | Incarné surtout par |
|---|---|
| **S**RP | MVVM (couches), DAO, `UniteDeTravail`, `MoteurWorkflowPassage` |
| **O**CP | Contrats `Ouvrir*`, Multibinder d'accueil, Template Method, Strategy |
| **L**SP | Sous-types de `DaoGenerique` substituables |
| **I**SP | Interfaces de rôle fines (`GardeQuitter`, `RafraichirAuRetour`, `EmplacementNavigation`) |
| **D**IP | Injection + Composition Root, contrats `Ouvrir*`, *binding* observable |

Et au-delà de SOLID : **DRY** (Template Method, `RowMapper`), **IoC** (DI, Factory), **faible
couplage / forte cohésion** (package-by-feature, Observer).
