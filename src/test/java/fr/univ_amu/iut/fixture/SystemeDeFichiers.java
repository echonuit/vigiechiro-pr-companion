package fr.univ_amu.iut.fixture;

import java.nio.file.FileSystems;

/// Ce que le système de fichiers du banc sait faire, pour les cas qui l'exigent.
///
/// L'ADR 3802 demande qu'un comportement de plateforme passe par une couture d'injection plutôt que
/// d'être supposé, et que le test qui reste soit éprouvable partout. Le prédicat vivait dans
/// `EcritureAtomiqueTest`, seul cas à en avoir besoin ; deux autres classes en ont eu besoin le
/// 2026-09-05 et l'ont **supposé** au lieu de l'appeler, ce qui a rendu deux erreurs sous Windows
/// avant leur première assertion (#5435).
///
/// L'emploi est déclaratif, jamais en cours de route :
///
/// ```java
/// @EnabledIf("fr.univ_amu.iut.fixture.SystemeDeFichiers#posixDisponible")
/// ```
///
/// Un `assumeTrue` posé au milieu d'un test interrompt le cas **appelant** et emporte les assertions
/// qui n'avaient rien de POSIX : le rapport présente alors un test partiel comme sauté. C'est le
/// défaut que #3778 a payé, et la raison pour laquelle cette classe n'expose pas d'assertion.
public final class SystemeDeFichiers {

    private SystemeDeFichiers() {}

    /// Vrai quand le système de fichiers par défaut porte la vue `posix`, donc quand les permissions
    /// se posent et se lisent. NTFS ne la porte pas, et `Files.setPosixFilePermissions` y jette
    /// `UnsupportedOperationException`.
    public static boolean posixDisponible() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }
}
