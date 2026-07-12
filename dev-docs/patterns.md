# Patterns et principes

L'architecture (cf. [Architecture](architecture.md)) applique des **patrons de conception** connus,
chacun choisi pour une raison précise et pour faire respecter les principes **SOLID** ainsi que
d'autres principes transverses (loi de Déméter, YAGNI, KISS, DRY… détaillés en fin de page,
[Au-delà de SOLID](#au-dela-de-solid)).

Chaque patron est présenté ainsi : **le problème** qu'il résout, **la solution**, **comment il est
utilisé ici** (avec, selon les cas, un extrait et un lien vers le code), un **diagramme** quand il
clarifie la structure ou le flux, et les **principes** qu'il sert.

!!! abstract "Rappel SOLID"
    **S**RP responsabilité unique · **O**CP ouvert/fermé · **L**SP substitution de Liskov ·
    **I**SP ségrégation des interfaces · **D**IP inversion des dépendances.

---

## MVVM (Model-View-ViewModel)

**Le problème.** Mélanger affichage, logique de présentation et règles métier dans les controllers
rend le code **intestable** (il faut une fenêtre) et **non réutilisable** (tout est lié à JavaFX).

**La solution.** Trois couches : le `model` (métier pur), le `viewmodel` (état **observable** +
logique de présentation), la `view` (FXML + controller) qui **observe** le viewmodel par *data
binding*. Le flux de dépendances va de la vue vers le modèle, jamais l'inverse.

**Dans VigieChiro.** Chaque feature suit ce découpage. La vue ne fait que **lier** des contrôles à des
propriétés ; elle ne calcule rien et ne touche pas la base.

```mermaid
classDiagram
    class View {
        Controller
        FXML
    }
    class ViewModel {
        Property
        ObservableList
    }
    class Model {
        services
        DAO
    }
    View ..> ViewModel : se lie
    ViewModel ..> Model : appelle
```

**Principes.** **SRP** (une responsabilité par couche), **DIP** (la vue dépend d'abstractions
observables, pas de logique concrète). Frontières **garanties par ArchUnit** (`viewmodel_sans_javafx_ui`,
`view_sans_jdbc`).

---

## Objets-valeurs (records immuables)

**Le problème.** Des entités **mutables** (avec setters) se prêtent aux états incohérents, au partage
accidentel d'une instance et aux bugs d'égalité (comparaison par référence).

**La solution.** Modéliser le domaine en **`record` immuables** : champs finaux, égalité **par
valeur**, aucun setter. Pour « modifier », on **crée** une nouvelle instance.

**Dans VigieChiro.** Le domaine est quasi entièrement en records (**≈ 70** : `Passage`, `Site`,
`SequenceDEcoute`, `Observation`…). Les DAO **construisent** ces records ligne par ligne via un
`RowMapper`, et les ViewModels les exposent dans des `ObservableList`.

**Principes.** Immuabilité (sûreté en lecture, raisonnement local) et **SRP** (l'entité ne porte que
ses données). Socle naturel du DAO et du `RowMapper`.

---

## Package-by-feature (tranches verticales)

**Le problème.** Une organisation **par couche** (`controllers/`, `services/`, `dao/`…) éparpille une
même fonctionnalité dans tout le projet : pour modifier un écran, on touche partout.

**La solution.** Regrouper le code **par fonctionnalité** : `sites/`, `passage/`… chacun contenant ses
4 couches. Une feature devient une **tranche verticale** autonome.

**Dans VigieChiro.** Les 10 features sont des paquets autonomes ; le socle `commun/` porte le partagé
(chrome, persistance, DI). On ouvre, modifie ou supprime une feature sans naviguer ailleurs.

**Principes.** **Forte cohésion / faible couplage** ; **OCP** à l'échelle du produit (ajouter une
feature ≈ ajouter un paquet, sans toucher aux autres — garanti par
`pas_de_dependance_inter_feature_vers_la_vue`).

---

## Injection de dépendances + Composition Root

**Le problème.** Si chaque objet **crée** ses dépendances (`new ServiceX()`), le graphe est figé,
impossible à substituer en test, et le câblage est dispersé partout.

**La solution.** Les objets **reçoivent** leurs dépendances (constructeur), et **un seul** endroit, la
*Composition Root*, assemble le graphe complet.

