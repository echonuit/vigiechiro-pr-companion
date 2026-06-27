# Ajouter une fonctionnalité

Ce guide montre comment greffer un **nouvel écran/parcours** en respectant l'architecture
(cf. [Architecture](architecture.md)). Le fil conducteur : on crée un **paquet feature** autonome,
on le câble à l'injection, puis on le **branche à la navigation** sans casser les frontières.

!!! tip "Le meilleur point de départ : copier une feature voisine"
    Une feature simple comme `bibliotheque/` ou `diagnostic/` est un bon gabarit. Calquez sa
    structure, les tests suivront le même moule.

## 1. Créer le paquet et ses 4 couches

Sous `src/main/java/fr/univ_amu/iut/`, créez `mafeature/` avec les 4 sous-paquets. Chacun a une
**règle stricte** (vérifiée par ArchUnit, cf. [Architecture](architecture.md)) :

```
mafeature/
├── model/         ← entités (records) + services + model/dao/ (SQLite)   — AUCUN JavaFX
├── viewmodel/     ← état observable (javafx.beans.property)              — pas de javafx.scene/fxml/stage
├── view/          ← Controller + MaFeature.fxml + mafeature.css          — ne touche jamais la base
└── di/            ← MaFeatureModule (Guice)                               — assemble la feature
```

## 2. Le modèle (`model/`)

- Une **entité** en `record` (immuable), p. ex. `record Truc(Long id, String nom)`.
- Un **DAO** en `PreparedStatement` héritant du patron des autres `*/model/dao/` (pas d'ORM).
- Un **service** qui orchestre les DAO et porte la logique métier.
- Si le schéma change : ajoutez une **migration** `src/main/resources/db/migration/V0x__ma_table.sql`
  (numéro suivant). Elle s'applique automatiquement au démarrage.

!!! warning "Frontière"
    Rien ici n'importe JavaFX : le test `model_sans_javafx` y veille.

## 3. Le ViewModel (`viewmodel/`)

Expose l'état en **propriétés observables** et la logique de présentation :

```java
public class MaFeatureViewModel {
    private final ObservableList<Truc> trucs = FXCollections.observableArrayList();
    private final StringProperty message = new SimpleStringProperty("");
    public MaFeatureViewModel(ServiceMaFeature service) { /* ... */ }
    public ObservableList<Truc> trucs() { return trucs; }
    public StringProperty messageProperty() { return message; }
}
```

!!! warning "Frontière"
    Uniquement `javafx.beans` ici : pas de `javafx.scene/fxml/stage` (test `viewmodel_sans_javafx_ui`).

## 4. La vue (`view/`)

- Un **`MaFeature.fxml`** + un **`MaFeatureController`** qui **se lie** au ViewModel (binding) et ne
  touche **jamais** la base directement (test `view_sans_jdbc`).
- Le controller est **injecté** : il reçoit son ViewModel par constructeur `@Inject`.

```java
public class MaFeatureController {
    @FXML private TableView<Truc> table;
    @Inject public MaFeatureController(MaFeatureViewModel vm) { this.vm = vm; }
    @FXML private void initialize() { table.setItems(vm.trucs()); /* bindings... */ }
}
```

## 5. Le module Guice (`di/`) + la racine

Un module qui publie service/VM, puis on l'ajoute à la **racine de composition** :

```java
public class MaFeatureModule extends AbstractModule {
    @Provides MaFeatureViewModel vm(ServiceMaFeature s) { return new MaFeatureViewModel(s); }
}
```

Enregistrez `new MaFeatureModule()` dans
[`RacineInjecteur`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/di)
(et dans le harnais de tests qui compose un injecteur partiel, le cas échéant).

## 6. Brancher la navigation (inversion de dépendance)

Pour qu'un **autre** écran ouvre le vôtre **sans dépendre de votre `view`**, suivez le patron
`Ouvrir*` (cf. [Architecture](architecture.md#navigation-et-decouplage-inter-feature)) :

1. **Publier le contrat** dans le socle `commun/view/OuvrirMaFeature.java` :
   ```java
   public interface OuvrirMaFeature { void ouvrir(Long id); }
   ```
2. **L'implémenter** dans `mafeature/view/NavigationMaFeature.java` (charge le FXML via la
   `controllerFactory` Guice, appelle `controleur.ouvrirSur(...)`, puis `navigateur.empiler(...)`).
   Calquez
   [`NavigationPassage`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/view/NavigationPassage.java).
3. **Le binder** dans `MaFeatureModule` : `bind(OuvrirMaFeature.class).to(NavigationMaFeature.class);`.
4. L'écran appelant **injecte** `OuvrirMaFeature` et appelle `ouvrir(...)`.

!!! note "Entrée depuis l'accueil ?"
    Si votre écran est une **activité d'accueil** (carte sur la page d'accueil), publiez une
    `ActiviteAccueil` (cf. les `Activite*` existantes) : le `MainController` peuple les cartes
    automatiquement.

!!! note "Données modifiées par une sous-activité ?"
    Si votre écran affiche des données qu'un écran ouvert par-dessus peut changer, implémentez
    [`RafraichirAuRetour`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/RafraichirAuRetour.java)
    sur le controller : le `Navigateur` le recharge au retour.

## 7. Ajouter un aperçu (capture d'écran)

Les écrans documentés ont un aperçu PNG régénéré en CI. Pour le vôtre :

- Écrivez `mafeature/outils/CaptureMaFeature.java` sur le patron des `Capture*` existants : il rend la
  vue **hors-écran** via
  [`ApercuFx`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/ApercuFx.java)
  (Headless Platform), sur une base SQLite jetable seedée.
- Ajoutez la classe à
  [`.github/assets/capture-screenshots.sh`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/assets/capture-screenshots.sh)
  et l'aperçu au manifeste `.github/assets/captures.manifest`.
- Le workflow **« Aperçus des vues »** régénère les PNG à chaque push sur `main`.

!!! tip "Écran avec écoute audio ?"
    Réutilisez
    [`SonDemo`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/SonDemo.java)
    (WAV de synthèse) +
    [`AttenteAudio`](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/AttenteAudio.java)
    pour afficher un spectrogramme réel dans la capture.

## 8. Tester

- **ViewModel / service** : tests unitaires (JUnit 5 + AssertJ), Mockito pour les dépendances.
- **Vue** : test d'intégration **TestFX** (headless) qui charge le FXML et vérifie les bindings.
- **Architecture** : rien à écrire, `ArchitectureTest` couvre vos frontières automatiquement.
- **Parcours complet** : un test `fr.univ_amu.iut.e2e.*` si votre écran s'inscrit dans un flux.

Détails et pièges dans [Tests et qualité](tests-et-qualite.md).

## Checklist avant la PR

- [ ] Les 4 couches respectent leurs frontières (`./mvnw test` → `ArchitectureTest` vert).
- [ ] Module Guice enregistré dans `RacineInjecteur` (l'app démarre : `./mvnw javafx:run`).
- [ ] Navigation branchée par contrat `Ouvrir*` si ouverte depuis un autre écran.
- [ ] Capture + manifeste si l'écran est documenté.
- [ ] Tests verts et **`./mvnw -Pquality-gate verify`** vert (PMD + couverture).
- [ ] Commits en **Conventional Commits** (cf.
      [CONTRIBUTING.md](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md)).
