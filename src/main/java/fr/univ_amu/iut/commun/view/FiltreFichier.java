package fr.univ_amu.iut.commun.view;

import java.util.Objects;

/// Types de fichiers proposés par un [SelecteurFichier] (« Sauvegarde SQLite (*.db) »).
///
/// @param libelle ce que l'utilisateur lit dans la liste des types
/// @param motif motif de nom accepté (« *.db »)
public record FiltreFichier(String libelle, String motif) {

    public FiltreFichier {
        Objects.requireNonNull(libelle, "libelle");
        Objects.requireNonNull(motif, "motif");
    }

    /// Sauvegarde de la base : le seul type que le socle sache restaurer.
    public static FiltreFichier baseSqlite() {
        return new FiltreFichier("Sauvegarde SQLite (*.db)", "*.db");
    }

    /// Archive d'une nuit de capture (#139) : décompressée de façon transparente à l'import.
    public static FiltreFichier archiveZip() {
        return new FiltreFichier("Archive ZIP", "*.zip");
    }

    /// Tableur : le format d'échange des exports (inventaires, vues, observations) et des résultats
    /// Tadarida.
    public static FiltreFichier csv() {
        return new FiltreFichier("CSV", "*.csv");
    }

    /// Image PNG : le format des exports **graphiques** (la courbe d'activité redessinée, #2352).
    public static FiltreFichier png() {
        return new FiltreFichier("Image PNG (*.png)", "*.png");
    }
}
