# Ajouter une fonctionnalité

Ce guide montre comment greffer un **nouvel écran/parcours** en respectant l'architecture
(cf. [Architecture](architecture.md)). Le fil conducteur : on crée un **paquet feature** autonome,
on le câble à l'injection, puis on le **branche à la navigation** sans casser les frontières.

!!! tip "Le meilleur point de départ : copier une feature voisine"
    Une feature simple comme `bibliotheque/` ou `diagnostic/` est un bon gabarit. Calquez sa
    structure, les tests suivront le même moule.

!!! danger "Cette numérotation décrit une ANATOMIE, pas une chronologie"
    Les huit sections ci-dessous disent **de quoi une feature est faite**, dans l'ordre où on la lit.
    Elles ne disent **pas** dans quel ordre on l'écrit.

    Dans le temps, **le test vient en premier** : le cycle de travail est
    [rouge, vert, refactor](cycle-de-chantier.md#pendant-lissue-rouge-vert-refactor-autant-de-fois-quil-le-faut), à chaque
    comportement. Jusqu'à #3505 cette page numérotait « Tester » en **8 sur 8**, ce qui se lisait comme
    une consigne d'écrire les tests en dernier - et c'en était une. La section 8 rassemble donc les
    **outils et niveaux** de test, pas le moment où l'on s'en sert.

!!! note "Avant le premier test : ce que le produit doit FAIRE"
    Une fonctionnalité neuve change ce que le produit fait, donc l'étape **2b** de l'ouverture de
    chantier s'applique : la capacité se décrit dans la
    [spécification vivante](cycle-de-chantier.md) avant d'être écrite, par `/instruire` puis
    `/proposer`. Les tâches du changement deviennent alors les lots du chantier, et se cochent dans
    les commits qui les réalisent.

    Une **capacité** est ici la capacité métier au sens de
    [l'ADR 0014](decisions/0014-parite-cli-ihm.md), nommée `<paquet>/<geste>` :
    `passage/emport-d-une-nuit`. Son grain est le geste, pas l'écran - une capacité traverse
    volontiers l'écran et la ligne de commande.

    Cette page décrit ensuite **comment** on l'écrit. La spécification dit **quoi**, et elle survit à
    la fermeture de l'issue.

## 1. Créer le paquet et ses 4 couches

Sous `src/main/java/fr/univ_amu/iut/`, créez `mafeature/` avec les 4 sous-paquets. Chacun a une
**règle stricte** (vérifiée par ArchUnit, cf. [Architecture](architecture.md)) :

```
mafeature/
├── model/         ← entités (records) + services + model/dao/ (SQLite)   : AUCUN JavaFX
├── viewmodel/     ← état observable (javafx.beans.property)              : pas de javafx.scene/fxml/stage
├── view/          ← Controller + MaFeature.fxml + mafeature.css          : ne touche jamais la base
└── di/            ← MaFeatureModule (Guice)                               : assemble la feature
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

!!! note "Où placer les `.fxml` / `.css`"
    **À côté du controller**, dans `src/main/java/.../view/` (pas dans `src/main/resources`). Le
    `pom.xml` copie les fichiers non-Java de `src/main/java` dans `target/classes` au même chemin de
    paquetage. Seules les ressources **partagées** (migrations `db/migration`, thème) vivent dans
    `src/main/resources`.

```java
public class MaFeatureController {
    @FXML private TableView<Truc> table;
    @Inject public MaFeatureController(MaFeatureViewModel vm) { this.vm = vm; }
    @FXML private void initialize() { table.setItems(vm.trucs()); /* bindings... */ }
}
```

!!! tip "Tables : densité uniforme et colonnes configurables"
    Deux aides du socle
    [`commun.view`](https://github.com/echonuit/vigiechiro-pr-companion/tree/main/src/main/java/fr/univ_amu/iut/commun/view)
    rendent une table cohérente avec le reste de l'application :

    - **`TableDonnees.uniformiser(table)`** (ou `uniformiserNavigable`) : densité, `placeholder` et
      habillage communs (#690).
    - **`GestionnaireColonnes.installer(table, menu, colonnes)`** : offre « Colonnes… » (panneau
      *masquer / réordonner* façon Notion) au **clic droit** de la table **et** dans un `MenuButton` ☰
      « outils ». Décrivez les colonnes avec `colonnesParDefaut(table)` (en-tête = libellé, colonne de
      tête = identité verrouillée) ou une `List<GestionnaireColonnes.Colonne>` à la main quand
      l'identité est ailleurs (ex. Qualification) ou que les en-têtes sont des icônes.

    Une action de clic droit propre à la vue (ex. « Fiche de l'espèce ») se **compose** :
    `installer(table, menu, colonnes, itemAction)` la place **avant** « Colonnes… », sans l'écraser. Une
    vue à **plusieurs tables** mais un **seul** ☰ (ex. Analyse : espèces/carrés/observations) câble
    chaque table par `installerClicDroit(table, colonnes, …)` et fait pointer le ☰ vers la table active
    via `GestionnaireColonnes.ouvrir(...)`.

    **Actions de ligne et double-clic (#1792)** : une table de données offre aussi les gestes sous le
    curseur, dans un **ordre stable d'un écran à l'autre** (action principale, actions secondaires,
    `Validation ▸`, `Copier ▸`, puis « Colonnes… » toujours en dernier). Le socle fournit
    `DoubleClicLigne.installer(table, action)` (qui pose du même coup la sélection au clic droit, sans
    casser une sélection multiple), `MenuLigne.item(libelle, table, action)`,
    `MenuCopier.creer(table, Entree…)` et `ActionVigieChiroPassage.item(…)`. Le **double-clic reste le
    miroir de l'action principale** du menu, et toute action qu'il déclenche doit **rendre compte**
    quand elle n'aboutit pas - un geste sans état visible ne peut pas être muet :
    voir [ADR 0021](decisions/0021-double-clic-miroir-qui-rend-compte.md) et la section
    [Actions de ligne d'une table](patterns.md#actions-de-ligne-dune-table-double-clic-et-menu-contextuel-socle-commun).

    **Persistance (#994)** : pour retenir la disposition **par écran** (ordre + visibilité restaurés à la
    réouverture), remplacez `installer` par `installerEtPersister(table, menu, colonnes, depotColonnes,
    feature, cle, …)` (le controller injecte `DepotDispositionColonnes`). Pour que les **vues mémorisées**
    (#623) capturent aussi les colonnes, passez un `AdaptateurColonnes` à `GestionnaireVues` :
    `GestionnaireColonnes.adaptateurMonoTable(cle, table, colonnes)` pour une table, ou un adaptateur à
    plusieurs entrées de map pour une vue multi-tables.

!!! tip "Tables exploratoires : la barre de filtres du socle"
    Si votre table **explore un corpus** (par opposition au détail d'un seul objet), elle prend la barre
    « à la Notion » plutôt qu'un filtre écrit à la main. Six écrans la portent ; en écrire un septième à
    la main serait la duplication que le chantier #3092 a supprimée.

    Cinq gestes, dans cet ordre :

    1. le **ViewModel** expose deux listes - `trucs()` (tout) et `trucsFiltres()` (une `FilteredList`) -
       plus son `Filtres<Truc>` ;
    2. la **table** se pose sur une `SortedList` **par-dessus** la liste filtrée, comparateur lié à celui
       de la table. Une `FilteredList` posée nue est **non modifiable** : `TableView` renonce alors à
       trier et **vide son `sortOrder` en silence** ;
    3. un **catalogue** `CriteresMaFeature` déclare les critères (`CritereListe`, `CritereBooleen`,
       `CritereLieu`) ; les clés **partagées** viennent de `ClesCriteres`, les clés propres restent chez
       vous ([ADR 3096](decisions/3096-une-cle-de-critere-est-un-contrat-de-serialisation.md)) ;
    4. un collaborateur `FiltresVuesMaFeature` assemble barre, vues mémorisées et mémoire de session -
       le contrôleur lui passe les nœuds du FXML regroupés, ce qui le garde sous le plafond `GodClass` ;
    5. le FXML gagne la barre **et le bandeau de retour** : sans lui, la mémoire de session remettrait
       des filtres amputés en silence
       ([ADR 3093](decisions/3093-une-restauration-rend-compte-de-deux-causes.md)).

    Deux règles à ne pas rater, parce qu'elles ne font rougir aucun test :

    - le **domaine** d'une puce se calcule sur `filtres().saufLui(CLE)`, jamais sur la liste affichée -
      sinon la puce s'auto-effondre sur la valeur déjà cochée
      ([ADR 3095](decisions/3095-un-domaine-se-calcule-sans-son-propre-critere.md)) ;
    - si l'écran porte un **verdict ou un résumé** sur l'ensemble, il se calcule sur la liste **non
      filtrée** ([ADR 3092](decisions/3092-un-filtre-ne-change-que-ce-quon-regarde.md)).

    Enfin, le décompte des critères s'ancre dans la fiche d'écran par une balise
    `<!--inv:criteres-mafeature-->N<!--/inv-->`, et la clé s'ajoute à `DocumentationAJourTest`.

## 5. Le module Guice (`di/`) + l'auto-découverte

Un module qui publie service/VM, **hérité de `ModuleDeFeature`** (le DSL du socle) :

```java
public class MaFeatureModule extends ModuleDeFeature {
    @Override public Fonctionnalite fonctionnalite() {                 // identité + feature-flag (obligatoire)
        return new Fonctionnalite("mafeature", "Ma fonctionnalité", Categorie.OPTIONNELLE);
    }
    @Override protected void configure() {
        activite(ActiviteMaFeature.class);   // carte d'accueil (optionnel)
        // indicateur(...), ongletReglages(...), actionMenu(...) au besoin
    }
    @Provides MaFeatureViewModel vm(ServiceMaFeature s) { return new MaFeatureViewModel(s); }
}
```

**On ne touche PAS `RacineInjecteur`** : les modules de feature sont **auto-découverts**
(`ServiceLoader<ModuleDeFeature>`, cf. [Injection](injection.md#la-racine-de-composition)). Déclarez
`MaFeatureModule` comme service dans les **deux** listes (gardées synchronisées par
`DecouverteModulesTest`) :

- `src/main/resources/META-INF/services/fr.univ_amu.iut.commun.di.ModuleDeFeature` (une ligne : le FQN
  du module) : chemin **classpath** (tests, fat-jar) ;
- `module-info.java` : ajoutez le module au `provides fr.univ_amu.iut.commun.di.ModuleDeFeature with …`
 , chemin **module-path** (`javafx:run`).

!!! tip "Contribuer aux points d'extension"
    Une feature peut aussi ajouter un **compteur** d'accueil (`indicateur(...)`), un **onglet de
    réglages** (`ongletReglages(...)`, cf. `OngletReglages` + `DescripteurReglage`) et une **entrée de
    menu principal (☰)** (`actionMenu(...)`, cf. `ActionMenu`) : toujours sans toucher le socle.

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
   [`NavigationPassage`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/passage/view/NavigationPassage.java).
3. **Le binder** dans `MaFeatureModule` : `bind(OuvrirMaFeature.class).to(NavigationMaFeature.class);`.
4. L'écran appelant **injecte** `OuvrirMaFeature` et appelle `ouvrir(...)`.

!!! note "Entrée depuis l'accueil ?"
    Si votre écran est une **activité d'accueil** (carte sur la page d'accueil), publiez une
    `ActiviteAccueil` (cf. les `Activite*` existantes) : le `MainController` peuple les cartes
    automatiquement.

!!! note "Données modifiées par une sous-activité ?"
    Si votre écran affiche des données qu'un écran ouvert par-dessus peut changer, implémentez
    [`RafraichirAuRetour`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/RafraichirAuRetour.java)
    sur le controller : le `Navigateur` le recharge au retour.

!!! note "Données modifiées pendant qu'on les regarde ?"
    C'est une **autre** question, et elle a une autre réponse. Un import, une synchronisation, une
    restauration écrivent sans que l'utilisateur ait quitté l'écran : le retour ne se produit jamais.
    Déclarez alors
    [`SuitLaRevision`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/SuitLaRevision.java)
    et implémentez `rafraichirDepuisLaDonnee()`. Le `Navigateur` s'occupe de l'abonnement et de sa
    restitution, cf. [Patrons › Le signal de mutation](patterns.md#le-signal-de-mutation-tu-ecris-tu-signales).

    Les deux contrats se déclarent **ensemble** : ils ne couvrent pas les mêmes écritures. Et si votre
    écran **écrit**, la règle vaut aussi dans l'autre sens : tu écris, tu signales.

!!! note "Un `Bindings.create…Binding` ?"
    Sa liste de dépendances doit énoncer **tout** ce que son calcul lit, **méthodes appelées comprises**.
    Rien ne le vérifie : un binding incomplet affiche juste tant qu'une autre propriété change au bon
    moment. Un cliquet compte les sites pour qu'un nouveau soit **vu**, cf.
    [Patrons › Un binding déclare tout ce qu'il lit](patterns.md#un-binding-declare-tout-ce-quil-lit).

!!! note "Un bouton grisé, un contrôle masqué, un libellé calculé ?"
    Ne les **posez** pas au montage. Un `setDisable(!x)` ou un `setVisible(x)` lu dans `initialize()`
    fige la valeur de cet instant, et l'écran restant vivant dans la pile du `Navigateur`, aucune
    reconstruction ne la reprendra. Faites porter l'état par une **propriété**, et liez le contrôle
    (`disableProperty().bind(...)`, `VisibiliteGeree.lier(...)`), cf.
    [Patrons › Un état de contrôle se lie](patterns.md#un-etat-de-controle-se-lie-il-ne-se-photographie-pas).

    Trois exceptions **légitimes**, où le fait ne peut pas changer en cours de session : ce qui dérive
    d'un drapeau de fonctionnalité, d'une ressource embarquée, ou ce qui vit dans une modale rebâtie à
    chaque ouverture.

    Et si le **libellé de navigation** de votre écran dérive de la donnée, il se relibelle : cf.
    [Navigation › Un libellé dérivé de la donnée](navigation.md#un-libelle-derive-de-la-donnee-se-relibelle).

### Développer une feature derrière un flag

Le champ `Categorie` de `fonctionnalite()` pilote la **désactivabilité** de la feature (cf.
[Injection › Feature-flags](injection.md#feature-flags)) :

- **`OPTIONNELLE`** : feature autonome, **active par défaut**, que l'utilisateur peut couper depuis
  l'onglet « Fonctionnalités » des Réglages.
- **`EXPERIMENTALE`** : feature **inactive par défaut**. C'est le mode **trunk-based** : on merge une
  feature en cours de dev sur `main` **sans l'exposer**, puis on l'active à la demande
  (`-Dvigiechiro.features.<id>=on` en dev/CI, ou l'interrupteur des Réglages) jusqu'à ce qu'elle passe
  `OPTIONNELLE`.
- **`COEUR`** : le **défaut sûr** pour une feature dont un autre écran dépend (voir l'avertissement).

!!! warning "Une feuille n'est désactivable que si son contrat `Ouvrir*` est neutralisé"
    Si un **autre** écran ouvre le vôtre via un `Ouvrir*` injecté **non optionnel**, couper votre
    feature casserait cet écran : elle doit rester `COEUR`. Pour la rendre **réellement désactivable**,
    neutralisez son contrat chez le consommateur :

    1. déclarez le contrat en **`OptionalBinder`** avec un **défaut inerte** (`newOptionalBinder(binder(),
       OuvrirMaFeature.class).setDefault().toInstance(id -> {})`), votre module faisant
       `.setBinding().to(NavigationMaFeature.class)` ;
    2. côté écran appelant, injectez `Optional<OuvrirMaFeature>` et **masquez le point d'entrée**
       (bouton/onglet) quand il est absent ;
    3. passez la `Categorie` à `OPTIONNELLE`/`EXPERIMENTALE` et ajoutez le cas au test « désactiver la
       feature laisse l'injecteur constructible » (`DecouverteModulesTest`).

    `import-vigiechiro` est la **feature de référence** (déjà pleinement optionnelle).

## 7. Ajouter un aperçu (capture d'écran)

Les écrans documentés ont un aperçu PNG régénéré en CI. Pour le vôtre :

- Écrivez `mafeature/outils/CaptureMaFeature.java` sur le patron des `Capture*` existants : il rend la
  vue **hors-écran** via
  [`ApercuFx`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/ApercuFx.java)
  (Headless Platform), sur une base SQLite jetable seedée.
- Ajoutez la classe à
  [`.github/assets/capture_screenshots.py`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/assets/capture_screenshots.py)
  et l'aperçu au manifeste `.github/assets/captures.manifest`.
- Le workflow **« Aperçus des vues »** régénère les PNG à chaque push sur `main`.

!!! tip "Écran avec écoute audio ?"
    Réutilisez
    [`SonDemo`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/SonDemo.java)
    (WAV de synthèse) +
    [`AttenteAudio`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/outils/AttenteAudio.java)
    pour afficher un spectrogramme réel dans la capture.

## 8. Les niveaux de test (à écrire AVANT le code qu'ils couvrent)

Chaque puce ci-dessous répond à « **avec quoi** je teste ceci ? ». Le **quand** est réglé ailleurs et
ne dépend pas du niveau : le test s'écrit d'abord et on le voit **rouge**, sinon il faudra réintroduire
le défaut à la main pour savoir ce qu'il couvre vraiment
([cycle d'une issue](cycle-de-chantier.md#pendant-lissue-rouge-vert-refactor-autant-de-fois-quil-le-faut)).

- **ViewModel / service** : tests unitaires (JUnit 5 + AssertJ), Mockito pour les dépendances.
- **Vue** : test d'intégration **TestFX** (headless) qui charge le FXML et vérifie les bindings.
- **Geste** : si votre écran porte une action qui **écrase ou supprime** quelque chose, elle doit être
  **cliquée** dans un test, et son **refus** aussi. Cela suppose que ses dialogues passent par les
  ports du socle (`Confirmateur`, `Notificateur`, `SelecteurFichier`, `DemandeurDeChoix` : cf.
  [Patrons](patterns.md#les-dialogues-dune-action-sont-des-ports-socle-commun)). Un `showAndWait()` en
  dur - alerte **ou** sélecteur de fichier - **fige** le test : le geste redevient intestable, et vous
  ne saurez que son bouton existe.
- **Formulaire** : si votre écran demande une **saisie** (créer, modifier, paramétrer), ce n'est **pas**
  un dialogue - c'est une **vue**. Faites-en une **modale** (FXML + controller + ViewModel + une entrée
  `ouvrirModale*` sur votre façade de navigation), comme `ModalePoint`, `ModaleSite` ou `ModaleSelection`.
  Un `Dialog<T>` bâti à la main rend le geste **injouable**, sa **validation** intestable, et sa capture
  de documentation **impossible** (il faudrait la reconstruire à la main - et elle dériverait).
- **Architecture** : rien à écrire, `ArchitectureTest` couvre vos frontières automatiquement.
- **Parcours complet** : un test `fr.univ_amu.iut.e2e.*` si votre écran s'inscrit dans un flux.

Détails et pièges dans [Tests et qualité](tests-et-qualite.md).

## Conventions de code et de commit

**Code** :

- formatage **Spotless / Palantir Java Format** (le *hook* pre-commit s'en charge ; sinon
  `./mvnw spotless:apply`) ;
- doc-comments **Markdown `///`** (JEP 467), pas de `/** */` HTML, gardé par `ConventionsDEcritureTest` ;
- **noms de classes en français**, sans accents dans les identifiants (`Navigateur`, `Passage`,
  `EtapeNavigation`…), gardé par `ConventionsDEcritureTest` ;
- **pas de tiret cadratin** : tiret simple, deux-points ou virgule. La règle vaut **partout** (doc,
  commentaires, chaînes affichées, styles, scripts, ateliers), et un garde la fait respecter
  ([ADR 2843](decisions/2843-typographie-cliquet-plutot-que-nettoyage.md)). Le glyphe reste permis là
  où il **est** la donnée plutôt qu'une ponctuation : la valeur absente (`Formats.VALEUR_ABSENTE`), un
  libellé de l'application **cité** entre guillemets français, une classe de caractères d'analyseur.

**Commits** : [Conventional Commits](https://www.conventionalcommits.org/fr/) **en français**, le
*scope* étant le nom de la feature ou du domaine (`feat(passage): …`). Le **type** pilote la version
publiée (`feat:` mineure, `fix:` patch, `BREAKING CHANGE:` majeure ; cf.
[CI/CD et release](ci-cd-release.md)). Petits commits logiques (un par préoccupation) ; toujours
**créer** un commit plutôt qu'amender.

!!! info "Le flux complet de contribution"
    Le parcours **fork → branche → PR** (reviewer automatique, identité git à vérifier) est décrit
    dans [CONTRIBUTING.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md).

## Checklist avant la PR

- [ ] Les 4 couches respectent leurs frontières (`./mvnw test` → `ArchitectureTest` vert).
- [ ] Module de feature déclaré comme service `ModuleDeFeature` dans les **deux** listes `ServiceLoader` (auto-découverte, cf. [Injection](injection.md#la-racine-de-composition)) - **pas** dans `RacineInjecteur` - et l'app démarre (`./mvnw javafx:run`).
- [ ] Navigation branchée par contrat `Ouvrir*` si ouverte depuis un autre écran.
- [ ] Capture + manifeste si l'écran est documenté.
- [ ] Tests verts, **`./mvnw -B test-compile pmd:pmd`** puis le cliquet 4617 vert, et la couverture
      tenue par `./mvnw -B verify -Djacoco.haltOnFailure=true`.
- [ ] Si le chantier porte un changement OpenSpec, **la tâche réalisée est cochée** dans les commits
      du travail, et le corps de la demande la nomme.
- [ ] Commits en **Conventional Commits** (cf.
      [CONTRIBUTING.md](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/CONTRIBUTING.md)).
