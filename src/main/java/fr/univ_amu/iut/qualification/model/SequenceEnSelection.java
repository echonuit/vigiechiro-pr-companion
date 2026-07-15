package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.VerdictFichier;
import fr.univ_amu.iut.passage.model.SequenceDEcoute;

/// Ligne de la sélection d'écoute affichée dans M-Qualification : une séquence retenue
/// ([SequenceDEcoute] : nom de fichier R7, durée, chemin pour l'écoute), sa position d'affichage,
/// son flag « écoutée » et son [#verdict] par fichier (#1524, lot 6).
///
/// Jointure de lecture pure (`selection_sequence` × `listening_sequence`) produite par
/// [ServiceQualification#detaillerSelection] : immuable, sans dépendance JavaFX.
///
/// @param sequence la séquence d'écoute retenue
/// @param position position d'affichage dans la sélection (≥ 0, ordre de relecture)
/// @param ecoutee `true` si la séquence a déjà été écoutée
/// @param verdict verdict par fichier rendu à l'écoute ([VerdictFichier#NON_JUGE] par défaut)
public record SequenceEnSelection(SequenceDEcoute sequence, int position, boolean ecoutee, VerdictFichier verdict) {

    public SequenceEnSelection {
        verdict = verdict == null ? VerdictFichier.NON_JUGE : verdict;
    }

    /// Constructeur de compatibilité (sans verdict) : le verdict retombe sur
    /// [VerdictFichier#NON_JUGE]. Préserve les appels antérieurs au verdict par fichier.
    public SequenceEnSelection(SequenceDEcoute sequence, int position, boolean ecoutee) {
        this(sequence, position, ecoutee, VerdictFichier.NON_JUGE);
    }

    /// Copie avec un nouveau verdict (séquence, position, écoutée inchangés).
    public SequenceEnSelection avecVerdict(VerdictFichier nouveau) {
        return new SequenceEnSelection(sequence, position, ecoutee, nouveau);
    }
}
