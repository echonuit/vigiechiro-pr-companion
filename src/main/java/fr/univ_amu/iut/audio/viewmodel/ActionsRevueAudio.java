package fr.univ_amu.iut.audio.viewmodel;

import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MarquageDouteux;
import fr.univ_amu.iut.validation.model.ModeRevue;
import fr.univ_amu.iut.validation.model.RevueEnLot;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.Taxon;
import fr.univ_amu.iut.validation.model.ValidationManuelle;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/// Actions de revue de la vue audio, **unitaires** (sur la sélection) et **en lot** (#479), extraites du
/// [AudioViewModel] pour qu'il garde la seule responsabilité d'orchestrer la revue (cohésion, seuil PMD
/// GodClass). Chaque action délègue au service ([ServiceValidation] pour l'unitaire, [RevueEnLot] pour les
/// lots atomiques), **recharge** la source et publie un **retour** via [MessagesAudio] ; les erreurs métier
/// sont restituées dans le bandeau sans lever.
final class ActionsRevueAudio {

    private final ServiceValidation service;
    private final ValidationManuelle validationManuelle;
    private final MarquageDouteux marquageDouteux;
    private final RevueEnLot revueEnLot;
    private final Supplier<LigneObservationAudio> selection;
    private final Supplier<ModeRevue> mode;
    private final Runnable recharger;
    private final MessagesAudio messages;

    ActionsRevueAudio(
            ServiceValidation service,
            ValidationManuelle validationManuelle,
            MarquageDouteux marquageDouteux,
            RevueEnLot revueEnLot,
            Supplier<LigneObservationAudio> selection,
            Supplier<ModeRevue> mode,
            Runnable recharger,
            MessagesAudio messages) {
        this.service = service;
        this.validationManuelle = validationManuelle;
        this.marquageDouteux = marquageDouteux;
        this.revueEnLot = revueEnLot;
        this.selection = selection;
        this.mode = mode;
        this.recharger = recharger;
        this.messages = messages;
    }

    // --- Actions unitaires (sur l'observation sélectionnée) ---

    boolean valider() {
        return surSelection(courant -> appliquer(() -> service.validerSelonMode(courant.idObservation(), mode.get())));
    }

    boolean corriger(Taxon taxon) {
        if (taxon == null) {
            return false;
        }
        LigneObservationAudio courant = selection.get();
        if (courant == null) {
            return false;
        }
        // Séquence non identifiée (sans observation) : « corriger » = la valider à la main, ce qui crée une
        // observation manuelle portant le taxon choisi (sans proposition Tadarida).
        if (courant.idObservation() == null) {
            return appliquer(() -> validationManuelle.valider(courant.idSequence(), taxon.code()));
        }
        if (taxon.code().equals(courant.taxonTadarida())) {
            messages.info("Pour retenir la proposition Tadarida, utilisez « Valider » : corriger attend"
                    + " un autre taxon.");
            return false;
        }
        return appliquer(() -> service.corriger(courant.idObservation(), taxon.code(), null));
    }

    boolean basculerReference() {
        return surSelection(
                courant -> appliquer(() -> service.marquerReference(courant.idObservation(), !courant.reference())));
    }

    boolean basculerDouteux() {
        return surSelection(
                courant -> appliquer(() -> marquageDouteux.marquer(courant.idObservation(), !courant.douteux())));
    }

    boolean commenter(long idObservation, String texte) {
        return appliquer(() -> service.commenter(idObservation, texte));
    }

    // --- Actions en lot (sur une liste d'identifiants), atomiques (#479) ---

    int validerLot(List<Long> ids) {
        return appliquerLot("validée(s)", () -> revueEnLot.valider(ids));
    }

    int corrigerLot(List<Long> ids, Taxon taxon) {
        return appliquerLot("corrigée(s)", () -> revueEnLot.corriger(ids, taxon.code()));
    }

    int marquerReferenceLot(List<Long> ids, boolean reference) {
        return appliquerLot(
                reference ? "marquée(s) référence" : "retirée(s) des références",
                () -> revueEnLot.marquerReference(ids, reference));
    }

    // --- Rouages ---

    private boolean surSelection(Predicate<LigneObservationAudio> action) {
        LigneObservationAudio courant = selection.get();
        // Une séquence non identifiée (sans observation) n'est pas validable/corrigeable en l'état : les
        // actions de revue ne s'appliquent qu'à une ligne portant une observation (idObservation non nul).
        return courant != null && courant.idObservation() != null && action.test(courant);
    }

    private boolean appliquer(Runnable action) {
        try {
            action.run();
            recharger.run();
            messages.effacerRetour();
            return true;
        } catch (RuntimeException echec) {
            messages.erreur(echec.getMessage());
            return false;
        }
    }

    private int appliquerLot(String libelle, IntSupplier action) {
        try {
            int traites = action.getAsInt();
            recharger.run();
            messages.succes(traites + " observation(s) " + libelle + ".");
            return traites;
        } catch (RuntimeException echec) {
            messages.erreur(echec.getMessage());
            return 0;
        }
    }
}
