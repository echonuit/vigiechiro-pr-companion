# Architecture

L'application est **locale** (base SQLite fichier, sans serveur), en **JavaFX 26 / Java 25**, injectée
par **Guice 7**. L'organisation est en **paquet-par-fonctionnalité** : chaque écran/parcours vit dans
**son propre paquet**, qui contient lui-même ses **4 couches MVVM**.

!!! tip "Les patrons derrière cette architecture"
    Chaque choix (MVVM, contrats, DAO, Template Method…) applique un **patron de conception** au
    service des principes **SOLID**. La page **[Patterns et principes](patterns.md)** les détaille un
    par un (le quoi, le pourquoi ici, et les principes servis).

## Carte du code

```
src/main/java/fr/univ_amu/iut/
├── App.java                     ← point d'entrée JavaFX (amorçage Guice + chrome)
├── module-info.java             ← module JPMS « vigiechiro » (open module)
│
├── commun/                      ← LE SOCLE partagé par toutes les fonctionnalités
│   ├── persistence/             ·   infrastructure DAO (SQLite, transactions, migrations)
│   ├── model/                   ·   domaine transverse (Horloge, Prefixe, Verdict, Statut...)
│   ├── viewmodel/               ·   état observable du chrome (NavigationViewModel...)
│   ├── view/                    ·   chrome (MainView, Navigateur) + contrats Ouvrir* / RafraichirAuRetour
│   ├── di/                      ·   racine de composition Guice (RacineInjecteur, modules socle)
│   └── outils/                  ·   harnais de capture d'écran (ApercuFx...)
│
├── sites/        passage/       importation/   qualification/   lot/
├── validation/   multisite/     diagnostic/    bibliotheque/    recherche/
├── analyse/      audio/         audit/         connexion/       maj/          ← les 15 features
│
├── cli/                         ← interface en ligne de commande (import/export scriptables)
└── perf/outils/                 ← bancs de mesure de performance
```

Les deux dernières entrées sont des **surfaces transverses**, documentées à part :
[`cli/`](cli.md) (seconde façade *headless* sur le métier) et
[`perf/outils/`](performance.md) (bancs de mesure des objectifs O3/O5).

## Les 4 couches d'une fonctionnalité

Chaque feature (ex. `sites/`) suit le même découpage, **du métier vers l'IHM** :

| Sous-paquet | Rôle | Règle clé |
|---|---|---|
| `model/` | Entités (records), services, et `model/dao/` (accès SQLite) | **Aucune** dépendance JavaFX (réutilisable, testable seul) |
| `viewmodel/` | État observable + logique de présentation | Importe **`javafx.beans`** uniquement, **jamais** `javafx.scene/fxml/stage` |
| `view/` | `Controller` + `*.fxml` + `*.css` | Se **lie** aux propriétés du ViewModel ; ne parle **jamais** à la base |
| `di/` | Le module Guice qui assemble la feature | Publie ses services/VM au conteneur |

!!! note "Le sens MVVM"
    Le `model` ignore l'IHM ; le `viewmodel` porte l'état en **propriétés observables**
    (`IntegerProperty`, `ObservableList`…) sans toucher aux composants graphiques ; la `view`
    **observe** le viewmodel par *data binding* JavaFX.

!!! info "Une feature peut être *sans écran*"
    Toutes n'ont pas les 4 couches. `recherche/` (#144) est une feature **de service** : `model` + `di`
    seulement, **pas de `view`**. La recherche globale est exposée par un contrat
    [`commun.model.RechercheGlobale`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/model/RechercheGlobale.java)
    (*Separated Interface*), implémenté par `recherche.model.ServiceRechercheGlobale` qui **agrège**
    les autres features, et **surfacé par le chrome** (la barre de recherche de `MainView`) plutôt que
    par un écran dédié.

## Les règles d'architecture sont des tests

Ces frontières ne sont pas qu'une convention : elles sont **vérifiées automatiquement** par
[**ArchUnit**](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/test/java/fr/univ_amu/iut/architecture/ArchitectureTest.java).
Casser une frontière fait **échouer la CI**.

!!! warning "Sauf si la dépendance se réduit à une constante"
    ArchUnit lit le **bytecode**, et une dépendance qui ne consiste qu'en une **constante compile-time**
    n'y laisse aucune trace : le compilateur inline la valeur, le `.class` ne cite jamais la classe qui la
    déclarait (JLS 13.1). Toutes les règles ci-dessous sont aveugles à ce cas — ce n'est pas un faux négatif
    isolé mais une **catégorie**, qui vaut pour toute `String`, `int` ou `boolean` constant.

    Le cas s'est produit : une commande CLI a cité le `viewmodel` d'une feature pour une clé de réglage
    sans qu'aucune règle ne bronche ([#2181](https://github.com/echonuit/vigiechiro-pr-companion/issues/2181)).
    D'où un **doublon au niveau des sources**, `IsolationFeatureSourcesTest`, qui lit l'`import` dans le
    `.java` — présent quoi qu'en fasse le compilateur. Mesuré : en réintroduisant volontairement une telle
    dépendance, les six règles ArchUnit restent **vertes** et ce test échoue.

    Il ne couvre que la règle d'**isolation inter-feature**. Un vert d'ArchUnit dit donc « aucune dépendance
    **dans le bytecode** », pas « aucune dépendance ».