**Dans VigieChiro.** [`RacineInjecteur`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/di/RacineInjecteur.java)
installe le socle + les 10 modules de feature (Guice). Même les controllers FXML sont injectés (cf.
*Factory* plus bas). En test, on substitue une base jetable sans changer le code de production.

```java
public static Injector creer() {
    return Guice.createInjector(
        new CommunModule(), new PersistenceModule(),
        new SitesModule(), new PassageModule(), /* … */ new RechercheModule());
}
```

Détails et diagramme de séquence : [Injection (Guice)](injection.md).

**Principes.** **DIP** (on dépend d'abstractions, le câblage est externalisé) et **IoC** (« ne nous
appelez pas, nous vous appellerons » : le conteneur instancie).

---

## Singleton (géré par le conteneur)

**Le problème.** Certaines ressources doivent être **uniques** dans toute l'application : une seule
base, un seul service de navigation. Les multiplier créerait des incohérences (deux connexions, deux
historiques).

**La solution.** Plutôt que le Singleton « maison » (constructeur privé + champ statique, difficile à
tester et à substituer), on **délègue l'unicité au conteneur** : `@Singleton` Guice.

**Dans VigieChiro.** `SourceDeDonnees`, `Navigateur`, les `Navigation*` et la **plupart des providers
de DAO et de services** des features sont `@Singleton` (~70 bindings) : une seule instance par
injecteur, mais **toujours injectée** (donc remplaçable en test).

**Principes.** Évite l'**état statique global** tout en restant **testable** : l'unicité est une
décision de **câblage**, pas une contrainte gravée dans la classe.

---

## Separated Interface (contrats `Ouvrir*`)

