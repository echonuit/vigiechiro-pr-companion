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
/// └── vigiechiro.db                    ← base SQLite (R21)
/// ```
public final class Workspace {

    /// Nom du fichier SQLite à la racine du workspace (R21).
    public static final String FICHIER_BASE = "vigiechiro.db";

    private static final String DOSSIER_DEFAUT = "VigieChiro-Companion";
    private static final String SOUS_DOSSIER_BRUTS = "bruts";
    private static final String SOUS_DOSSIER_TRANSFORMES = "transformes";

    private final Path racine;

    public Workspace(Path racine) {
        this.racine = racine.toAbsolutePath();
    }

    /// Workspace par défaut : `<home>/Documents/VigieChiro-Companion` (R21). Le dossier n'est
    /// pas créé ici : il le sera paresseusement à la première connexion (cf. `SourceDeDonnees`).
    public static Workspace parDefaut() {
        Path documents = Path.of(System.getProperty("user.home"), "Documents", DOSSIER_DEFAUT);
        return new Workspace(documents);
    }

    /// Dossier racine du workspace.
    public Path racine() {
        return racine;
    }

    /// Chemin du fichier de base SQLite (R21).
    public Path cheminBaseDeDonnees() {
        return racine.resolve(FICHIER_BASE);
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
        return "Workspace[" + racine + "]";
    }
}
