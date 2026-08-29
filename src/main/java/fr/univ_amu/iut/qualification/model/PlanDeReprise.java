package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.commun.model.VerdictFichier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Ce qu'une reprise d'avis écrira, **avant** qu'une ligne le soit (#4627, ADR 4517 et ADR 4627).
///
/// Le patron est celui de [fr.univ_amu.iut.passage.model.PlanDePaquet] : on planifie sans écrire, et
/// ce qu'on ne peut pas ranger est nommé plutôt qu'écarté en silence (article A3).
///
/// @param aAppliquer les verdicts du relecteur à poser, dans l'ordre de la sélection
/// @param refus ce qui interdit d'écrire, nommé ; un plan qui refuse n'applique rien
/// @param avisDejaPresent l'avis que la reprise remplacerait, `null` s'il n'y en a pas
public record PlanDeReprise(List<VerdictRepris> aAppliquer, List<String> refus, AvisDejaPresent avisDejaPresent) {

    /// Un verdict de relecteur à poser sur une séquence de la sélection.
    ///
    /// @param idSequence la séquence jugée
    /// @param verdict ce que le relecteur en a dit
    public record VerdictRepris(Long idSequence, VerdictFichier verdict) {}

    /// L'avis qui occupe déjà les deux colonnes, et ce que le remplacer coûterait.
    ///
    /// @param pseudo qui l'a posé
    /// @param verdicts combien de verdicts seraient définitivement perdus
    public record AvisDejaPresent(String pseudo, int verdicts) {}

    public PlanDeReprise {
        aAppliquer = List.copyOf(Objects.requireNonNull(aAppliquer, "aAppliquer"));
        refus = List.copyOf(Objects.requireNonNull(refus, "refus"));
    }

    /// Le plan d'une reprise, **sans rien écrire** : il confronte l'avis à la sélection reçue.
    ///
    /// @param selection les rattachements de la sélection, dans leur ordre d'affichage
    /// @param avis ce que le relecteur renvoie
    /// @return le plan, avec ce qu'il posera, ce qui l'en empêche, et ce qu'il remplacerait
    public static PlanDeReprise pour(List<SequenceSelectionnee> selection, AvisRevenu avis) {
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(avis, "avis");

        Set<Long> connues = new LinkedHashSet<>();
        for (SequenceSelectionnee rattachement : selection) {
            connues.add(rattachement.idSequence());
        }

        List<String> refus = new ArrayList<>();
        for (Long jugee : avis.verdicts().keySet()) {
            if (!connues.contains(jugee)) {
                refus.add("la séquence " + jugee + " ne fait pas partie de la sélection : ce paquet ne"
                        + " correspond pas à cette nuit");
            }
        }

        List<VerdictRepris> aAppliquer = new ArrayList<>();
        if (refus.isEmpty()) {
            for (SequenceSelectionnee rattachement : selection) {
                VerdictFichier rapporte = avis.verdicts().get(rattachement.idSequence());
                if (rapporte != null) {
                    aAppliquer.add(new VerdictRepris(rattachement.idSequence(), rapporte));
                }
            }
        }
        return new PlanDeReprise(aAppliquer, refus, avisDejaPresent(selection));
    }

    /// `true` si le plan interdit d'écrire.
    public boolean refuse() {
        return !refus.isEmpty();
    }

    /// `true` si écrire remplacerait l'avis d'un autre relecteur.
    public boolean demandeConfirmation() {
        return avisDejaPresent != null;
    }

    private static AvisDejaPresent avisDejaPresent(List<SequenceSelectionnee> selection) {
        String pseudo = null;
        int verdicts = 0;
        for (SequenceSelectionnee rattachement : selection) {
            if (rattachement.porteUnAvisDeRelecteur()) {
                pseudo = rattachement.pseudoRelecteur();
                verdicts++;
            }
        }
        return pseudo == null ? null : new AvisDejaPresent(pseudo, verdicts);
    }
}
