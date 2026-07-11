package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.LigneSuivi;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;

/// Ligne **observable** de la table de dépôt VigieChiro (#983) : une unité téléversée (archive ZIP ou
/// séquence WAV) et son avancement, réhydratée depuis `depot_unite` (#981) et mise à jour en direct
/// pendant un dépôt. Spécialise le socle [LigneSuivi] avec les colonnes propres au dépôt : le nom du
/// fichier (la clé de reprise) et sa nature.
///
/// Les mutateurs sont réservés au pilote ([SuiviLignesDepot]), à appeler sur le **fil JavaFX**.
public final class LigneDepot extends LigneSuivi {

    private final String identifiant;
    private final TypeDepotUnite type;

    LigneDepot(int numero, String identifiant, TypeDepotUnite type) {
        super(numero);
        this.identifiant = identifiant;
        this.type = type;
    }

    /// Nom du fichier téléversé (unique par passage, clé de ciblage des événements de dépôt).
    public String identifiant() {
        return identifiant;
    }

    /// Nature de l'unité (archive ZIP ou séquence WAV).
    public TypeDepotUnite type() {
        return type;
    }
}
