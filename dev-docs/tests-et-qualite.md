# Tests et qualité

La chaîne qualité tourne à **chaque push** (CI) et localement via `./mvnw`. Cette page est la
référence structurée ; le repo-root garde un mémo
[**TESTING.md**](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/TESTING.md).

## Les commandes

| Commande | Effet |
|---|---|
| `./mvnw test` | **Toute** la suite de tests |
| `./mvnw test -Dtest=SitesViewModelTest` | Une seule **classe** de test |
| `./mvnw test -Dtest=SitesViewModelTest#chargeLesSites` | Une seule **méthode** |
| `./mvnw verify` | Build complet : tests + couverture + contrôles (PMD/JaCoCo **non** bloquants) |
| `./mvnw -Pquality-gate verify` | **Portail qualité** : PMD `failOnViolation` + seuils JaCoCo **bloquants** |
| `./mvnw -Pmutation test` | Tests de **mutation** PIT (lent, à la demande) |
| `./mvnw pmd:check` | Rapport PMD seul (rapide) |
| `./mvnw spotless:check` / `spotless:apply` | Vérifie / applique le formatage |
| `./mvnw javafx:run` | Lance l'application |

!!! tip "Quand lancer `clean` ?"
    Le build Maven est **incrémental** : `./mvnw verify` réutilise `target/`. Après certains
    changements, une classe périmée peut y subsister et provoquer une erreur **trompeuse** (à la
    compilation ou au packaging) que `./mvnw clean verify` fait disparaître. Réflexe : en cas
    d'erreur inexpliquée alors que le code semble correct, **relancer avec `clean`**. Cas typiques :

    - **suppression ou renommage** d'une classe/méthode : l'ancien `.class` reste dans `target/` ;
    - **changement de dépendances** (`pom.xml`).

    La CI part **toujours** d'un checkout propre : ce piège est purement **local**. Le cas du
    **packaging** est réglé depuis #1188 : le fat-jar est attaché sous le classifier `shaded`, le shade
    ne re-traite plus sa propre sortie (packaging **idempotent**, garde-fou en CI).

## IHM testée en *headless* (sans X11 ni xvfb)

Les tests **TestFX** tournent en mémoire grâce à la **Headless Platform** de JavaFX : aucun `xvfb`,
aucun display. La config vit dans le bloc Surefire du `pom.xml`, **rien à régler** :

```xml
<glass.platform>Headless</glass.platform>   <!-- plateforme Glass logicielle -->
<prism.order>sw</prism.order>               <!-- rendu logiciel -->
<java.awt.headless>true</java.awt.headless>
<testfx.robot>glass</testfx.robot>          <!-- robot piloté par Glass -->
```

On **ne met pas** `testfx.headless=true` : ce flag réactiverait l'ancien bootstrap Monocle. Le
headless vient de `glass.platform=Headless`, pas de TestFX. Le `argLine` ajoute les `--add-opens` /
`--add-exports` requis par JavaFX (accès aux internes `com.sun.javafx.*` pour TestFX) + l'agent JaCoCo
+ l'agent **Mockito** (voir ci-dessous).

