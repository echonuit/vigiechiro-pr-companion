# Stack technique et architecture

Cette page décrit la **pile technique** sur laquelle l'application est construite et les **choix
d'architecture** qui structurent son code.

## Plate-forme et langage

- **Indépendance plate-forme** : l'application fonctionne sans modification sur Windows 10/11, Linux
  (Ubuntu/Debian récents) et macOS récent. Les installeurs natifs embarquent leur propre *runtime*
  Java, si bien qu'aucun JDK/JRE n'a à être installé sur le poste de l'utilisateur.
- **Langage** : **Java 25**.
- **Interface graphique** : **JavaFX 26**, écrite en **FXML** (la mise en page est séparée du code des
  contrôleurs et peut être retouchée sans recompiler la logique). Les tests d'IHM tournent en mode
  *headless* via la **Headless Platform** de JavaFX 26 (`-Dglass.platform=Headless`), donc sans serveur
  d'affichage ni `xvfb`.
- **Persistance** : couche d'accès aux données en **JDBC** sur **SQLite** (driver
  `org.xerial:sqlite-jdbc`), sans ORM. Le schéma est créé et fait évoluer par des **migrations
  versionnées**. Une base = un fichier `.sqlite` portable, lisible par DB Browser for SQLite ou par
  n'importe quel client tiers.

## Architecture du code

- **MVVM** (Model - View - ViewModel) : le modèle métier (entités, services, DAO) ignore JavaFX ; le
  *ViewModel* expose un état observable (`javafx.beans`) sans dépendre de `javafx.scene/fxml/stage` ;
  la *View* (contrôleur + FXML + CSS) se lie aux propriétés du ViewModel et ne parle jamais à la base.
- **Package par fonctionnalité** : chaque fonctionnalité est un paquet autonome
  (`model` / `viewmodel` / `view` / `di`) posé sur un **socle commun** (`commun`) qui porte le chrome,
  la navigation, la persistance et les contrats inter-fonctionnalités.
- **Injection de dépendances** avec **Guice 7** : chaque fonctionnalité publie ses services et
  ViewModels via un module Guice ; la navigation inter-fonctionnalités passe par des **contrats**
  définis dans le socle (inversion de dépendance), de sorte qu'aucune fonctionnalité ne dépend de la
  *vue* d'une autre.
- Ces règles de couches et d'acyclicité sont **vérifiées automatiquement** par des tests
  **ArchUnit** : une violation (par exemple un `model` qui importerait JavaFX, ou une dépendance
  croisée vers une vue) casse la compilation des tests.
