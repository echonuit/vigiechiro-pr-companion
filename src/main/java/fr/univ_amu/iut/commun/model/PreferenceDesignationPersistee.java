package fr.univ_amu.iut.commun.model;

import com.google.inject.Inject;
import java.util.Objects;

/// L'implémentation **persistée** de [PreferenceDesignation], liée par `PersistenceModule` là où une
/// base existe.
///
/// Elle lit **au moment de servir**, et non une fois au démarrage. `PreferenceConservation` a payé
/// cette leçon (#3471) : prise en instantané dans son constructeur, elle ne voyait jamais un réglage
/// changé depuis l'écran, et réécrivait même la valeur périmée par-dessus.
public final class PreferenceDesignationPersistee implements PreferenceDesignation {

    private final Reglages reglages;

    @Inject
    public PreferenceDesignationPersistee(Reglages reglages) {
        this.reglages = Objects.requireNonNull(reglages, "reglages");
    }

    @Override
    public boolean dialogueDeLApplication() {
        return reglages.lireBooleen(ReglageDesignation.CLE, ReglageDesignation.DEFAUT);
    }
}
