# Tests et qualité

La chaîne qualité tourne à **chaque push** (CI) et localement via `./mvnw`. Cette page est la
référence structurée ; le repo-root garde un mémo
[**TESTING.md**](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/TESTING.md).

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
    - **`sun.misc.Unsafe`** *(à surveiller)* : le warning ne vient **pas** du code du projet, mais il ne
      vient pas non plus de Guava. **Mesuré le 2026-08-06** (#2747), sur JDK 25.0.3, en lançant `AppTest` :

        ```
        WARNING: sun.misc.Unsafe::staticFieldBase has been called by
                 com.google.inject.internal.aop.HiddenClassDefiner (guice-7.0.0.jar)
        WARNING: sun.misc.Unsafe::staticFieldBase will be removed in a future release
        ```

        C'est **Guice 7.0.0**, dans la génération de proxys AOP. La version précédente de cette page
        accusait Guava : c'est cette erreur qui a fait attendre une montée de Guava, laquelle a bien eu
        lieu (33.4.8-jre, #2740) **sans rien changer** - elle ne pouvait pas.

        ⚠️ **Ce n'est pas « rien à faire pour l'instant », c'est une échéance.** La JEP 498 retirera
        l'accès, et `--sun-misc-unsafe-memory-access=allow` (posé au `pom.xml` pour `javafx:run` et le
        lanceur jpackage, et au manifeste Flatpak) ne fait que la repousser. Le jour où le drapeau
        disparaît, l'application ne démarre plus.

        **Ce qui la lèverait** : une version de Guice qui cesse d'utiliser `Unsafe`. 7.0.0 est la
        dernière publiée ; Dependabot suit la dépendance et proposera la montée. Le drapeau se retire
        **le jour où ce warning cesse d'apparaître**, et pas avant : le vérifier se fait en une
        commande, `./mvnw test -Dtest=AppTest` puis chercher « sun.misc.Unsafe » dans la sortie.

!!! danger "Lancer les tests avec le bon JDK"
    Utilisez un **JDK 25 standard** (`25.0.2-open` / Temurin), **pas** un JDK packagé FX (`fx-zulu`) :
    ce dernier embarque JavaFX 25, masque les jars Maven FX 26 et fait échouer le headless
    (`NPE com.sun.glass.ui.PlatformFactory.getPlatformFactory()` : la Headless Platform n'existe qu'en
    FX 26). Comme la CI :
    ```bash
    export JAVA_HOME=~/.sdkman/candidates/java/25.0.2-open
    ```

### L'écran headless est figé à 1000×1000

La Headless Platform de JavaFX 26 rend dans un écran **codé en dur à 1000×1000 px**
(`HeadlessApplication.staticScreen_getScreens`, avec le `stride` de `HeadlessWindow`) : aucune
propriété ni variable d'environnement ne le change, et le framebuffer est un `ByteBuffer` de
`1000*1000*4` octets alloué une fois pour toutes.

Conséquence : une fenêtre qui, une fois affichée, **dépasse 1000 px** - typiquement une modale qui
grandit quand un bandeau se révèle et que `sizeToScene` la redimensionne - fait **déborder** le rendu :

```text
java.lang.IndexOutOfBoundsException
    at com.sun.glass.ui.headless.HeadlessWindow.blit(HeadlessWindow.java:333)
    at javafx.stage.Window.sizeToScene(...)
```

C'est un artefact du **test**, pas un défaut de production : un vrai écran (≥ 1000 px) et un vrai
gestionnaire de fenêtres n'ont pas ce framebuffer figé.

**La bonne réponse est de faire tenir la fenêtre sous 1000 px**, ce qui corrige du même coup le vrai
bug côté utilisateur (une fenêtre trop grande déborde aussi les petits portables). C'est ce qu'a fait
#2496 pour `RattachementModale` : corps dans un `ScrollPane`, pied épinglé, la fenêtre reste bornée
(cf. [ADR 2493](decisions/2493-une-modale-a-revelation-suit-la-croissance.md)). **À privilégier
systématiquement.**

!!! warning "Dernier recours : agrandir le framebuffer par réflexion"
    Pour un écran **vraiment irréductible** - un test dont la fenêtre ne *peut pas* descendre sous
    1000 px sans dénaturer ce qu'il vérifie - on peut agrandir le seul **nombre de lignes** du
    framebuffer au bootstrap du test (le `stride` reste à 1000, on ajoute des lignes). En test, JavaFX
    est chargé dans le **module sans nom** (classpath, `useModulePath=false`), donc la réflexion
    atteint le champ privé **sans `--add-opens`** :

    ```java
    /// Agrandit le framebuffer de la Headless Platform (lignes seulement, stride inchangé).
    /// À appeler sur le fil JavaFX, après le démarrage du toolkit et avant tout rendu.
    /// DERNIER RECOURS : couple le test aux internes de glass. À éviter si la fenêtre peut être bornée.
    static void agrandirEcranHeadless(int lignes) throws ReflectiveOperationException {
      Object app = Class.forName("com.sun.glass.ui.Application")
          .getMethod("GetApplication").invoke(null);
      if (app == null) {
        return;
      }
      java.lang.reflect.Field champ = app.getClass().getDeclaredField("frameBuffer");
      champ.setAccessible(true);
      champ.set(app, java.nio.ByteBuffer.allocate(1000 * lignes * 4));
    }
    ```

    Validé pendant #2496 : sur `RattachementModale` câblée, `agrandirEcranHeadless(3000)` rend ses
    12 tests verts au lieu du `blit` qui débordait. **Coûts** : ~`1000 * lignes * 4` octets par fork ;
    dépendance à un champ privé (`frameBuffer`) et à un nom de méthode (`GetApplication`) qui peuvent
    changer d'une version de JavaFX à l'autre. D'où « dernier recours » : préférer **borner la
    fenêtre**.

### Les butoirs TestFX sont des coupe-circuits, pas des budgets

`FxToolkit` borne deux attentes : le démarrage du toolkit JavaFX (`testfx.launch.timeout`) et la mise
en place d'un test, c'est-à-dire l'exécution de la méthode `@Start` par
`ApplicationExtension.beforeEach` (`testfx.setup.timeout`). Leur seul rôle est d'empêcher un fil FX
bloqué de figer le build indéfiniment. **Ils ne mesurent rien** : dépasser un butoir ne dit pas que le
code est lent, seulement que la machine n'a pas rendu la main à temps.

Les deux valeurs sont posées dans le `pom.xml` à **120 s**, et non laissées aux défauts de TestFX
(30 s et 60 s) :

```xml
<testfx.setup.timeout>120000</testfx.setup.timeout>
<testfx.launch.timeout>120000</testfx.launch.timeout>
```

Ce n'est pas un confort. Les défauts de TestFX supposent une JVM seule sur sa machine, alors que la
suite lance **une JVM par coeur** (`surefire.forkCount=1C`) sur un runner partagé. Les deux décisions
avaient été prises séparément, et le calcul ne tombait pas juste (#2120) :

| Grandeur | Mesure |
|---|---|
| Mise en place la plus lente de la suite, 4 coeurs / 4 forks, machine au repos | **6,9 s** (`SonsValidationViewTest#basculer_reference`) |
| Marge sous l'ancien butoir de 30 s | **4,3x** |
| Durée du job `build` sur 27 runs CI consécutifs | de **370 s à 2260 s**, soit **5,4x** |

La marge était **plus petite que la variation de la machine**. Le butoir vivait donc dans le bruit, et
expirait au hasard des runs. À 120 s, la marge est de 17x sur le nominal, soit 3x au-delà de la pire
dégradation observée, et un vrai interblocage est toujours coupé en deux minutes.

!!! warning "Un `» Timeout` en CI ne se lit pas comme un échec de test"
    Surefire l'affiche sous cette forme, sans distinguer une assertion fausse d'une attente expirée :

    ```
    SonsValidationViewTest.basculer_reference » Timeout
    ```

    La trace le tranche en trois lignes : `ApplicationExtension.beforeEach` puis
    `FxToolkit.setupApplication` puis `WaitForAsyncUtils.waitFor` signifient que **le test n'a jamais
    commencé**. Le premier réflexe est alors de regarder la **durée du job**, pas le diff : si le build
    a mis trois fois son temps habituel, c'est le runner qu'on observe, pas le code.

    Le piège est ailleurs : un rouge intermittent qu'on prend l'habitude d'écarter finit par couvrir
    celui qui compte. C'est ce précédent, et non le temps perdu, qui a motivé #2120.

`ButoirsTestFxTest` vérifie que ces deux valeurs atteignent bien la JVM **forkée**. Elles passent par
`systemPropertyVariables` : posées sur la JVM de Maven, elles n'auraient aucun effet, et TestFX
retomberait **en silence** sur ses défauts.

## La taxonomie des tests

Les tests vivent sous `src/test/java/fr/univ_amu/iut/`, en **miroir** des paquets de production.

| Catégorie | Emplacement | Vérifie |
|---|---|---|
| Unitaires métier | `<feature>/model/`, `<feature>/dao/`, `commun/persistence/`, `commun/model/` | Entités, services, DAO, migrations. Sans JavaFX. |
| ViewModel | `<feature>/viewmodel/` | État observable + logique de présentation, sans composant graphique. |
| Intégration de vue (TestFX) | `<feature>/view/*VueIntegrationTest` | La vue FXML se lie au ViewModel et réagit (headless). |
| **Geste** (TestFX) | `<feature>/view/*ViewTest` | Le bouton est **cliqué**, et on vérifie son **effet** (#1405). |
| Bout en bout | `fr.univ_amu.iut.e2e.*`, `<feature>/e2e/Parcours*E2ETest` | Le scénario complet : IHM → ViewModel → service → base. |
| **CLI shell (bats)** | `src/test/bats/*.bats` (fixtures partagées `helper.bash`) | La CLI **empaquetée** (fat-jar shadé), au niveau **processus** : arguments picocli, texte d'aide, **codes de sortie**, refus métier, ce que les tests Java in-process ne voient pas. `cli.bats` éprouve les commandes du chantier #1565 ; `cli-surface.bats` couvre le contrat **hors-ligne de chaque** sous-commande (aide, refus des options requises manquantes, exécution locale, refus sans jeton) ; `cli-reseau.bats` pointe le client sur un **serveur stub** (processus Python `stub_vigiechiro.py`) via la surcharge `VIGIECHIRO_URL` (`ConnexionModule#urlDeBase`) et prouve le chemin réseau **sans jeton réel ni Internet**. Reste à étoffer : contrats métier réseau sur fixtures Eve réalistes (#1592). Lancés en CI après le smoke-test du fat-jar (#1572, amorce). |
| Architecture (ArchUnit) | `architecture/ArchitectureTest` | Les **6 règles** de frontière MVVM (cf. [Architecture](architecture.md)). |
| **Documentation** | `documentation/DocumentationAJourTest` | Toute commande CLI a sa ligne, tout écran a sa fiche (#1458). |

Outils : **JUnit 5 + AssertJ + Mockito** ; **ApprovalTests** pour les sorties verbatim (CSV Tadarida
`_Vu` : le premier run produit un `*.received`, à approuver en `*.approved`).

### Tester un geste, pas un bouton

Un test qui vérifie qu'un bouton est **présent et actif** ne dit rien de ce qu'il fait. C'était
pourtant tout ce qu'on avait sur les actions **irréversibles** - restaurer la base, supprimer un
passage et sa nuit, réimporter par-dessus les validations de l'observateur. Et pas par négligence : un `showAndWait()` **fige** un test headless, donc le clic
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
    tests qui sèment encore un passage à la main (le compte de référence vit **dans le test lui-même**,
    pas ici, pour ne pas diverger), et elle ne peut que **rétrécir** :

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
| Les **chiffres balisés** `<!--inv:clé-->N<!--/inv-->` (#2385) | L'**inventaire réel du code** (contrats `Ouvrir*`, états de `StatutWorkflow`, features, sous-commandes, **catalogues de critères de filtre**) | Un décompte **figé dans la prose** qui dérive après un ajout : « 43 sous-commandes » quand le code en câble 44 |

Deux détails qui comptent :

- Les commandes sont lues **sur l'annotation**, par réflexion - jamais instanciées. Leurs constructeurs
  tirent des `Provider` qui **ouvrent la base** : les instancier ferait de l'E/S pour rien.
- `ActiviteAccueil.pageDoc()` est une méthode du **contrat**, pas une convention. Le nom de la fiche ne se
  déduit ni du titre (« Sons de référence » se documente dans `validation.md`) ni du paquet (la feature
  `audio` aussi) : il faut le **dire**. Le compilateur force donc à choisir une fiche, et le test refuse
  qu'elle soit absente.

#### Ancrer un chiffre : les balises d'inventaire

Certains nombres de la doc **décrivent le code** : le nombre de contrats `Ouvrir*`, d'états du workflow,
de features, de sous-commandes CLI. Écrits en dur, ils **dérivent** au premier ajout (un contrat de plus,
un état de plus) sans que rien ne rougisse. Une **balise d'inventaire** les ancre à un décompte que le
test recalcule. On écrit le nombre entre deux commentaires :

```markdown
l'application compte **<!--inv:features-->N<!--/inv--> features** métier
```

!!! warning "Règle de rédaction : un chiffre d'inventaire ne s'écrit pas en dur"
    **Si le code sait recalculer un chiffre, la documentation le porte en balise.** Sinon, elle ne
    l'écrit pas du tout.

    La raison n'est pas l'élégance, c'est une mesure : « les 21 tests bats » vivait à **trois**
    endroits quand il y en avait **89** - un facteur quatre, accumulé sans que rien ne le dise
    (#2749). Et personne n'avait mal fait : un chiffre juste le jour où on l'écrit devient faux tout
    seul.

    ⚠️ Trois, et non deux : la correction n'en avait trouvé que deux, et le troisième a survécu une
    journée de plus, dans un encadré du même fichier. **Un chiffre faux a des jumeaux**, et le
    balayage qui corrige n'est pas celui qui compte - c'est l'audit d'harmonisation de la clôture qui
    a rendu le troisième.

    Trois cas, trois gestes :

    - **le code sait compter** → balise `<!--inv:clé-->N<!--/inv-->`, plus une entrée dans
      `DocumentationAJourTest` ;
    - **le code ne sait pas** (un commentaire de workflow, une note de PR) → **écrire la phrase sans
      le nombre**. « les tests bats, qui lancent chacun un JVM » dit ce qu'il faut sans rien promettre ;
    - **le chiffre est une mesure datée** (« 66 aperçus sur 138 différaient le 6 août ») → il reste en
      dur, et c'est **juste** : ce n'est pas un inventaire, c'est un constat, et un constat a une date.

    ⚠️ Le troisième cas se confond avec le premier au premier coup d'œil. Un balayage a compté « 51,
    66, 108 aperçus » comme des inventaires divergents : c'étaient trois **deltas** d'ADR, et le total,
    138, était juste partout. Lire la phrase entière avant de conclure à une dérive.

où `N` est le chiffre (`15` aujourd'hui). Un commentaire HTML **ne s'affiche pas** : la phrase se lit
« 15 features » comme avant, mais `chaque_chiffre_balise_egale_l_inventaire_reel` relit `N` et le
confronte au code. Une divergence fait **rougir la CI**, le message portant le vrai chiffre. Clés
reconnues :

| Clé | Décompte réel |
|---|---|
| `ouvrir` | fichiers `commun/view/Ouvrir*.java` |
| `etats-workflow` | valeurs de l'enum `StatutWorkflow` |
| `features` | dossiers de `fr.univ_amu.iut` hors `commun`, `cli`, `perf` |
| `cli` | sous-commandes câblées dans `CommandeRacine` |
| `workflows-ci` | fichiers `.yml` de `.github/workflows` |
| `migrations` | fichiers `V*.sql` de `db/migration` |
| `criteres-validation` | fabriques `CritereFiltre` de `CriteresAudio` |
| `criteres-analyse` | fabriques `CritereFiltre` de `CriteresAnalyse` |
| `criteres-activite` | fabriques `CritereFiltre` de `CriteresActivite` |
| `criteres-multisite` | fabriques `CritereFiltre` de `CriteresMultisite` |
| `criteres-audit` | fabriques `CritereFiltre` de `CriteresAudit` |

Les cinq clés `criteres-*` (#3105) comptent les fabriques par **nom distinct** : plusieurs catalogues
offrent des surcharges du même critère (`groupe`, `heure`), qui restent **une seule puce** à l'écran.
La réflexion s'arrête au type de retour, sans rien invoquer : pas de toolkit JavaFX, pas de données.

Ces cinq-là existent parce que la dérive s'était déjà produite : « Douteux » et « Non identifiés »
ont vécu deux paliers sans figurer dans `validation.md`, et un commentaire de `FiltresVuesAudio`
annonçait deux critères là où le code en câblait dix. Un décompte réécrit en prose finit toujours par
mentir - la règle est donc de **nommer la source** dans un commentaire, et d'ancrer le **nombre** ici.

Poser une balise sur une **clé non listée** échoue aussi : on ajoute d'abord la clé et son décompte au
test (une clé = un fait que le code sait recalculer). Et le test exige qu'**au moins une** balise subsiste
par clé, pour qu'un inventaire ne perde pas discrètement son ancre. Enfin, `aucune_commande_documentee_n_a_disparu_de_la_cli`
fait le trajet **inverse** du tableau CLI : une commande décrite dans `cli.md` mais **absente** du câblage
(renommée, supprimée) fait rougir tout autant.

## Les outils qualité

| Outil | Rôle | Bloquant ? |
|---|---|---|
| **ArchUnit** | Frontières MVVM + absence de cycles | Oui (tests) |
| **Spotless** (Palantir) | Format du code, via un *hook* pre-commit silencieux | Oui (`spotless:check` en CI) |
| **PMD** | *Code smells* | Bloquant **sous `-Pquality-gate`** |
| **JaCoCo** | Couverture | Seuils bloquants **sous `-Pquality-gate`** |
| **PIT** | Qualité des tests par **mutation** | Non (à la demande, `-Pmutation`) |

### SonarQube for IDE (facultatif, à configurer)

L'extension **SonarQube for IDE** (ex-SonarLint) analyse à la frappe et complète utilement PMD :
elle voit des bugs et des fuites de ressources que le ruleset ne cherche pas. Mais **PMD fait foi** :
c'est lui qui bloque la CI. Or, laissée par défaut, l'extension applique le profil « Sonar way »
(542 règles Java) et **contredit trois seuils** délibérément arbitrés dans
[`pmd-ruleset.xml`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/pmd-ruleset.xml) :

| Règle Sonar | Défaut | Ce que dit le ruleset PMD | Remontées sur `src/main/java` |
|---|---|---|---|
| `java:S107` (nb de paramètres) | 7 | `ExcessiveParameterList` **11** : les `@Provides` Guice agrègent leurs collaborateurs | **30** |
| `java:S3776` (complexité cognitive) | 15 | pendant de `CyclomaticComplexity` **24** : les parseurs écrits à la main montent à 27 | **9** |
| `java:S106` (sortie standard) | actif | aucun équivalent : `**/outils/**` et la CLI picocli écrivent sur stdout | **28 fichiers** |

Le réglage qui les réaligne **ne peut pas être versionné** : `sonarlint.rules` est de scope
`application`, donc VS Code le lit **uniquement** depuis les réglages utilisateur et ignore
silencieusement un bloc placé dans `.vscode/settings.json`. À recopier dans ses réglages personnels :

```json
"sonarlint.rules": {
    "java:S107":  { "level": "on", "parameters": { "maximum": "11", "constructorMax": "11" } },
    "java:S3776": { "level": "on", "parameters": { "Threshold": "30" } },
    "java:S106":  { "level": "off" }
}
```

Trois points à ne pas redécouvrir :

- La clé de `java:S3776` prend une **majuscule** (`Threshold`). Écrite en minuscule, elle est ignorée
  sans le moindre message et le seuil reste à 15.
- `java:S107` ne visite ni les **records** : les 27 records à 8 composants ou plus, dont
  `LigneObservationAudio` et ses 30 composants, ne remontent pas. Inutile de relever le seuil pour eux.
- On ne coupe **que** ces trois règles. Le reste de « Sonar way » est un complément, pas un doublon ;
  le désactiver en bloc reviendrait à ne garder que ce que PMD sait déjà faire.

Pour une configuration réellement **partagée** entre contributeurs, la seule voie serait le
*connected mode* (SonarQube Cloud, gratuit sur dépôt public) avec un profil qualité côté serveur.
Le coût est un second référentiel de règles à tenir en phase avec `pmd-ruleset.xml` ; tant que
l'écart tient en trois lignes, le bloc ci-dessus suffit.

### Couverture et mutation

- **JaCoCo** : sous `-Pquality-gate`, seuils **bloquants** au niveau `BUNDLE`. Leurs valeurs, la
  raison de chacune et la recette pour re-mesurer la couverture vivent dans le
  [`pom.xml`](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/pom.xml), **seule
  source** : les répéter ici les ferait diverger au premier resserrage. Les `**/outils/**` (capture
  d'écran, bancs de mesure) sont **exclus** : ils sont validés par exécution, pas par tests
  unitaires.
- **PIT** (`-Pmutation`) évalue si les tests **détectent** des mutations du code. Lent sur tout le
  dépôt, mais **rapide ciblé** sur la classe qu'on vient d'écrire - et c'est ainsi qu'il sert le mieux :

    ```bash
    ./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage \
        -DtargetClasses=fr.univ_amu.iut.passage.viewmodel.SaisieHorairesNuit \
        -DtargetTests=fr.univ_amu.iut.passage.viewmodel.SaisieHorairesNuitTest
    ```

⚠️ **`test-compile` n'est pas une commodité, c'est ce qui fait démarrer le minion.** PIT hérite de
l'`argLine` de Surefire, qui contient deux valeurs posées par des greffons liés à des **phases** :
`@{jacocoArgLine}` (`jacoco:prepare-agent`) et `${net.bytebuddy:byte-buddy-agent:jar}`
(`dependency:properties`, phase `initialize`). Invoquer le **but seul**
(`./mvnw -Pmutation org.pitest:...:mutationCoverage`) n'exécute **aucune** phase : les deux restent
littérales, le minion reçoit `-javaagent:${net.bytebuddy:byte-buddy-agent:jar}`, refuse de démarrer, et
PIT ne rapporte que :

```
PIT >> SEVERE : Coverage generator Minion exited abnormally due to MINION_DIED
```

Ce message **ne dit pas** la cause, et il est identique quelle que soit la classe visée - y compris une
classe pure sans JavaFX ni SQLite. D'où la conclusion tentante, et fausse, que « PIT ne marche pas sur ce
dépôt ». Il marche : toujours enchaîner une phase, comme dans les exemples ci-dessus.

**Sur une classe de vue (TestFX), rien de plus à faire.** Le profil `mutation` du `pom.xml` passe déjà
les quatre propriétés headless au minion (`glass.platform=Headless`, `prism.order=sw`,
`java.awt.headless=true`, `testfx.robot=glass`) : sans elles, PIT lançait ses minions hors headless et
s'arrêtait sur « *tests did not pass without mutation* ». **Ne pas** les repasser à la main via
`-DjvmArgs=…` : la ligne de commande **remplace** la liste du profil au lieu de s'y ajouter, et une liste
incomplète ramène l'échec que le profil avait supprimé.

PIT couvre donc aussi la couche `view`, ce que l'échec brut laissait croire impossible.

**Deux mesures tournent toutes seules, chaque nuit**, et leurs périmètres ne se recouvrent pas.

Elles étaient hebdomadaire et mensuelle : les cycles complets prenaient **17 semaines** et **15 mois**.
Quinze mois, pour les vues d'une feature, c'est plus long que le chantier qui y introduirait une
régression. En quotidien les mêmes cycles prennent **17 et 15 jours**, et le dépôt étant public, les
minutes d'Actions sont illimitées sur les runners standard : la fréquence ne coûte que du temps machine.

Deux limites à garder en tête. La rotation est **aveugle au diff** : elle avance d'un paquet par jour quoi
qu'on ait touché, donc elle mesure plus souvent, pas plus juste. Et un bilan quotidien que personne ne lit
vaut moins qu'un bilan hebdomadaire qu'on lit - la fréquence ne crée pas l'attention. Les deux sont **non
bloquantes**, comme le rapport ADR dont elles sont le calque : un survivant n'est pas un défaut mais une
**question** posée à un humain, et bloquer une fusion là-dessus ferait cocher au hasard.

| Workflow | Quand | Ce qu'elle mute | Avec quels tests |
|---|---|---|---|
| `mutation-model.yml` | chaque nuit (3 h UTC), **un paquet par tour** (cycle de 17 jours) | `fr.univ_amu.iut.<feature>.model.*` | tous, **sauf** `e2e` et `commun.api` |
| `mutation-ihm.yml` | chaque nuit (5 h UTC), **une feature par tour** (cycle de 15 jours) | `fr.univ_amu.iut.<feature>.view.*` | ceux de la feature, **sauf** `e2e` |

Chacune publie son bilan dans le **résumé du job** ; le rapport HTML détaillé est conservé 30 jours en
artefact.

**Pourquoi les E2E sont exclus des deux.** Un E2E est **large en couverture et pauvre en jugement**.
`ParcoursDepotE2ETest` couvre à lui seul 8 539 blocs : PIT le retient comme test candidat pour des mutants
situés dans des centaines de classes, et rejoue le parcours entier - des minutes de TestFX - pour
apprendre ce qu'un test unitaire dit en millisecondes. Le premier passage sur le dépôt entier a produit
**19 expirations en une heure**, toutes autour de ce parcours.

Les exclure ne **cache** rien, et c'est le point : un mutant que seul un E2E tuerait est, par définition,
un mutant qu'aucun test unitaire ne détecte. Il ressort désormais en **survivant**, c'est-à-dire en
question posée à un humain, au lieu d'être tué silencieusement.

**Pourquoi le modèle aussi se mesure un paquet à la fois.** Les paquets `model` d'un seul coup n'ont pas
fini : le job a été **tué à 300 minutes pile**, après 335 unités sur ~4 657 mutants. Ce n'est pas la
lenteur d'un test qui l'a tué, c'est le **volume** - 4 657 mutants à quelques secondes chacun font cinq
heures quelle que soit la finesse des tests. Un paquet seul, en revanche, tient sans peine :
`saison.model` a rendu 86 mutants et **97 % de détection en 12 minutes**, sans expiration.

**Le plus gros paquet tient, et confortablement.** `commun.model` (96 classes) a été mesuré : **2 h 35**
sur les 5 heures de budget, 746 mutants, **92 % de détection**. L'extrapolation à partir de `saison`
donnait 4 h 45 - elle était pessimiste d'un facteur deux, parce que le coût se compte en **mutants**, pas
en classes : le périmètre entier en produisait 4 657, `commun.model` seulement 746.

Reste `passage` (93 classes) comme seul inconnu, et il devrait se comporter comme `commun`. La maille n'a
donc **pas** à descendre d'un cran.

À noter, parce que cela contre-indique une maille plus fine : le **calcul de couverture est un coût fixe**
payé à chaque tour, autour de 14 minutes quelle que soit la taille du paquet - 12 minutes sur les 12 de
`saison`, 14 sur les 155 de `commun`. Découper davantage ferait payer ce préambule plus souvent, pour un
rendement moindre.

**Pourquoi `commun.api` est écarté côté tests.** Nuance qui compte : ce n'est pas une classe mutée, c'est
la suite du **réessai gradué** ([ADR 2354](decisions/2354-le-reessai-reseau-est-gradue-jamais-aveugle-toujours-jittere.md)),
la plus lente du dépôt (5,5 s). Son sujet est l'**attente** : tout mutant qui fait boucler le réessai une
fois de plus dépasse le butoir de PIT (~11 s), et l'expiration y est un **effet du sujet muté**, pas un
défaut d'outillage. Les 16 expirations du run tué pointaient vers elle.

**Pourquoi l'IHM se mesure une feature à la fois.** Muter une vue coûte ~9 s par mutant : chaque mutant
rejoue des tests TestFX, qui démarrent un toolkit JavaFX. Les 283 classes de vue de l'application
demanderaient des dizaines d'heures, quand la borne d'un job est à 300 minutes. La mesure est donc
**complète sur un cycle, pas sur un mois** : la rotation se déduit du mois, sans état à écrire ni relire,
et le tour se fait en 15 mois. Elle vaut le détour - sur `saison`, 80 mutants, 36 % tués, **40 % de
survivants**.

!!! danger "Ne pas allonger le butoir pour « laisser le temps » aux tests graphiques"
    C'est la correction qui vient à l'esprit devant une expiration, et elle rendrait le chiffre **faux
    dans le sens rassurant** : PIT compte une expiration comme une **détection**. Vérifié en rendant le
    butoir absurde sur une classe de vue - 21 mutants, 21 expirations, score annoncé **100 %**, là où la
    mesure honnête donne 43 %.

    Le butoir par défaut (`4 s + 1,25 × durée normale`) suffit une fois les E2E écartés : sur le
    périmètre des vues, **1 expiration sur 280 mutants**. C'est le périmètre qu'il fallait corriger, pas
    le butoir (#2768).

⚠️ **PIT n'a plus d'analyse incrémentale.** Depuis la version 1.25.x, le stockage de l'historique est un
greffon **commercial** (arcmutate). Les options `historyInputFile`/`historyOutputFile` figurent toujours
au descripteur du greffon Maven, mais les passer sans lui ne les fait pas ignorer : PIT **refuse de
démarrer**. Le descripteur dit que l'option existe, pas que la fonction est là (#2768).

Le même bilan se lit en local, sur n'importe quel rapport :

```bash
python3 scripts/qualite/rapport_mutation.py --markdown
```

Il trie les classes par nombre de survivants plutôt que d'afficher un pourcentage : le score situe, la
liste travaille.

⚠️ **Lire le rapport, pas le résumé.** `target/pit-reports/mutations.xml` écrit ses attributs en
**apostrophes simples** (`status='SURVIVED'`). Un filtre écrit en guillemets doubles ne matche rien et
annonce « 0 survivant » sur n'importe quel rapport - y compris sur une classe dont sept mutants
survivaient. Le résumé imprimé en fin de course (`Generated N Killed M`) est la référence à recouper.

    Rapport HTML dans `target/pit-reports/`. Un **mutant survivant** désigne une ligne que rien ne
    vérifie vraiment.

### Un garde-fou de non-régression se vérifie en le voyant rouge

Un test écrit pour empêcher un défaut de revenir ne vaut que si l'on a **constaté qu'il échoue** quand
le défaut est là. Les suites de l'EPIC #1863 ont produit **quatre** contre-exemples en une seule
session, tous verts et tous creux :

- un test d'alias CLI qui passait **avec et sans** l'alias (`--help` sur une commande inconnue déclenche
  l'aide de la **racine**, qui liste justement la commande cherchée) ;
- une sonde live dont la remise en état allait échouer en silence ;
- une garde « n'écrire que si la saisie a changé », posée pour corriger un défaut constaté, que rien
  n'avait jamais verrouillée ;
- un test de boucle d'horodatage qui refaisait lui-même la moitié du calcul qu'il prétendait vérifier.

**Deux gestes, pas un.** Ils ne couvrent pas la même chose :

| | Ce que ça couvre | Ce que ça ne voit pas |
|---|---|---|
| **PIT** (`-Pmutation` ciblé) | l'**espace entier** des mutations d'une classe : conditions inversées, bornes, retours neutralisés | tout ce qui n'est pas du code Java mutable - attribut d'annotation (`aliases`), câblage Guice, FXML, sonde réseau |
| **La mutation à la main** | n'importe quoi : réintroduire le défaut d'origine, retirer une annotation, casser un binding | une seule hypothèse à la fois, celle qu'on a pensé à tester |

PIT est plus **exhaustif** là où il s'applique ; le geste manuel est plus **large**. Les trois premiers
contre-exemples ci-dessus sont hors de portée de PIT ; le quatrième, en revanche, était exactement dans
sa cible - et un `-Pmutation` ciblé l'aurait signalé sans qu'on ait à deviner lequel mutant écrire.

**En pratique** : à la passe 6 d'une clôture, lancer PIT ciblé sur les classes que le chantier a
introduites, et vérifier à la main les garde-fous que PIT ne peut pas atteindre.

## Ce qui bloque la CI

| Workflow | Commande | Bloquant ? |
|---|---|---|
| « Java CI » ([maven.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/maven.yml)), tests **+ couverture** **+ hygiène des dépendances** | `./mvnw -B verify -Djacoco.haltOnFailure=true` | **Oui** |
| « Quality gate » ([lint.yml](https://github.com/echonuit/vigiechiro-pr-companion/blob/main/.github/workflows/lint.yml)), formatage | `./mvnw -B spotless:check` | **Oui** |
| « Quality gate », portail PMD | `./mvnw -B -Pquality-gate compile pmd:check` | **Oui** |

L'**hygiène des dépendances** est verrouillée dans la première ligne : `dependency:analyze-only` est
lié à la phase `verify` et **échoue sur écart** (`failOnWarning`). Jusqu'à #3515 il n'était lié à
aucune phase et n'apparaissait dans aucun workflow - il ne tournait que si quelqu'un le tapait, et
signalait cinq écarts en terminant en succès.

Deux familles d'écarts, deux gestes opposés : **utilisée sans être déclarée** veut dire qu'on compile
grâce à une transitive, qui disparaîtra le jour où son porteur montera de version - la déclarer ;
**déclarée sans être utilisée** veut dire qu'on porte un artefact pour rien - le retirer, après avoir
vérifié que ce n'est pas un faux positif de l'analyseur, qui lit le bytecode et ne voit donc ni les
liaisons résolues à l'exécution ni les artefacts résolus avec un classifieur de plateforme. Les
exclusions du `pom.xml` nomment chacune sa raison ; une exclusion sans motif est le même bruit sous un
autre nom.

⚠️ Ne pas mêler une **déclaration** à une **montée de version** : les deux dans la même PR rendent un
éventuel rouge illisible.

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
- **Un test qui MESURE une géométrie monte sa scène avec `Habillage.scene(...)`**, jamais `new Scene`.
  Sans lui, il mesure la police de la **machine hôte** au lieu de celle du produit, et son verdict
  dépend de ce qui a tourné avant lui dans le même fork. `ScenesHabilleesTest` le garde (#3773).

### ⚠️ La police d'un test n'est pas celle du produit, sauf si on la lui donne

`Typographie.installer()` garde un `static boolean` : l'enregistrement de la police embarquée est
**global au JVM et fait une seule fois**. Un test qui monte sa scène à la main voit donc la police du
produit **si un voisin l'a installée avant lui**, et celle du système sinon - avec `reuseForks=true`,
c'est l'**ordre d'exécution** qui décide.

Mesuré (#3773) : `CartesAccueilTest` a rendu **vert à 8 h 14 et rouge à 15 h 34**, sur le **même
commit** et la **même image** `macos-26-arm64`. Puis, joué **seul** sous macOS - donc sans voisin -, il
échoue. L'écart tient à 20,43 px contre 17,666 px selon la police effectivement rendue.

⚠️ **Ce défaut ne se voit pas depuis un poste Linux** : `Noto Sans` y est une police système, donc
trouvée installée ou non. Une suite locale verte ne dit rien de cette propriété. Sur le runner Ubuntu,
l'ADR 3361 note que `sans-serif` se résout en « une police plus large » - ce que la CI voit exactement
n'a pas été mesuré.

Le remède ne dépend d'aucune machine : passer par `Habillage.scene(...)`, qui installe la police **et**
pose le trio du chrome.

### Quatre pièges récurrents

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
    [Patterns](patterns.md)), **synchrone par défaut en test de vue/ViewModel** (liaison Guice
    `@ImplementedBy(ExecuteurTacheSynchrone.class)`, cf. [Patterns](patterns.md)) : avec lui,
    `bouton.fire()` rend l'état terminal observable au retour du clic, sans attente.

!!! warning "Un E2E n'a pas le double synchrone : attendre le signal, pas le retour du clic"
    Un test `*E2ETest` monte le **vrai** `RacineInjecteur`, pas le module de test qui rebranche
    `ExecuteurTacheSynchrone` (#793). `occupation.occuper(...)` y tourne donc sur le **vrai**
    `ExecuteurTacheAsynchrone` : thread virtuel + `Platform.runLater`. Après un `robot.interact(...)`
    (ou un appel direct en `@Start`) qui déclenche ce chemin, `waitForFxEvents()` ne fait que vider la
    file du fil FX - il n'attend pas le thread d'arrière-plan qui la remplira. L'assertion tombe alors
    avant le callback de succès : un échec qui ne se reproduit que sur une machine lente, donc en CI
    (#3668, #3717). Remède : `WaitForAsyncUtils.waitFor(timeout, TimeUnit, () -> <prédicat
    observable>)` sur l'état attendu, jamais une assertion immédiate après l'`interact`.