- **Les dialogues d'une action passent par des contrats du socle**, jamais par un `Alert` ou un
  `FileChooser` construit dans un contrôleur : `Confirmateur` (le oui/non), `Notificateur` (le compte
  rendu), `SelecteurFichier` (la désignation d'un fichier), `DemandeurDeChoix` (le choix parmi plusieurs
  options). Chaque écran en détient une instance remplaçable, que ses tests substituent par un double.

  La contrainte est mécanique : un `showAndWait()` **fige** un test d'IHM *headless*. Une action qui
  ouvre un dialogue en dur est donc **impossible à déclencher dans un test** - seul le **grisage** de
  son bouton reste vérifiable, jamais son **effet**. Et il suffit d'en oublier **un seul** (l'alerte
  finale, ou le sélecteur de fichier initial) pour que le geste entier redevienne intestable. C'est ce
  qui privait les gestes **irréversibles** du produit (purger les originaux, restaurer la base,
  supprimer une nuit, réimporter par-dessus des validations) de tout test de leur effet.

  Deux principes de conception en découlent, et ils valent au-delà du code :

  - **« Annuler » n'est pas une option, c'est un renoncement.** Un dialogue « Enregistrer / Abandonner /
    Annuler » n'a pas trois issues : il en a **deux**, plus la possibilité de ne pas choisir. Corollaire
    à ne jamais perdre de vue : **renoncer n'est pas abandonner** - les deux ferment la fenêtre, **un
    seul détruit** le travail de l'utilisateur.
  - **Un formulaire n'est pas un dialogue, c'est une vue.** Toute saisie (déclarer un site, personnaliser
    une sélection d'écoute) est une **modale** à part entière : FXML, contrôleur, ViewModel, entrée de
    navigation. Un `Dialog` bâti à la main rend le geste injouable, sa validation intestable, et sa
    capture de documentation **impossible** - il faudrait la reconstruire à la main, et elle **dériverait**
    du vrai écran (c'est arrivé : la documentation a affiché pendant des mois un protocole qui n'existait
    pas).

## Outillage et cycle de vie

- Cycle de vie géré avec **Maven** via le **Maven Wrapper** (`./mvnw` / `mvnw.cmd`) : aucune
  installation globale de Maven n'est requise.
- **Tests automatisés** avec **JUnit 5**, **AssertJ**, **TestFX** (IHM, headless), **Mockito** et
  **ApprovalTests**. Des tests **end-to-end** rejouent les parcours métier de bout en bout.
- **Qualité de code** outillée et **bloquante en CI** : **Spotless** (Palantir Java Format, déclenché
  en *pre-commit*), **PMD**, et une **couverture minimale** vérifiée par **JaCoCo**. Les valeurs de
  ces seuils, et la justification de chacune, vivent dans le `pom.xml` du dépôt applicatif : les
  répéter ici les ferait diverger au premier ajustement.
- **Intégration continue** sur **GitHub Actions** : à chaque *push*, build + tests + portail qualité ;
  une CI rouge interdit la fusion. Les captures d'écran de référence de la documentation sont
  régénérées automatiquement.

## Distribution et exécution

- **Installeurs natifs** produits par **jpackage**, un par système : `.msi` (Windows), `.dmg`
  (macOS Apple Silicon), `.deb` (Debian/Ubuntu). Chacun embarque le *runtime* : **aucun Java à
  installer**.
- **Lancement depuis les sources** : `./mvnw javafx:run` suffit dans un environnement Java 25.
- **Mode CLI** (sans IHM) pour les opérations scriptables (import d'une nuit, export d'un CSV),
  utilisable en automatisation et en CI.

## Composant audio fourni

La brique technique la plus lourde est **fournie** sous forme de composant réutilisable, pour
concentrer le développement sur la chaîne métier :

- **Vue audio (sonogramme + spectrogramme)** : composant JavaFX (`audio-view`, distribué via JitPack)
  qui prend un fichier WAV en entrée et affiche le sonogramme (amplitude/temps) et le spectrogramme
  (fréquence/temps), avec **curseur de lecture synchronisé** et **zoom temps/fréquence**. Le calcul FFT
  et le rendu graphique sont internes au composant : on l'instancie avec un chemin de fichier et on
  écoute ses évènements. Il est utilisé dans [M-Qualification](Analyse%20et%20conception/Maquettes/M-Qualification.md)
  et [M-SonsValidation](Analyse%20et%20conception/Maquettes/M-SonsValidation.md).

## Licence et publication

- **Licence GPLv3** : le code et ses composants restent librement réutilisables par la communauté
  VigieChiro.
- Code source hébergé sur **GitHub** ([echonuit/vigiechiro-pr-companion](https://github.com/echonuit/vigiechiro-pr-companion)),
  documentation publiée sur **GitHub Pages**, jeu de données d'exemple archivé sur **Zenodo**.

## Données

- L'application travaille sur des **fichiers locaux** : un dossier de session importé peut contenir
  plusieurs giga-octets de WAV sans dégrader notablement les performances de l'IHM. À titre indicatif,
  **une grosse nuit peut peser ~40 Go par enregistreur** ; le client cible peut déployer **jusqu'à 24
  enregistreurs en parallèle** sur 40 à 50 nuits d'une saison estivale (volumétrie cumulée de l'ordre
  de plusieurs To).
- Les **CSV d'observations** suivent le format produit par Tadarida : séparateur point-virgule, champs
  entre guillemets quand nécessaire, encodage UTF-8 (cf. exemples dans
  [`transformes/`](https://github.com/echonuit/vigiechiro-pr-companion-exemple-nuit/tree/main/transformes)
  du dépôt d'échantillon). Sur une séquence (5 s réelles ralenties ×10), Tadarida peut produire **plusieurs
  lignes** (une par espèce distincte identifiée), avec un *timing* début/fin précis dans la séquence,
  exprimé en **secondes réelles** à l'intérieur de la tranche de 5 s.
- Les **WAV bruts** sont au format PCM 16 bits, mono, 384 kHz. Les **WAV transformés** déposés sur
  Vigie-Chiro sont des tranches de **5 s réelles**, **déjà ralenties ×10** (signal ramené dans la bande
  audible), qui durent donc **50 s à l'écoute** : c'est ce fichier qui est lu en lecture normale dans
  l'IHM, et non un ralentissement appliqué à la volée. Chaque séquence est **nommée par l'heure réelle de son début** (l'horodatage de
  l'enregistrement décalé de 5 s par séquence, suffixe `_000`) : c'est ce nom que porte l'`observations.csv`,
  et qui relie une observation à sa séquence audio.
