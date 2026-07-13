package fr.univ_amu.iut.lot.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

/// État observable de la zone **« Traitement Vigie-Chiro »** de M-Lot (#1263) : où en est l'analyse de la
/// nuit déposée, et ce que l'observateur peut en faire.
///
/// Séparé de `LotViewModel`, déjà au plafond de complexité : y ajouter le suivi l'aurait fait basculer en
/// God Class — le même motif qui avait fait naître `DepotViewModel`.
///
/// **L'application ne surveille pas le serveur.** Elle relit son état quand on ouvre l'écran, après un
/// lancement, ou quand on le lui demande — jamais en boucle. Un calcul dure des dizaines de minutes : un
/// sondage périodique ne serait que du bruit, et le site officiel n'en fait pas davantage.
///
/// Hors connexion, la zone affiche le **dernier état connu** (cache #1262) plutôt que rien, en disant
/// honnêtement de quand il date.
public class TraitementViewModel {

    private final Optional<SuiviTraitement> suivi;
    private final Horloge horloge;

    /// Ce que dit l'analyse, en une phrase.
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");

    /// Fraîcheur de l'information (« Dernier état connu le … »), vide tant qu'on n'a rien relevé.
    private final ReadOnlyStringWrapper fraicheur = new ReadOnlyStringWrapper("");

    /// Avertissement quand le calcul traîne au-delà de 24 h, vide sinon.
    private final ReadOnlyStringWrapper alerte = new ReadOnlyStringWrapper("");

    /// Un relevé est en cours (le bouton « Actualiser » se met en attente).
    private final ReadOnlyBooleanWrapper enCours = new ReadOnlyBooleanWrapper(false);

    /// La nuit a **déjà été analysée** : relancer le calcul effacerait ses observations sans pouvoir les
    /// recalculer (#1244) — le bouton de lancement doit être gardé (#1261).
    private final ReadOnlyBooleanWrapper relanceBloquee = new ReadOnlyBooleanWrapper(false);

    @Inject
    public TraitementViewModel(Optional<SuiviTraitement> suivi, Horloge horloge) {
        this.suivi = Objects.requireNonNull(suivi, "suivi");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Le suivi est-il disponible ? Faux hors application connectée (outils de capture, mode hors ligne) :
    /// la zone est alors simplement absente.
    public boolean disponible() {
        return suivi.isPresent();
    }

    /// Affiche le **dernier état connu** sans toucher au réseau : à appeler sur le fil JavaFX à l'ouverture
    /// de l'écran, pour que la zone ne soit jamais muette.
    public void chargerDernierReleve(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        Optional<ReleveTraitement> releve = suivi.flatMap(moteur -> moteur.dernierReleve(idPassage));
        if (releve.isEmpty()) {
            reinitialiser();
            return;
        }
        appliquer(releve.get().traitement());
        fraicheur.set(FormatsTraitement.fraicheur(releve.get()));
    }

    /// Demande au serveur où il en est. **Bloquant** (réseau) : à appeler **hors du fil JavaFX**, via le
    /// socle [fr.univ_amu.iut.commun.view.ExecuteurTache].
    public Traitement relever(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return suivi.orElseThrow(() -> new IllegalStateException("Suivi VigieChiro indisponible."))
                .relever(idPassage);
    }

    /// Restitue un état fraîchement relevé, **sur le fil JavaFX**.
    public void appliquer(Traitement traitement) {
        Objects.requireNonNull(traitement, "traitement");
        message.set(FormatsTraitement.libelle(traitement));
        alerte.set(FormatsTraitement.alerte(traitement, horloge));
        // Une nuit terminée ou en échec a déjà été calculée : la relancer détruirait ses observations.
        relanceBloquee.set(traitement.resultatsDisponibles() || traitement.enEchec());
        fraicheur.set("À l'instant.");
        enCours.set(false);
    }

    /// Le relevé a échoué (serveur injoignable) : on le dit, sans effacer ce qu'on savait déjà.
    public void echec(String motif) {
        alerte.set("Impossible de joindre Vigie-Chiro : " + motif);
        enCours.set(false);
    }

    /// Marque un relevé en cours (bouton « Actualiser » cliqué), sur le fil JavaFX.
    public void marquerEnCours() {
        enCours.set(true);
    }

    /// Rien de connu : nuit jamais relevée (ou passage sans participation).
    private void reinitialiser() {
        message.set("Analyse non lancée : les observations n'existent pas encore côté Vigie-Chiro.");
        fraicheur.set("");
        alerte.set("");
        relanceBloquee.set(false);
        enCours.set(false);
    }

    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty fraicheurProperty() {
        return fraicheur.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty alerteProperty() {
        return alerte.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty enCoursProperty() {
        return enCours.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty relanceBloqueeProperty() {
        return relanceBloquee.getReadOnlyProperty();
    }
}
