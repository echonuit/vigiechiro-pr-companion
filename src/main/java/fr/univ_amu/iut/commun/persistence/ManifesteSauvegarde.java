package fr.univ_amu.iut.commun.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Ce qu'une **sauvegarde complète** a emporté, écrit à sa racine dans [#NOM_FICHIER] (#2726).
///
/// C'est la mémoire qui manquait : une sauvegarde savait copier des dossiers, pas dire **d'où ils
/// venaient**. La restauration ne pouvait donc ni les remettre à leur place, ni corriger les
/// `root_path` de la base (#2727), et elle ne tenait sa promesse que si l'on restaurait sur la
/// machine et l'arborescence d'origine.
///
/// Le manifeste est **versionné** : une sauvegarde écrite aujourd'hui doit rester lisible par une
/// version ultérieure du produit, et une sauvegarde antérieure à ce format n'en a pas du tout. La
/// restauration doit donc traiter l'absence de manifeste comme un cas normal, pas comme une erreur.
///
/// @param version version du format, [#VERSION_COURANTE] à l'écriture
/// @param racines une entrée par racine de session effectivement copiée
public record ManifesteSauvegarde(int version, List<RacineSauvegardee> racines) {

    /// Nom du fichier, à la racine du dossier de sauvegarde.
    public static final String NOM_FICHIER = "manifeste.json";

    /// Version du format écrite par cette version du produit.
    public static final int VERSION_COURANTE = 1;

    public ManifesteSauvegarde {
        racines = List.copyOf(Objects.requireNonNull(racines, "racines"));
    }

    public static ManifesteSauvegarde courant(List<RacineSauvegardee> racines) {
        return new ManifesteSauvegarde(VERSION_COURANTE, racines);
    }

    /// La racine sauvegardée qui occupe le dossier `identifiant` sous `sessions/`, si elle y est.
    /// C'est ce que la restauration a sous la main en parcourant la sauvegarde : un nom de dossier,
    /// dont seul le manifeste sait d'où il venait.
    public Optional<RacineSauvegardee> pourIdentifiant(String identifiant) {
        return racines.stream()
                .filter(racine -> racine.identifiant().equals(identifiant))
                .findFirst();
    }
}
