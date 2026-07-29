package fr.univ_amu.iut.lot.model;

import fr.univ_amu.iut.commun.model.ActionGroupee;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/// « Préparer le dépôt » appliqué à plusieurs passages (#2357, lot 3).
///
/// Première [ActionGroupee] du produit. Elle n'apporte **aucune règle** : [ServiceLot#preparerLot]
/// porte R14, les contrôles de cohérence et la transition de statut. Ce que cette classe ajoute est
/// la capacité de dire **à l'avance** ce qui va être écarté, et pourquoi.
///
/// ## Pourquoi l'éligibilité se relit, et ne se déduit pas de l'échec
///
/// On pourrait lancer et voir qui tombe. Mais « un lot qui ignore silencieusement la moitié de la
/// sélection est pire qu'un lot qui refuse » : l'observateur doit lire la liste des écartés **avant**
/// de décider. [#motifNonEligible] consulte donc l'état du lot sans rien transitionner
/// ([ServiceLot#consulterLot] n'écrit pas), ce qui la rend sûre à appeler sur toute la sélection.
///
/// Elle ne prétend pas être exhaustive : entre la consultation et l'exécution, la préparation peut
/// encore refuser. Ce cas-là devient un **échec** du passage, avec son motif, et le lot continue.
public class PreparationGroupee implements ActionGroupee {

    private final ServiceLot service;

    public PreparationGroupee(ServiceLot service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String libelle() {
        return "Préparer le dépôt";
    }

    /// Trois motifs d'écart, dans l'ordre où l'observateur les comprend : la nuit est déjà passée à
    /// l'étape suivante, son verdict l'interdit, ou un contrôle bloquant s'y oppose.
    @Override
    public Optional<String> motifNonEligible(CiblePassage cible) {
        EtatLot etat;
        try {
            etat = service.consulterLot(cible.idPassage());
        } catch (RegleMetierException introuvable) {
            return Optional.of(introuvable.getMessage());
        }
        if (etat.statut().estSurLaPlateforme() || etat.statut() == StatutWorkflow.DEPOT_EN_COURS) {
            // Une nuit récupérée tombait sinon jusqu'au « pas encore vérifié » du bas (#2581) : un refus
            // exact sur la lettre et faux sur le fond - ce n'est pas qu'elle attend d'être vérifiée,
            // c'est qu'elle est déjà là-bas.
            return Optional.of("déjà déposé");
        }
        if (etat.statut() == StatutWorkflow.PRET_A_DEPOSER) {
            return Optional.of("dépôt déjà préparé");
        }
        if (etat.statut() != StatutWorkflow.VERIFIE) {
            return Optional.of("pas encore vérifié");
        }
        return etat.controles().stream()
                .filter(ControleCoherence::estBloquant)
                .map(controle -> controle.libelle().toLowerCase(Locale.ROOT))
                .findFirst();
    }

    /// Le jeton est ignoré : la préparation est une transition **atomique** (contrôles puis changement
    /// de statut). Il n'y a pas d'entre-deux où s'arrêter, donc rien à honorer.
    @Override
    public void executer(CiblePassage cible, JetonAnnulation jeton) {
        service.preparerLot(cible.idPassage());
    }
}