**Le problème.** Si `sites` appelait directement `passage.view.NavigationPassage`, les features
seraient **couplées** entre elles — impossible de les faire évoluer indépendamment (et la règle
ArchUnit l'interdit).

**La solution.** Publier une **interface dans le socle**, l'implémenter dans la feature cible :
l'appelant dépend de l'**abstraction**, jamais de l'implémentation. La dépendance est **inversée**.

**Dans VigieChiro.** [`OuvrirPassage`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/OuvrirPassage.java)
(socle) est implémenté par
[`NavigationPassage`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/view/NavigationPassage.java)
(feature `passage`) et **bindé** par `PassageModule`. `sites` injecte `OuvrirPassage`.

```mermaid
classDiagram
    class OuvrirPassage {
        <<interface>>
        +ouvrir(Long, ContexteSite)
    }
    class NavigationPassage
    SiteDetailController ..> OuvrirPassage : injecte
    NavigationPassage ..|> OuvrirPassage : implémente
    note for OuvrirPassage "dans le socle commun.view"
```

**Principes.** **DIP** (les deux features dépendent du contrat, pas l'une de l'autre) et **OCP**
(brancher une nouvelle implémentation sans modifier l'appelant). Tous les contrats : `OuvrirSite`,
`OuvrirPassage`, `OuvrirVerification`, `OuvrirImportation`, `OuvrirLot`, `OuvrirValidation`,
`OuvrirDiagnostic`. Voir aussi [Navigation](navigation.md#ouvrir-une-autre-feature-sans-en-dependre).

---

## Facade (`Navigation*`)

**Le problème.** Ouvrir un écran demande plusieurs gestes : charger le FXML, brancher la
`controllerFactory`, ouvrir le controller sur son contexte, empiler dans le `Navigateur`. Répétés tels
quels chez chaque appelant, ils seraient verbeux et fragiles.

**La solution.** Une **façade** par feature expose une opération **simple** (`ouvrir(...)`) qui
orchestre ces gestes en interne.

**Dans VigieChiro.** [`NavigationPassage`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/view/NavigationPassage.java)
(et ses homologues `Navigation*`) implémente le contrat `Ouvrir*` en **cachant** le `FXMLLoader` et le
`Navigateur` : l'appelant ne voit qu'`ouvrir(idPassage, contexte)`. Le `Navigateur` lui-même est une
façade sur la zone centrale du chrome + l'historique.

**Principes.** **SRP** (la mécanique d'ouverture est encapsulée) et **faible couplage** (l'appelant
ignore FXML / Navigateur).

---

## Plugin / Extension (Multibinder)

**Le problème.** L'accueil affiche une carte pour **certaines** features (et un compteur de tableau de
bord pour d'autres). Si le `MainController` connaissait chacune, ajouter une contribution l'obligerait
à **se modifier** à chaque fois.

**La solution.** Le socle déclare un `Set<T>` que **les features intéressées alimentent** (multibinding
Guice), sans que le socle connaisse les contributeurs. Il injecte l'ensemble et l'agrège.

**Dans VigieChiro.** Quatre points d'extension suivent ce patron, chacun avec un helper du DSL
[`ModuleDeFeature`](injection.md#ce-que-publie-un-module-de-feature) : `ActiviteAccueil` (carte
d'accueil, `activite(...)`), `IndicateurAccueil` (compteur, `indicateur(...)`), `OngletReglages`
(onglet de l'écran Réglages, `ongletReglages(...)`) et `ActionMenu` (entrée du menu ☰, `actionMenu(...)`).
Le contrat est **agnostique de JavaFX** (dans `commun/view`), la feature ne fournit que des données
(un descripteur, un libellé…), et c'est le socle (`MainController`, `EcranReglagesController`,
`ConstructeurMenuOutils`) qui construit les widgets. Exemple : une bascule de menu déclare une
`BooleanProperty` liée à `ReglagesReactifs` ; le socle en fait une `CheckMenuItem`.

```mermaid
classDiagram
    class MainController {
        Set~ActiviteAccueil~ activites
    }
    class ActiviteAccueil {
        <<interface>>
    }
    MainController o-- ActiviteAccueil : agrège
    ActiviteAccueil <|.. ActiviteMesSites
    ActiviteAccueil <|.. ActiviteImporterNuit
```

**Principes.** **OCP** par excellence : le chrome est **fermé à la modification** mais **ouvert à
l'extension** (une nouvelle carte = un nouveau binding, zéro ligne touchée dans le socle).

**Feature = plugin.** Le patron va jusqu'au bout : les modules de feature sont eux-mêmes
**auto-découverts** par `RacineInjecteur` (`ServiceLoader<ModuleDeFeature>`, cf.
[Injection](injection.md#la-racine-de-composition)). Une feature complète (DAO, services, carte,
compteur, réglages, entrée de menu) s'ajoute donc **sans toucher une seule ligne du socle ni de la
racine de composition** — juste un `XxxModule extends ModuleDeFeature` déclaré comme service.

---

## Interfaces de rôle fines (ISP)

**Le problème.** Une grosse interface « écran » avec *garde de sortie + fil d'Ariane + rafraîchissement
+ …* forcerait **chaque** écran à tout implémenter, même ce qu'il n'utilise pas.

**La solution.** De petites interfaces **optionnelles**, à responsabilité unique, qu'un écran
implémente **seulement si** la capacité le concerne. Le `Navigateur` les détecte par `instanceof`.

**Dans VigieChiro.**

| Interface (1 rôle) | Implémentée par les écrans qui… |
|---|---|
| [`GardeQuitter`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/GardeQuitter.java) | ont une **saisie non enregistrée** |
| [`EmplacementNavigation`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/EmplacementNavigation.java) | ont une **place hiérarchique** (fil d'Ariane) |
| [`RafraichirAuRetour`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/RafraichirAuRetour.java) | affichent des **données mutables** |

Un écran lecture seule n'implémente **aucune** des trois.

**Principes.** **ISP** (aucun écran n'est forcé d'implémenter ce qu'il n'utilise pas) et **OCP** (le
Navigateur honore de nouvelles capacités sans connaître les écrans).

---

## DAO (Data Access Object)

**Le problème.** Du SQL `PreparedStatement` mélangé à la logique métier ou à l'IHM est impossible à
tester, à réutiliser, et viole la séparation des couches.

**La solution.** Isoler l'accès aux données derrière des objets dédiés ; le reste du code ignore JDBC
et dialogue avec des **services**.

**Dans VigieChiro.** Chaque entité a son DAO dans `*/model/dao/`. La règle ArchUnit `view_sans_jdbc`
**interdit** à l'IHM de toucher `model.dao` ou `java.sql`.

**Principes.** **SRP** (la persistance est une responsabilité à part) et **DIP** (le métier dépend
d'abstractions de données, pas de l'API JDBC).

---

## Template Method (`DaoGenerique`)

**Le problème.** Tous les DAO réécriraient la même mécanique : ouvrir une connexion, exécuter, itérer
le `ResultSet`, fermer. Beaucoup de **duplication**.

**La solution.** Une classe de base fixe le **squelette** de l'algorithme (`findAll`, `findById`,
`delete`) et **délègue** les détails variables à des méthodes que les sous-classes remplissent.

**Dans VigieChiro.** [`DaoGenerique<T, ID>`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/DaoGenerique.java)
fournit les opérations communes ; un DAO concret donne seulement `table()`, `colonneCle()` et son
`RowMapper`.

```mermaid
classDiagram
    class DaoGenerique {
        <<abstract>>
        +findAll()
        +findById(id)
        +delete(id)
        #table()
        #colonneCle()
        #rowMapper()
    }
    class SiteDao
    class PassageDao
    DaoGenerique <|-- SiteDao
    DaoGenerique <|-- PassageDao
```

**Principes.** **DRY** (la boucle `ResultSet` n'existe qu'une fois), **OCP** (un nouveau DAO **étend**
sans modifier la base) et **LSP** (tout `DaoGenerique` concret est substituable à l'abstraction).

---

## Strategy (`RowMapper`, génération de sélection)

**Le problème.** Une partie d'un algorithme **varie** (comment lire une ligne ? comment choisir des
séquences ?) alors que le reste est stable. Un `if/else` géant serait fragile et fermé.

**La solution.** Encapsuler la partie variable derrière une **abstraction interchangeable**, injectée
ou passée au client.

**Dans VigieChiro.** Deux usages :

- [`RowMapper<T>`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/RowMapper.java)
  (`@FunctionalInterface`) : « transformer **une** ligne en entité » varie par DAO (souvent une
  lambda) ; l'itération reste dans `DaoGenerique`.

  ```java
  @FunctionalInterface
  public interface RowMapper<T> { T mapper(ResultSet rs) throws SQLException; }
  ```

- [`GenerateurSelection`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/qualification/model/GenerateurSelection.java) :
  `selectionner(sequences, methode, taille)` choisit un sous-ensemble selon la `MethodeSelection`
  (répartition temporelle vs aléatoire vs manuel) — une **règle pure**, sans base ni IHM.

```mermaid
classDiagram
    class RowMapper {
        <<interface>>
        +mapper(ResultSet) T
    }
    class DaoGenerique
    DaoGenerique o-- RowMapper : utilise (stratégie)
```

**Principes.** **OCP** (ajouter une stratégie sans modifier l'appelant), **SRP** (chaque stratégie est
une règle isolée, **testable sans persistance ni IHM** — objectif réutilisation O6).

---

## Table de suivi par unité (socle `commun`)

**Le problème.** Trois opérations longues (génération d'archives #820, import par fichier #947, dépôt
VigieChiro #983) doivent montrer l'avancement **de chaque unité de travail** (état coloré + barre),
alimenté depuis des fils d'arrière-plan, parfois dans le désordre (travail parallèle).

**La solution.** Un socle en trois couches, spécialisé par feature :

- `commun.viewmodel` : `EtatUnite` (en attente / en cours / terminée / échec), `LigneSuivi` (ligne
  observable extensible), `SuiviLignes<L>` (pilote générique, ciblage par numéro, tolérant aux
  événements inconnus ou dans le désordre) ;
- `commun.view` : `TableSuivi` (colonnes `#` / spécifiques / Progression, rangées colorées
  `.ligne-suivi.etat-…` dans `design.css`) + `CelluleProgressionUnite` (barre vive ou icône + libellé,
  raison d'échec en infobulle) ;
- côté feature : une interface d'événements métier (`SuiviArchives`, `SuiviFichiers`, `SuiviDepot`,
  chacune avec sa variante `inerte()`), un **relais** qui rejoue chaque événement sur le fil JavaFX
  (`Platform.runLater`), et une spécialisation `SuiviLignesXxx extends SuiviLignes<LigneXxx>` qui
  traduit les événements en mutations observables.

**La règle.** Toute nouvelle opération longue « par unité » réutilise ce socle : définir l'interface
d'événements (+ `inerte()`), la ligne et le pilote spécialisés, le relais fil JavaFX — jamais une
table ad hoc.

## Occupation d'un écran pendant un traitement long (socle `commun`)

**Le problème.** Un traitement lourd (agrégats, inspection de dossier, appel réseau) exécuté
**synchrone sur le fil JavaFX** fige l'IHM sans feedback ; un `setCursor(WAIT)` n'y suffit pas, fil
bloqué. Le patron correct (thread virtuel → travail → `Platform.runLater`) était recopié écran par
écran, avec le piège récurrent des mutations hors fil JavaFX (« clic figé »).

**La solution.** Deux briques de `commun.view`, à composer :

- `ExecuteurTache` (interface `@ImplementedBy` synchrone) : `executer(Supplier travail, Consumer
  succès, Consumer échec)` exécute le travail **hors du fil JavaFX** puis applique résultat/erreur
  **sur** le fil JavaFX. `ExecuteurTacheAsynchrone` (thread virtuel + `runLater`) en production ;
  `ExecuteurTacheSynchrone` (défaut) rend les tests déterministes. Sœur d'`ExecuteurFiche`.
- `IndicateurOccupation` : superpose sur un `StackPane` hôte un voile + roue + libellé « … en
  cours » (`enCoursProperty`, styles `.occupation-*` dans `design.css`), et pilote un `ExecuteurTache`
  via `occuper(libellé, travail, succès, échec)`. Le voile capte les clics le temps du traitement.

**La règle.** Toute opération longue d'un écran passe par `IndicateurOccupation` (l'échec est routé
vers le filet d'erreurs de l'écran, #795) — jamais un `Thread.ofVirtual()` + `runLater` recopié à la
main. Le déport écran par écran est suivi dans l'EPIC #793.

## Écrans de données : densité, badge, filtres (socle design partagé)

**Le problème.** Les onze écrans sont nés à des moments différents, sans référentiel de design commun :
tables plus ou moins denses, statuts tantôt en texte coloré tantôt en pastille, chaque feature
recopiant son CSS. Le chantier #686 a unifié la **famille « écrans de données »** (audio, multisite,
analyse, fiche site, qualification) sur un socle `commun/view`.

**La solution.** Trois briques réutilisables, plus une feuille de style chargée par tous les écrans :

- `commun.view.TableDonnees` : `uniformiser(table)` (et `uniformiserNavigable` pour une table qui
  répond au clavier) applique la classe CSS `table-donnees` (hauteur de ligne, padding, en-tête
  uniques). **Un appel unique** dans le contrôleur garantit la densité partagée ;
- `commun.view.ColonneBadge` : `cellule(Function<S, String> classe)` fabrique une cellule **pastille**
  dont la couleur est **dérivée de la donnée de la ligne** (jamais stockée). Les surcharges
  `classe(StatutWorkflow)` / `classe(Verdict)` couvrent les types de `commun.model` ;
- `commun.view.design.css` : jetons sémantiques (`-badge-succes/avertissement/danger/info/neutre-*`) et
  classes `.badge-*`, **chargée par tous les FXML** (plus de CSS de statut recopié par feature).

**Le piège d'architecture (mapping feature → classe CSS).** `ColonneBadge` vit dans `commun`, qui **ne
doit dépendre d'aucune feature** (règle `ArchitectureTest.features_sans_cycle`). Le socle ne connaît donc
que les enums de `commun.model`. Pour un statut **propre à une feature** (`Fraicheur` côté sites,
`StatutObservation` côté validation…), le mapping statut → classe CSS reste **côté feature**, et l'on
passe cette fonction au générique `cellule(Function)` :

```java
// sites : la vue mappe son enum
colStatut.setCellFactory(c -> ColonneBadge.cellule(LignePassage::statutClasseCss));
// audio : FormatLigneAudio.classeBadgeStatut(StatutObservation) -> "badge-observation-…"
col.statut().setCellFactory(c ->
    ColonneBadge.cellule(ligne -> FormatLigneAudio.classeBadgeStatut(ligne.statut())));
```

Les classes CSS correspondantes (`.badge-observation-*`, `.badge-frais/tiede/froid`) vivent quand même
dans `design.css` : ce ne sont que des **chaînes**, aucune dépendance de code de `commun` vers la feature.

**La règle.** Une nouvelle table de données réutilise `TableDonnees.uniformiser` + `ColonneBadge` ;
jamais une densité ni une pastille ad hoc. Un statut de `commun.model` passe par `ColonneBadge.classe` ;
un statut de feature reçoit un `classeBadge`/`classeCss` **côté feature** (jamais une surcharge dans
`commun`, sous peine de cycle).

## Unit of Work (`UniteDeTravail`)

**Le problème.** Par défaut, chaque écriture DAO s'auto-commit. Mais « créer un passage **et** sa
session » doit être **atomique** : si la 2ᵉ échoue, la 1ʳᵉ ne doit pas rester en base.

**La solution.** Regrouper les écritures dans **une transaction** : tout réussit (commit), ou tout est
annulé (rollback).

**Dans VigieChiro.** [`UniteDeTravail`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence/UniteDeTravail.java)
exécute un bloc sur **une seule connexion** :

```java
uniteDeTravail.executer(connexion -> {
    // plusieurs écritures... une exception => rollback
});
```

```mermaid
sequenceDiagram
    participant S as Service
    participant U as UniteDeTravail
    participant DB as Connexion
    S->>U: executer(co -> { écritures })
    U->>DB: autoCommit(false)
    U->>DB: écritures…
    alt tout réussit
        U->>DB: commit()
    else exception
        U->>DB: rollback()
    end
```

**Principes.** **SRP** (la gestion transactionnelle est isolée des DAO) ; garantit l'**intégrité** O7.

---

## Observer (propriétés et *binding* JavaFX)

**Le problème.** Comment garder l'IHM **synchronisée** avec l'état sans que le modèle « pousse » vers
des widgets qu'il ne devrait pas connaître ?

**La solution.** Le sujet (une `Property` / `ObservableList`) **notifie** ses observateurs au
changement ; la vue **s'abonne** par *binding*. Le sujet ignore qui l'observe.

**Dans VigieChiro.** Le viewmodel expose des propriétés ; la vue s'y lie. Quand l'état change, l'IHM se
met à jour **toute seule** : la vue *observe*, elle ne *tire* pas. C'est le moteur de MVVM.

```mermaid
sequenceDiagram
    participant VM as ViewModel
    participant P as Property
    participant V as Vue
    VM->>P: set(nouvelle valeur)
    P-->>V: notifie (invalidation)
    V->>V: met à jour le widget
```

**Principes.** **Faible couplage** View↔ViewModel et **DIP** (la vue dépend d'abstractions
observables, pas de logique).

!!! note "API fluente (le « builder » le plus proche)"
    Les liaisons s'écrivent souvent avec l'**API fluente** de JavaFX :
    `Bindings.when(cond).then(a).otherwise(b)`, `Bindings.createStringBinding(...)`. C'est un
    *builder* conditionnel **fourni par la bibliothèque** — pas un patron Builder que nous
    implémentons. Le projet n'a d'ailleurs **pas de Builder maison** : les entités sont des `record`
    immuables (cf. *Objets-valeurs*), qui rendraient un builder superflu.

---

## Factory (`controllerFactory`)

**Le problème.** Par défaut, `FXMLLoader` crée les controllers avec `new` (constructeur vide) : ils ne
peuvent **pas** recevoir de dépendances injectées.

**La solution.** Fournir au loader une **fabrique** qui délègue la création à Guice.

**Dans VigieChiro.** `loader.setControllerFactory(injector::getInstance)` : chaque controller est
instancié **par le conteneur**, donc reçoit ses ViewModels/services par constructeur (cf.
[`App`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/App.java)).
Diagramme de séquence du bootstrap : [Injection](injection.md#des-controllers-fxml-injectes).

**Principes.** **DIP** (le controller ne construit pas ses dépendances) et **IoC**.

---

## Machine à états (`MoteurWorkflowPassage`)

**Le problème.** Le statut d'un passage (`Importé → … → Déposé`) doit avancer **dans l'ordre** : on ne
doit ni sauter une étape (importer puis déposer) ni revenir en arrière. Disséminer ces règles dans les
services serait fragile.

**La solution.** Centraliser les **transitions autorisées** dans un objet dédié : depuis un état, une
seule cible permise (le successeur immédiat).

**Dans VigieChiro.** [`MoteurWorkflowPassage`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/model/MoteurWorkflowPassage.java)
porte l'ordre et expose `suivant(...)` / `estTransitionAutorisee(...)` / `exigerTransitionAutorisee(...)`.
La logique est **isolée** de l'énum
[`StatutWorkflow`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/StatutWorkflow.java)
(simple porteur de libellés).

```mermaid
stateDiagram-v2
    [*] --> IMPORTE
    IMPORTE --> TRANSFORME
    TRANSFORME --> VERIFIE
    VERIFIE --> PRET_A_DEPOSER
    PRET_A_DEPOSER --> DEPOSE
    DEPOSE --> [*]
```

**Principes.** **SRP** (les règles de transition ne polluent ni l'énum ni les services) et un **point
de vérité unique** pour l'avancement d'une nuit.

---

## Synthèse : où vit chaque principe SOLID

| Principe | Incarné surtout par |
|---|---|
| **S**RP | MVVM (couches), DAO, `UniteDeTravail`, `MoteurWorkflowPassage`, Facade, objets-valeurs |
| **O**CP | Contrats `Ouvrir*`, Multibinder d'accueil, Template Method, Strategy |
| **L**SP | Sous-types de `DaoGenerique` substituables |
| **I**SP | Interfaces de rôle fines (`GardeQuitter`, `RafraichirAuRetour`, `EmplacementNavigation`) |
| **D**IP | Injection + Composition Root, contrats `Ouvrir*`, *binding* observable, Factory |

## Au-delà de SOLID

SOLID n'est pas seul : l'architecture respecte aussi plusieurs principes transverses, eux aussi
visibles dans le code.

### Loi de Déméter (« ne parle qu'à tes amis proches »)

Un objet ne devrait appeler que les méthodes de **lui-même**, de ses **paramètres**, de ce qu'il
**crée** et de ses **champs directs** — pas de chaîne `a.getB().getC().faire()`.

**Ici.** La vue se lie à `vm.titreProperty()` (un collaborateur **direct**), jamais à
`vm.modele().site().nom()`. Les contrats `Ouvrir*` reçoivent un `ContexteSite` / `ContextePassage`
(données **passées en paramètre**) plutôt que de fouiller dans l'écran appelant. Et `view_sans_jdbc`
interdit à la vue de « traverser » les couches jusqu'à la base.

### YAGNI (« vous n'en aurez pas besoin »)

Ne pas construire de **généricité spéculative**.

**Ici.** Pas d'ORM (des DAO `PreparedStatement` directs) ; le workflow est une simple `List` ordonnée
(`suivant()` = `index + 1`), pas un moteur d'états générique ; `DaoGenerique` n'offre que les
opérations **réellement** communes (lecture/suppression), les `insert`/`update` n'étant écrits que là
où on en a besoin ; l'application étant mono-utilisateur, `idUtilisateurCourant` est simplement le
premier utilisateur (aucune machinerie d'authentification construite « au cas où »).

### KISS (« reste simple »)

**Ici.** SQLite fichier (pas de serveur), tests headless **en mémoire** (pas de `xvfb`), capture par
`Scene.snapshot()` (pas d'orchestration lourde).

### DRY (« ne te répète pas »)

**Ici.** `DaoGenerique` (Template Method) et `RowMapper` (Strategy) factorisent la boucle `ResultSet`
écrite **une seule fois** ; les sections communes de doc renvoient à une source unique.

### Tell, Don't Ask

Demander à un objet d'**agir**, plutôt que de lire son état pour décider à sa place.

**Ici.** `MoteurWorkflowPassage.exigerTransitionAutorisee(actuel, cible)` **vérifie et lève** si la
transition est interdite, au lieu d'exposer l'ordre pour que chaque appelant le re-teste.

### Composition plutôt qu'héritage

**Ici.** Le chrome **compose** des capacités via de petites interfaces optionnelles (ISP) détectées à
l'exécution, et l'injection **compose** le graphe d'objets — au lieu d'une hiérarchie de classes
profonde. (`DaoGenerique` reste un héritage **assumé**, limité au Template Method.)

### Convention plutôt que configuration

**Ici.** Les `.fxml`/`.css` à côté de leur controller, les paquets de test en **miroir** de la
production, le `captures.manifest`, les noms `Capture*` / `Navigation*` / `*Module` : autant de
conventions qui évitent de la configuration.

### Fail-fast

**Ici.** `exigerTransitionAutorisee` lève **tôt** ; `Objects.requireNonNull(...)` garde les
constructeurs ; `DataAccessException` remonte une erreur SQL sans la masquer.