| Test | Ce qu'il garantit |
|---|---|
| `model_sans_javafx` | Aucun paquet `..model..` ne dépend de `javafx..` (le métier reste réutilisable). |
| `persistance_sans_javafx` | `commun.persistence` et `..model.dao..` ignorent JavaFX. |
| `viewmodel_sans_javafx_ui` | Un `..viewmodel..` n'importe pas `javafx.scene/fxml/stage` (`javafx.beans` OK). |
| `view_sans_jdbc` | Un `..view..` ne touche jamais `..model.dao..` ni `java.sql..` : il passe par les VM/services. |
| `features_sans_cycle` | Les slices `fr.univ_amu.iut.(*)` sont **sans cycle** (hors racine de composition `commun.di`). |
| `pas_de_dependance_inter_feature_vers_la_vue` | Une feature ne dépend pas du `view`/`viewmodel` d'une **autre** feature (le socle `commun` est partagé). |

## Navigation et découplage inter-feature

Le chrome (fenêtre + zone centrale + fil d'Ariane) est porté par le socle
[`commun.view`](https://github.com/echonuit/vigiechiro-pr-companion/tree/main/src/main/java/fr/univ_amu/iut/commun/view) :

- [`Navigateur`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/Navigateur.java)
  pilote la zone centrale et l'historique (pile d'écrans `EtapeNavigation`). Les écrans restent
  **vivants** au retour (état préservé).
- Contrats **optionnels** qu'un écran peut implémenter sur son controller :
  [`GardeQuitter`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/GardeQuitter.java)
  (confirmer la sortie si saisie non enregistrée),
  [`EmplacementNavigation`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/EmplacementNavigation.java)
  (fil d'Ariane hiérarchique),
  [`RafraichirAuRetour`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/view/RafraichirAuRetour.java)
  (recharger ses données quand on y revient).

**Comment une feature en ouvre une autre sans en dépendre** (inversion de dépendance) : le socle
publie un **contrat** `Ouvrir*` dans `commun.view`, la feature cible l'**implémente** via sa classe
`Navigation*` (bindée par son module Guice), et la feature appelante l'obtient **par injection**.

```java
// commun/view/OuvrirPassage.java — le CONTRAT (dans le socle)
public interface OuvrirPassage {
    void ouvrir(Long idPassage, ContexteSite contexte);
}

// passage/view/NavigationPassage.java — l'IMPLÉMENTATION (dans la feature passage)
@Singleton
public class NavigationPassage implements OuvrirPassage {
    @Override public void ouvrir(Long idPassage, ContexteSite contexte) {
        FXMLLoader loader = new FXMLLoader(NavigationPassage.class.getResource("Passage.fxml"));
        loader.setControllerFactory(injector::getInstance);          // controller injecté par Guice
        Parent vue = loader.load();
        ((PassageController) loader.getController()).ouvrirSur(idPassage, contexte);
        navigateur.empiler(vue, "passage", controleur.libelleFil(), controleur);
    }
}
```

Ainsi `sites` ouvre M-Passage **sans dépendre de `passage.view`** : la règle ArchUnit
`pas_de_dependance_inter_feature_vers_la_vue` reste respectée. La **liste de référence** des contrats
`Ouvrir*` (**10**) est maintenue à un seul endroit : [Navigation](navigation.md#ouvrir-une-autre-feature-sans-en-dependre).

## Persistance

**SQLite** (fichier `vigiechiro.db` dans l'espace de travail **par défaut**, relocalisable via l'onglet
« Emplacements » des réglages, cf. [ADR 1038](decisions/1038-la-configuration-d-amorcage-vit-hors-de-la-base.md)), via des **DAO** écrits en
`PreparedStatement` (**pas d'ORM**). Le schéma évolue par **migrations versionnées**
[`src/main/resources/db/migration/V0x__*.sql`](https://github.com/echonuit/vigiechiro-pr-companion/tree/main/src/main/resources/db/migration),
appliquées par
[`MigrationSchema`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence)
sur la [`SourceDeDonnees`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/persistence).

Le cœur du domaine est l'**agrégat « nuit de capture »** (feature `passage`), qui avance dans un
**workflow à états** :

`IMPORTE → TRANSFORME → VERIFIE → PRET_A_DEPOSER → DEPOT_EN_COURS → DEPOSE` (6 états, cf. `StatutWorkflow`)

Le détail des entités, des tables (**28** au schéma courant, 19 à l'origine en `V01`) et de la correspondance avec le **MCD du brief** est sur
[Modèle de données et domaine](modele-de-donnees.md) ; le **mécanisme** d'accès (DAO, transactions,
migrations) sur [Persistance](persistance.md).

## Injection de dépendances

[`RacineInjecteur`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/src/main/java/fr/univ_amu/iut/commun/di)
assemble le socle + tous les modules `*/di/*Module.java` des features. Les `Controller` FXML sont
eux aussi **injectés** via une `controllerFactory` posée sur le `FXMLLoader`. Certaines valeurs
transverses sont fournies par binding nommé (ex. `@Named("idUtilisateurCourant")`, l'utilisateur
courant de l'application mono-utilisateur).

---

Pour mettre tout cela en pratique, suivez **[Ajouter une fonctionnalité](ajouter-une-fonctionnalite.md)**.
