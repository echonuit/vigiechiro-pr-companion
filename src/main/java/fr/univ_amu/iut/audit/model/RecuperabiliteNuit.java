package fr.univ_amu.iut.audit.model;

import java.util.Objects;

/// Ce que deviendrait **une nuit** si l'on repartait d'une base neuve (#1151).
///
/// @param idPassage passage concerné
/// @param libelle de quelle nuit on parle (carré, point, date) : l'utilisateur ne raisonne pas en `id`
/// @param source d'où l'audio reviendrait (ou pas)
/// @param sequencesPresentes séquences effectivement sur le disque
/// @param sequencesTotal séquences que la base connaît
/// @param motif pourquoi ce verdict, en une phrase
public record RecuperabiliteNuit(
        Long idPassage, String libelle, SourceAudio source, int sequencesPresentes, int sequencesTotal, String motif) {

    public RecuperabiliteNuit {
        Objects.requireNonNull(idPassage, "idPassage");
        Objects.requireNonNull(libelle, "libelle");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(motif, "motif");
    }

    /// Ligne prête à afficher, en IHM comme en CLI.
    public String enClair() {
        return libelle + " : " + source + " (" + motif + ")";
    }
}
