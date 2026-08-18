package fr.univ_amu.iut.lot.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.LigneSuivi;
import fr.univ_amu.iut.lot.model.TypeDepotUnite;
import java.time.Duration;

/// Ligne **observable** de la table de dépôt VigieChiro (#983) : une unité téléversée (archive ZIP ou
/// séquence WAV) et son avancement, réhydratée depuis `depot_unite` (#981) et mise à jour en direct
/// pendant un dépôt. Spécialise le socle [LigneSuivi] avec les colonnes propres au dépôt : le nom du
/// fichier (la clé de reprise) et sa nature.
///
/// Les mutateurs sont réservés au pilote ([SuiviLignesDepot]), à appeler sur le **fil JavaFX**.
public final class LigneDepot extends LigneSuivi {

    private final String identifiant;
    private final TypeDepotUnite type;

    /// Vrai quand l'échec de cette unité ne se retente pas (#3687). Simple champ et non propriété
    /// observable : rien ne le lit en direct, il est agrégé au recalcul comme les autres états.
    private boolean definitif;

    LigneDepot(int numero, String identifiant, TypeDepotUnite type) {
        super(numero);
        this.identifiant = identifiant;
        this.type = type;
    }

    /// Une coupure momentanée est réessayée (#2354) : affiche « Nouvelle tentative dans N s… » via la
    /// mention de reprise du socle. L'unité reste « en cours » ; la mention s'efface d'elle-même dès que
    /// l'envoi reprend ou que l'unité se conclut (cf. [LigneSuivi]).
    public void reprise(Duration delai) {
        signalerReprise("Nouvelle tentative dans " + delai.toSeconds() + " s…");
    }

    /// Marque l'échec comme **définitif** (#3687) : le serveur a refusé pour une raison qu'un nouvel
    /// essai ne changera pas (URL signée expirée, jeton mort, corps refusé).
    ///
    /// La distinction ne se devine pas du texte de la raison - la même panne s'y écrit de trop de
    /// façons - mais vient de `ReponseApi.estReessayable()`, décidé à l'émission et transporté jusqu'ici.
    public void echouerDefinitivement(String raison) {
        echouer(raison);
        definitif = true;
    }

    /// `true` si un nouvel essai est **inutile** : cette unité ne doit pas être reproposée à la reprise.
    public boolean echecDefinitif() {
        return definitif;
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
