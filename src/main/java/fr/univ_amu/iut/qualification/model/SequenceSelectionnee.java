package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.VerdictFichier;
import java.util.Objects;

/// Rattachement d'une séquence d'écoute à une [SelectionDEcoute] : une ligne de la table de
/// jonction N..N `selection_sequence` (C11 ↔ C8).
///
/// Elle porte **deux** verdicts et non un : le nôtre, et celui d'un relecteur à qui la nuit a été
/// confiée. Ils coexistent et ne fusionnent jamais ; l'avis du relecteur s'affiche, il ne vote pas,
/// et `AgregationVerdict` ne le voit pas. Le pourquoi est dans l'ADR 4517.
///
/// La clé primaire composite est `(selection_id, sequence_id)`.
///
/// @param idSelection identifiant de la sélection (FK → `listening_selection.id`)
/// @param idSequence identifiant de la séquence rattachée (FK → `listening_sequence.id`)
/// @param position rang d'affichage dans la sélection (≥ 0)
/// @param ecoutee `true` si la séquence a déjà été écoutée (flag `listened`)
/// @param verdict verdict par fichier posé **ici** ([VerdictFichier#NON_JUGE] par défaut)
/// @param verdictRelecteur verdict rapporté par un relecteur, [VerdictFichier#NON_JUGE] tant
///     qu'aucun avis n'est revenu ; jamais dérivé de [#verdict]
/// @param pseudoRelecteur pseudo de qui a posé [#verdictRelecteur], `null` s'il n'y en a pas
public record SequenceSelectionnee(
        Long idSelection,
        Long idSequence,
        int position,
        boolean ecoutee,
        VerdictFichier verdict,
        VerdictFichier verdictRelecteur,
        String pseudoRelecteur) {

    public SequenceSelectionnee {
        verdict = verdict == null ? VerdictFichier.NON_JUGE : verdict;
        verdictRelecteur = verdictRelecteur == null ? VerdictFichier.NON_JUGE : verdictRelecteur;
    }

    /// Constructeur de compatibilité (sans verdict, #1524) : préserve les appels antérieurs au verdict
    /// par fichier ; le verdict retombe sur [VerdictFichier#NON_JUGE].
    public SequenceSelectionnee(Long idSelection, Long idSequence, int position, boolean ecoutee) {
        this(idSelection, idSequence, position, ecoutee, VerdictFichier.NON_JUGE);
    }

    /// Constructeur de compatibilité (sans relecteur, #4624) : un rattachement sans avis rapporté.
    public SequenceSelectionnee(
            Long idSelection, Long idSequence, int position, boolean ecoutee, VerdictFichier verdict) {
        this(idSelection, idSequence, position, ecoutee, verdict, VerdictFichier.NON_JUGE, null);
    }

    /// Copie avec un nouveau verdict **local** : l'avis du relecteur est préservé.
    public SequenceSelectionnee avecVerdict(VerdictFichier nouveau) {
        return new SequenceSelectionnee(
                idSelection,
                idSequence,
                position,
                ecoutee,
                Objects.requireNonNull(nouveau),
                verdictRelecteur,
                pseudoRelecteur);
    }

    /// `true` si un relecteur a rendu un avis : il faut **quoi** et **qui**, pas l'un des deux.
    public boolean porteUnAvisDeRelecteur() {
        return verdictRelecteur != VerdictFichier.NON_JUGE && pseudoRelecteur != null;
    }
}
