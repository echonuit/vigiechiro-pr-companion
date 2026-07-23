package fr.univ_amu.iut.commun.model;

import java.nio.file.Path;

/// Espace de travail local : dossier racine qui contient les sessions, la base SQLite et les
/// réglages (règles R21/R22 de la spec).
///
/// Toute la résolution de chemins passe par cette classe via [Path] : **aucun chemin n'est
/// codé en dur** ailleurs (portabilité O1, accessibilité multi-OS). Le défaut est
/// `<Documents>/VigieChiro-Companion/` (R21), surchargé en test par un `@TempDir`.
///
/// ```
/// <workspace>/
/// ├── Car040962-2026-Pass1-A1/        ← R22 : nom = préfixe du passage
/// │   ├── bruts/                       ← enregistrements originaux (R7)
/// │   └── transformes/                 ← séquences d'écoute (R8) + CSV Tadarida (R23)
/// ├── logs/                            ← journaux applicatifs (rotation, #1523)
/// └── vigiechiro.db                    ← base SQLite (R21)
/// ```
public final class Workspace {

    /// Nom du fichier SQLite à la racine du workspace (R21).
    public static final String FICHIER_BASE = "vigiechiro.db";

    private static final String DOSSIER_DEFAUT = "VigieChiro-Companion";
    private static final String SOUS_DOSSIER_BRUTS = "bruts";
    private static final String SOUS_DOSSIER_TRANSFORMES = "transformes";
    private static final String SOUS_DOSSIER_LOGS = "logs";

    private final Path racine;
    private final Path cheminBase;

    /// Workspace dont la base vit à sa racine, ce qui est le cas ordinaire.
    public Workspace(Path racine) {
        this(racine, racine.resolve(FICHIER_BASE));
    }

    /// Workspace dont la base vit **ailleurs** (#1038) : elle est le seul artefact irremplaçable, et
    /// on peut vouloir la mettre au sûr sans déménager le reste, qui est un cache.
    public Workspace(Path racine, Path cheminBase) {
        this.racine = racine.toAbsolutePath();
        this.cheminBase = cheminBase.toAbsolutePath();
    }

    /// Workspace par défaut : `<home>/Documents/VigieChiro-Companion` (R21). Le dossier n'est
    /// pas créé ici : il le sera paresseusement à la première connexion (cf. `SourceDeDonnees`).
    public static Workspace parDefaut() {
        return new Workspace(racineParDefaut());
    }

    /// Workspace **effectif**, résolu dans cet ordre : la propriété système
    /// `-Dvigiechiro.workspace` d'abord, puis la [ConfigurationAmorcage] persistée, puis le défaut.
    ///
    /// La propriété garde la priorité parce qu'elle sert aux tests et aux lancements ponctuels : une
    /// configuration persistée ne doit jamais reprendre la main sur un emplacement demandé pour
    /// cette exécution-là. Point unique de résolution (socle + drapeaux de fonctionnalités).
    public static Workspace resolu() {
        String surcharge = System.getProperty("vigiechiro.workspace");
        if (surcharge != null) {
            return new Workspace(Path.of(surcharge));
        }
        ConfigurationAmorcage configuration = ConfigurationAmorcage.lue();
        Path racine = configuration.espaceDeTravail().orElseGet(Workspace::racineParDefaut);
        return new Workspace(racine, configuration.cheminBase().orElseGet(() -> racine.resolve(FICHIER_BASE)));
    }

    private static Path racineParDefaut() {
        return Path.of(System.getProperty("user.home"), "Documents", DOSSIER_DEFAUT);
    }

    /// Dossier racine du workspace.
    public Path racine() {
        return racine;
    }

    /// Chemin du fichier de base SQLite (R21), à la racine du workspace sauf choix contraire (#1038).
    public Path cheminBaseDeDonnees() {
        return cheminBase;
    }

    /// Dossier des **journaux applicatifs** (`logs/`), à la racine du workspace (#1523). Comme les autres
    /// résolveurs, ne crée pas le dossier : l'amorçage de la journalisation
    /// ([ConfigurationJournalisation]) s'en charge.
    public Path dossierLogs() {
        return racine.resolve(SOUS_DOSSIER_LOGS);
    }

    /// Dossier d'une session, nommé exactement comme le préfixe du passage (R22).
    public Path dossierSession(String prefixe) {
        return racine.resolve(prefixe);
    }

    /// Sous-dossier `bruts/` d'une session (originaux R7).
    public Path dossierBruts(String prefixe) {
        return dossierSession(prefixe).resolve(SOUS_DOSSIER_BRUTS);
    }

    /// Sous-dossier `bruts/` d'une session **désignée par son dossier racine** (le `root_path` persisté),
    /// et non par son préfixe : utile pour purger les originaux d'une session existante sans reconstruire
    /// son préfixe. Complément de [#dossierBruts(String)].
    public Path dossierBrutsDeSession(Path racineSession) {
        return racineSession.resolve(SOUS_DOSSIER_BRUTS);
    }

    /// Sous-dossier `transformes/` d'une session (séquences R8 + CSV R23).
    public Path dossierTransformes(String prefixe) {
        return dossierSession(prefixe).resolve(SOUS_DOSSIER_TRANSFORMES);
    }

    @Override
    public String toString() {
        // La base est nommée seulement quand elle a quitté la racine : sinon on la répéterait dans
        // chaque message d'erreur alors qu'elle s'en déduit.
        return cheminBase.equals(racine.resolve(FICHIER_BASE))
                ? "Workspace[" + racine + "]"
                : "Workspace[" + racine + ", base=" + cheminBase + "]";
    }
}
