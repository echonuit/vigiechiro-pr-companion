package fr.univ_amu.iut.qualification.model;

import fr.univ_amu.iut.qualification.model.dao.SelectionDao;
import java.util.Objects;

/// Pose l'avis d'un relecteur depuis un [PlanDeReprise] déjà établi (#4627, ADR 4517 et ADR 4627).
///
/// **Le plan commande.** La reprise ne recalcule pas ce qu'elle pose : elle suit ce que le plan a
/// annoncé, sans quoi l'utilisateur aurait confirmé un remplacement et en aurait obtenu un autre.
/// C'est la conduite de [fr.univ_amu.iut.passage.model.EcrivainPaquet].
public final class RepriseAvis {

    private RepriseAvis() {}

    /// Applique `plan`, ou refuse en disant ce qui l'en empêche.
    ///
    /// **Un plan qui refuse n'écrit rien, pas même sa part valide.** Un verdict hors de la sélection
    /// figée signale un paquet qui ne correspond pas à la nuit : en poser la moitié laisserait une
    /// base à demi reprise, que rien ne distinguerait d'une reprise complète.
    ///
    /// @param dao l'accès aux rattachements de la sélection
    /// @param idSelection la sélection visée
    /// @param plan ce qui a été annoncé, et qui fait foi
    /// @param avis l'avis revenu, dont le pseudo signe chaque verdict posé
    /// @param remplacementConfirme `true` quand l'utilisateur a confirmé d'écraser l'avis présent
    /// @return le nombre de verdicts posés
    /// @throws IllegalStateException si le plan refuse, ou s'il remplacerait un avis sans confirmation
    public static int appliquer(
            SelectionDao dao, Long idSelection, PlanDeReprise plan, AvisRevenu avis, boolean remplacementConfirme) {
        Objects.requireNonNull(dao, "dao");
        Objects.requireNonNull(idSelection, "idSelection");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(avis, "avis");

        if (plan.refuse()) {
            throw new IllegalStateException("L'avis n'est pas repris : " + String.join(" ; ", plan.refus()));
        }
        if (plan.demandeConfirmation() && !remplacementConfirme) {
            PlanDeReprise.AvisDejaPresent present = plan.avisDejaPresent();
            throw new IllegalStateException("L'avis de « " + present.pseudo() + " » et ses " + present.verdicts()
                    + " verdict(s) seraient définitivement remplacés : reprise non confirmée");
        }
        for (PlanDeReprise.VerdictRepris repris : plan.aAppliquer()) {
            dao.marquerAvisDeRelecteur(idSelection, repris.idSequence(), repris.verdict(), avis.pseudoRelecteur());
        }
        return plan.aAppliquer().size();
    }
}
