package fr.univ_amu.iut.saison.viewmodel;

import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// État observable de l'écran **M-Saison** : une ligne par point suivi, plus le résumé d'en-tête et le
/// signalement de fenêtre, tous **dérivés du même** [SoldeSaison] (ils ne peuvent pas diverger).
///
/// Sans dépendance à `javafx.scene/fxml/stage` (règle `viewmodel_sans_javafx_ui`) : il consomme
/// [ServiceSoldeSaison] et publie des propriétés observables que la vue lie. Constructeur simple,
/// assemblé par `SaisonModule`.
public class SaisonViewModel {

    private static final DateTimeFormatter JOUR_MOIS = DateTimeFormatter.ofPattern("dd/MM");

    /// En deçà de ce nombre de jours avant la fermeture de la fenêtre du second passage, on signale.
    private static final int SEUIL_FENETRE_PROCHE_JOURS = 45;

    /// Nombre de saisons antérieures consultables (en lecture) proposées au sélecteur.
    private static final int SAISONS_ANTERIEURES = 2;

    private final ServiceSoldeSaison service;
    private final String idUtilisateur;

    private final ObservableList<LigneSaison> lignes = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyStringWrapper signalement = new ReadOnlyStringWrapper(this, "signalement", "");
    private final ReadOnlyIntegerWrapper annee = new ReadOnlyIntegerWrapper(this, "annee", 0);

    public SaisonViewModel(ServiceSoldeSaison service, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
    }

    /// Charge le solde de la **saison courante** (année de l'horloge du service).
    public void chargerCourant() {
        publier(service.soldeCourant(idUtilisateur));
    }

    /// Charge le solde de la saison `annee`.
    public void charger(int annee) {
        publier(service.soldePour(idUtilisateur, annee));
    }

    private void publier(SoldeSaison solde) {
        lignes.setAll(solde.lignes());
        annee.set(solde.annee());
        resume.set(construireResume(solde));
        signalement.set(construireSignalement(solde));
    }

    private static String construireResume(SoldeSaison solde) {
        return solde.pointsSuivis() + " point(s) suivi(s) · "
                + solde.passagesFaits() + "/" + solde.passagesAttendus() + " passage(s) fait(s) · "
                + "fenêtre du second passage jusqu'au "
                + solde.echeanceSecondPassage().format(JOUR_MOIS);
    }

    private static String construireSignalement(SoldeSaison solde) {
        long jours = solde.joursAvantEcheanceSecondPassage();
        long points = solde.pointsSecondPassageEnAttente();
        if (points == 0 || jours < 0 || jours > SEUIL_FENETRE_PROCHE_JOURS) {
            return "";
        }
        return "La fenêtre du second passage se referme dans " + jours + " jour(s) pour " + points + " point(s).";
    }

    /// Années proposées au sélecteur : la saison chargée et les `SAISONS_ANTERIEURES` précédentes
    /// (consultables en lecture). Vide tant qu'aucun solde n'a été chargé.
    public List<Integer> anneesProposees() {
        int courante = annee.get();
        if (courante == 0) {
            return List.of();
        }
        return IntStream.rangeClosed(0, SAISONS_ANTERIEURES)
                .mapToObj(decalage -> courante - decalage)
                .toList();
    }

    public ObservableList<LigneSaison> lignes() {
        return lignes;
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty signalementProperty() {
        return signalement.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty anneeProperty() {
        return annee.getReadOnlyProperty();
    }

    public int annee() {
        return annee.get();
    }
}
