package fr.univ_amu.iut.lot.viewmodel;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ImportApresReleve;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.ReleveTraitement;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import java.time.ZoneId;
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
/// God Class : le même motif qui avait fait naître `DepotViewModel`.
///
/// **L'application ne surveille pas le serveur.** Elle relit son état quand on ouvre l'écran, après un
/// lancement, ou quand on le lui demande, jamais en boucle. Un calcul dure des dizaines de minutes : un
/// sondage périodique ne serait que du bruit, et le site officiel n'en fait pas davantage.
///
/// Hors connexion, la zone affiche le **dernier état connu** (cache #1262) plutôt que rien, en disant
/// honnêtement de quand il date.
public class TraitementViewModel {

    /// Le fuseau des heures affichées : celui du **poste**, comme la commande depuis #3678 (#5683). C'est
    /// l'heure de celui qui lit, et c'est elle qui lui dit s'il attend ou s'il revient plus tard.
    private static final ZoneId FUSEAU = ZoneId.systemDefault();

    private final Optional<SuiviTraitement> suivi;
    private final Optional<ImportObservations> importation;
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
    /// recalculer (#1244) : le bouton de lancement doit être gardé (#1261).
    private final ReadOnlyBooleanWrapper relanceBloquee = new ReadOnlyBooleanWrapper(false);

    /// L'analyse est demandée et la plateforme y travaille : planifiée, en cours, ou relancée (#5682). Lue
    /// dans le relevé, jamais dans la réponse au clic : elle survit ainsi à la réouverture de l'écran.
    private final ReadOnlyBooleanWrapper analyseDemandee = new ReadOnlyBooleanWrapper(false);

    /// Ce que le dernier relevé a fait des observations, ou ce qu'il reste à faire (#5784). Vide tant que
    /// l'analyse n'est pas terminée.
    private final ReadOnlyStringWrapper importObservations = new ReadOnlyStringWrapper("");

    /// Sans import : les outils de capture et les écrans montés hors connexion.
    public TraitementViewModel(Optional<SuiviTraitement> suivi, Horloge horloge) {
        this(suivi, Optional.empty(), horloge);
    }

    @Inject
    public TraitementViewModel(
            Optional<SuiviTraitement> suivi, Optional<ImportObservations> importation, Horloge horloge) {
        this.suivi = Objects.requireNonNull(suivi, "suivi");
        this.importation = Objects.requireNonNull(importation, "importation");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// Un relevé, et ce qu'il a fait des observations de la nuit.
    ///
    /// @param traitement l'état rendu par la plateforme
    /// @param issue ce que le relevé a fait des observations
    public record Releve(Traitement traitement, ImportApresReleve.Issue issue) {}

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
        Traitement traitement = releve.get().traitement();
        appliquer(traitement);
        // Aucun import ici, et aucun réseau : la carte dit seulement ce qu'il reste à faire (#5784).
        importObservations.set(ImportApresReleve.dejaImportee(importation, idPassage, traitement)
                .map(deja -> deja
                        ? FormatsTraitement.OBSERVATIONS_DEJA_IMPORTEES
                        : FormatsTraitement.OBSERVATIONS_A_IMPORTER)
                .orElse(""));
        fraicheur.set(FormatsTraitement.fraicheur(releve.get(), FUSEAU));
    }

    /// Demande au serveur où il en est. **Bloquant** (réseau) : à appeler **hors du fil JavaFX**, via le
    /// socle [fr.univ_amu.iut.commun.view.ExecuteurTache].
    public Traitement relever(Long idPassage) {
        Objects.requireNonNull(idPassage, "idPassage");
        return suivi.orElseThrow(() -> new IllegalStateException("Suivi Vigie-Chiro indisponible."))
                .relever(idPassage);
    }

    /// Relève l'état puis, s'il dit l'analyse terminée, importe les observations dans le même geste
    /// (#5784). **Bloquant** (réseau) : à appeler hors du fil JavaFX, comme [#relever].
    public Releve releverEtImporter(Long idPassage) {
        Traitement traitement = relever(idPassage);
        return new Releve(traitement, ImportApresReleve.pour(importation, idPassage, traitement));
    }

    /// Restitue un relevé et ce qu'il a fait des observations, **sur le fil JavaFX**.
    public void appliquer(Releve releve) {
        Objects.requireNonNull(releve, "releve");
        appliquer(releve.traitement());
        importObservations.set(FormatsTraitement.importObservations(releve.issue()));
    }

    /// Restitue un état fraîchement relevé, **sur le fil JavaFX**.
    public void appliquer(Traitement traitement) {
        Objects.requireNonNull(traitement, "traitement");
        message.set(FormatsTraitement.libelle(traitement, FUSEAU));
        alerte.set(FormatsTraitement.alerte(traitement, horloge));
        // Une nuit terminée ou en échec a déjà été calculée : la relancer détruirait ses observations.
        relanceBloquee.set(traitement.resultatsDisponibles() || traitement.enEchec());
        analyseDemandee.set(traitement.enAttente());
        importObservations.set("");
        fraicheur.set("À l'instant.");
        enCours.set(false);
    }

    /// Le relevé a échoué (serveur injoignable) : on le dit, sans effacer ce qu'on savait déjà.
    public void echec(String motif) {
        alerte.set("Impossible de joindre Vigie-Chiro : " + motif);
        enCours.set(false);
    }

    /// Échec **remonté d'une exception**. Le contexte que nous avons écrit reste entier ; seul ce qui
    /// vient d'ailleurs est enrichi puis borné (#2076).
    public void echec(Throwable refus) {
        alerte.set(RetourOperation.erreur("Impossible de joindre Vigie-Chiro", refus)
                .texte());
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
        importObservations.set("");
        relanceBloquee.set(false);
        analyseDemandee.set(false);
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

    /// Ce que le dernier relevé a fait des observations, vide s'il n'y a rien à en dire (#5784).
    public ReadOnlyStringProperty importObservationsProperty() {
        return importObservations.getReadOnlyProperty();
    }

    /// Vrai tant que la plateforme travaille sur une analyse demandée (#5682).
    public ReadOnlyBooleanProperty analyseDemandeeProperty() {
        return analyseDemandee.getReadOnlyProperty();
    }
}