!!! note "Préparation à Java 26+"
    Sous Java 25 tout passe, mais deux signaux annoncent la bascule 26 :

    - **Agent Mockito explicite** *(fait)* : Mockito 5 s'auto-attachait dynamiquement (« A Java agent
      has been loaded dynamically… will be disallowed by default in a future release »). On passe
      désormais `byte-buddy-agent` en `-javaagent` via `maven-dependency-plugin:properties`
      (`${net.bytebuddy:byte-buddy-agent:jar}` dans l'`argLine`) : plus d'auto-attachement, prêt pour
      le JDK 26.
    - **`sun.misc.Unsafe`** *(à surveiller)* : le warning vient de **Guava** (transitif), pas du code
      du projet. Il disparaîtra avec une montée de version de Guava ; rien à faire ici pour l'instant.

!!! danger "Lancer les tests avec le bon JDK"
    Utilisez un **JDK 25 standard** (`25.0.2-open` / Temurin), **pas** un JDK packagé FX (`fx-zulu`) :
    ce dernier embarque JavaFX 25, masque les jars Maven FX 26 et fait échouer le headless
    (`NPE com.sun.glass.ui.PlatformFactory.getPlatformFactory()` : la Headless Platform n'existe qu'en
    FX 26). Comme la CI :
    ```bash
    export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
    ```

## La taxonomie des tests

Les tests vivent sous `src/test/java/fr/univ_amu/iut/`, en **miroir** des paquets de production.

| Catégorie | Emplacement | Vérifie |
|---|---|---|
| Unitaires métier | `<feature>/model/`, `<feature>/dao/`, `commun/persistence/`, `commun/model/` | Entités, services, DAO, migrations. Sans JavaFX. |
| ViewModel | `<feature>/viewmodel/` | État observable + logique de présentation, sans composant graphique. |
| Intégration de vue (TestFX) | `<feature>/view/*VueIntegrationTest` | La vue FXML se lie au ViewModel et réagit (headless). |
| **Geste** (TestFX) | `<feature>/view/*ViewTest` | Le bouton est **cliqué**, et on vérifie son **effet** (#1405). |
| Bout en bout | `fr.univ_amu.iut.e2e.*`, `<feature>/e2e/Parcours*E2ETest` | Le scénario complet : IHM → ViewModel → service → base. |
| **CLI shell (bats)** | `src/test/bats/*.bats` (fixtures partagées `helper.bash`) | La CLI **empaquetée** (fat-jar shadé), au niveau **processus** : arguments picocli, texte d'aide, **codes de sortie**, refus métier — ce que les tests Java in-process ne voient pas. `cli.bats` éprouve les commandes du chantier #1565 ; `cli-surface.bats` couvre le contrat **hors-ligne de chaque** sous-commande (aide, refus des options requises manquantes, exécution locale, refus sans jeton). Les chemins **réseau** effectifs (dépôt, import, traitement) restent à cadrer (#1592, stub local ou `-Papi-live`). Lancés en CI après le smoke-test du fat-jar (#1572, amorce). |
| Architecture (ArchUnit) | `architecture/ArchitectureTest` | Les **6 règles** de frontière MVVM (cf. [Architecture](architecture.md)). |
| **Documentation** | `documentation/DocumentationAJourTest` | Toute commande CLI a sa ligne, tout écran a sa fiche (#1458). |

Outils : **JUnit 5 + AssertJ + Mockito** ; **ApprovalTests** pour les sorties verbatim (CSV Tadarida
`_Vu` : le premier run produit un `*.received`, à approuver en `*.approved`).

### Tester un geste, pas un bouton

Un test qui vérifie qu'un bouton est **présent et actif** ne dit rien de ce qu'il fait. C'était
pourtant tout ce qu'on avait sur les actions **irréversibles** - purger les originaux de toutes les
nuits, restaurer la base, supprimer un passage et sa nuit, réimporter par-dessus les validations de
l'observateur. Et pas par négligence : un `showAndWait()` **fige** un test headless, donc le clic
était **impossible**.

Les dialogues d'une action sont désormais des **ports** remplaçables (`Confirmateur`, `Notificateur`,
`SelecteurFichier`, `DemandeurDeChoix` : cf.
[Patrons](patterns.md#les-dialogues-dune-action-sont-des-ports-socle-commun)).
Un test de geste les remplace par des doubles, **déclenche** l'action, et vérifie **ce qui s'est
passé** :

```java
controleur.confirmateur().definir(message -> { confirmations.add(message); return confirme; });
controleur.notificateur().definir((niveau, entete, message) -> annonces.add(entete));

robot.interact(() -> robot.lookup("#boutonSupprimer").queryButton().fire());

assertThat(sitesEnBase()).isEmpty();     // l'effet, pas « un mock a été appelé »
```

Trois exigences, dans l'ordre d'importance :

1. **Le refus.** Sur une action irréversible, « Annuler annule vraiment » est le test qui compte le
   plus - et c'est celui qui manquait partout.
2. **L'effet réel.** Quand la fixture le permet (vrai injecteur + vraie base), asserter que la ligne a
   **disparu de la base**, pas qu'un mock a reçu un appel.
3. **Le message de confirmation est un contenu.** Sur une suppression en cascade, c'est le seul
   avertissement que l'utilisateur recevra : vérifier qu'il annonce le gain, ce qui est conservé, et
   ce qui est **définitivement** perdu.
4. **Renoncer n'est pas abandonner.** Quand un dialogue offre plusieurs issues, l'une d'elles **détruit**
   souvent quelque chose et une autre **ne fait rien**. Les deux ferment le dialogue. Un test doit les
   **distinguer** - c'est le piège le plus coûteux de tout ce chantier.

!!! tip "Ce qu'aucun test ne verra"
    Trois défauts d'IHM de #1431 n'ont été trouvés qu'en **regardant une capture** : un libellé tronqué,
    un emoji qui ne se rend pas (#700), et une **réplique** de dialogue qui avait **dérivé** du vrai
    écran. Un geste testé n'est pas un écran regardé : rendez la capture, et **ouvrez-la**.

### Semer une nuit : `JeuDeDonneesPassage`

Le schéma est **profond** : une `observation` référence une `sequence`, qui référence une
`recording_session`, qui référence un `passage`, qui référence un `point`, un `site`, un `recorder` et un
`user`. Écrire un test sur **une observation** obligeait donc à connaître **sept tables** - et
**soixante-quinze** fichiers de test resemaient cette même chaîne à la main, en trois styles SQL
différents (#1258).

Ce n'était pas de la rigueur, c'était du **bruit** : le test parlait de la plomberie au lieu de parler de
ce qu'il vérifie. Et chaque migration de schéma coûtait autant de retouches que de copies.

```java
JeuDeDonneesPassage jeu = JeuDeDonneesPassage.dans(source)
        .carre("130711")
        .point("Z41")
        .semer();

long douteuse  = jeu.ajouterObservation("Pipkuh");
long validee   = jeu.ajouterObservationValidee("Nyclei");
long corrigee  = jeu.ajouterObservationCorrigee("Pipkuh", "Pippip");
```

Valeurs par défaut : utilisateur `u-1`, carré `640380`, point `A1`, enregistreur `SN-1` - celles que les
tests utilisaient déjà. Tout se surcharge avant `semer()`.

**Migration opportuniste**, pas de big bang : on bascule un fichier **quand on le retouche**. Les trois
styles SQL et les jeux de colonnes variables rendent une conversion mécanique **risquée**, et un test
converti trop vite est un test qu'on ne relit plus.

!!! warning "Un cliquet empêche de l'oublier"
    **Une migration opportuniste sans garde-fou est une migration qu'on oublie** - le même défaut que la
    doc, qui dérivait parce que rien ne rougissait. `CliquetFixturePassageTest` **épingle la liste** des
    tests qui sèment encore un passage à la main (**66** au 2026-07-14), et elle ne peut que **rétrécir** :

    - **ajouter un semeur de plus** → CI rouge (c'est le cas qui compte : sans lui, la dette repousserait
      aussi vite qu'on la coupe) ;
    - **en migrer un** → CI rouge aussi, jusqu'à ce qu'on **retire son nom** de la liste. Le geste est
      trivial, et c'est ce qui rend le progrès **visible**.

    Le compteur restant est donc **toujours exact**, sans que personne ait à s'en souvenir.

Deux limites, assumées :

- la fixture **ne migre pas** le schéma (les tests ne l'obtiennent pas tous de la même façon) et **ne sème
  aucun taxon** : le référentiel réel est déjà posé par `V02__seed_taxons.sql`, et réinsérer `Pipkuh`
  **viole la clé primaire** ;
- les **outils de capture** (`src/main/.../outils`) restent **autonomes** : ce sont des exécutables
  indépendants, la fixture de test ne leur est pas accessible, on accepte leur duplication.

### La documentation est tenue par un test

Une doc qui ment est **pire** qu'une doc absente : on la croit. Le dépôt l'avait déjà tranché pour les
**captures**, défendues par quatre garde-fous (cf. [Captures](captures.md)). Les **commandes** et les
**écrans**, eux, n'avaient rien - et ils ont dérivé, en silence : `dev-docs/cli.md` a documenté jusqu'à
**22 commandes sur 29**, et l'écran « Audit de cohérence » a vécu **sans aucune fiche** de sa livraison
(#1133) à la clôture de l'EPIC #1154. Aucune CI n'a rougi. Une relecture à la main les a trouvés.

`DocumentationAJourTest` comble l'asymétrie, en confrontant la doc non pas à une liste tenue à la main
(c'est exactement ce qui dérive) mais à la **vérité du câblage** :

| Ce qui est confronté | À quoi | Ce que ça empêche |
|---|---|---|
| Les sous-commandes de l'annotation `@Command` de `CommandeRacine` | Le tableau de `dev-docs/cli.md` | Une commande livrée, testée, verte en CI… et **introuvable** dans sa propre doc |
| Les `ActiviteAccueil` **liées dans l'injecteur** | La fiche `docs/ecrans/<pageDoc>.md` | Un **écran entier** offert à l'utilisateur, sans page |
| Les fiches présentes sur le disque | La `nav` de `mkdocs.yml` **et** le tableau de `docs/ecrans/index.md` | Une page que le site ne publie pas, ou qu'on ne peut atteindre depuis l'index de sa section |

Deux détails qui comptent :

- Les commandes sont lues **sur l'annotation**, par réflexion - jamais instanciées. Leurs constructeurs
  tirent des `Provider` qui **ouvrent la base** : les instancier ferait de l'E/S pour rien.
- `ActiviteAccueil.pageDoc()` est une méthode du **contrat**, pas une convention. Le nom de la fiche ne se
  déduit ni du titre (« Sons de référence » se documente dans `validation.md`) ni du paquet (la feature
  `audio` aussi) : il faut le **dire**. Le compilateur force donc à choisir une fiche, et le test refuse
  qu'elle soit absente.

## Les outils qualité

| Outil | Rôle | Bloquant ? |
|---|---|---|
| **ArchUnit** | Frontières MVVM + absence de cycles | Oui (tests) |
| **Spotless** (Palantir) | Format du code, via un *hook* pre-commit silencieux | Oui (`spotless:check` en CI) |
| **PMD** | *Code smells* | Bloquant **sous `-Pquality-gate`** |
| **JaCoCo** | Couverture | Seuils bloquants **sous `-Pquality-gate`** |
| **PIT** | Qualité des tests par **mutation** | Non (à la demande, `-Pmutation`) |

### Couverture et mutation

- **JaCoCo** : sous `-Pquality-gate`, seuils **bloquants** au niveau `BUNDLE` — **85 % de lignes** et
  **70 % de branches**. Les `**/outils/**` (capture d'écran, bancs de mesure) sont **exclus** : ils
  sont validés par exécution, pas par tests unitaires.
- **PIT** (`-Pmutation`) évalue si les tests **détectent** des mutations du code. Lent : à lancer à la
  demande, hors cycle normal.

## Ce qui bloque la CI

| Workflow | Commande | Bloquant ? |
|---|---|---|
| « Java CI » ([maven.yml](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml)) — tests **+ couverture** | `./mvnw -B verify -Djacoco.haltOnFailure=true` | **Oui** |
| « Quality gate » ([lint.yml](https://github.com/IUTInfoAix-S201/vigiechiro-pr-companion/blob/main/.github/workflows/lint.yml)) — formatage | `./mvnw -B spotless:check` | **Oui** |
| « Quality gate » — portail PMD | `./mvnw -B -Pquality-gate compile pmd:check` | **Oui** |

`lint.yml` vérifie aussi la **complétude des captures** (cf. [Captures](captures.md)). Une PR doit
passer **les deux** workflows (cf. [CI/CD et release](ci-cd-release.md)).

Deux invariants sont en plus **verrouillés** : un test fige le **plan d'exécution** des requêtes O5
(l'index ne doit pas régresser, cf. [Performance et benchmarks](performance.md)), et les **garde-fous**
PMD / ArchUnit ne se désactivent **jamais** pour « faire passer » un build (cf.
[Sécurité et données sensibles](securite.md)).

## Écrire un nouveau test

- Un test de vue part d'un **injecteur** (réel ou partiel) Guice et d'une **base jetable** (workspace
  temporaire + `MigrationSchema.migrer()`), comme les `*VueIntegrationTest` / `*E2ETest` existants.
- **TestFX** : interroger les nœuds par `fx:id` (`lookup("#monId")`), piloter via le robot Glass,
  asserter avec AssertJ. Préférer **`bouton.fire()`** à un clic robot quand c'est possible (plus
  stable en headless).
- Pour une capture déterministe, voir
  [Ajouter une fonctionnalité §7](ajouter-une-fonctionnalite.md#7-ajouter-un-apercu-capture-decran).

### Trois pièges récurrents

!!! warning "`assertThat(path).endsWith(Path)` canonicalise"
    Cette forme appelle `toRealPath` et lève `NoSuchFileException` si le dossier n'existe pas (erreur
    sur runner neuf). Préférer le booléen **lexical** : `assertThat(p.endsWith(autre)).isTrue()`.

!!! warning "`fire()` est un no-op sur un contrôle désactivé"
    `Button.fire()` comme `Hyperlink.fire()` vérifient `isDisabled()` **avant** d'émettre. Un test qui
    « clique » un contrôle grisé ne déclenche donc **rien**, et s'il attend un refus métier, il échoue
    sans dire pourquoi. Le plus souvent, c'est le **test** qui a tort : quand l'affordance (#789) a
    déjà **fermé** le geste, il n'y a plus de refus à annoncer, et c'est le **grisage** qu'il faut
    asserter. *On ne prévient pas après coup ce qu'on a déjà empêché.*

!!! warning "Mutation hors fil JavaFX"
    Un handler qui modifie l'IHM depuis un thread d'arrière-plan lève `Not on FX application thread`,
    souvent **avalée** (l'écran fige). Découper **préparation** (fil FX) / **exécution** (hors-thread)
    / retour sur le fil FX - c'est exactement le contrat du socle `ExecuteurTache` (#793, cf.
    [Patterns](patterns.md)), **synchrone en test** : avec lui, `bouton.fire()` rend l'état terminal
    observable au retour du clic, sans attente.
