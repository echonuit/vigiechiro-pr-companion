package fr.univ_amu.iut.saison.viewmodel;

import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.saison.model.LigneSaison;
import fr.univ_amu.iut.saison.model.ServiceSoldeSaison;
import fr.univ_amu.iut.saison.model.SoldeSaison;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

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

    /// Les lignes que les deux filtres de l'écran (#3103) laissent passer : ce que la **table** montre.
    /// [#lignes] reste le solde entier de la saison, sur lequel se calculent le résumé et le
    /// signalement. Chercher un lieu ne change pas ce qu'il y a à faire cette année.
    private final FilteredList<LigneSaison> lignesFiltrees = new FilteredList<>(lignes);

    private final Filtres<LigneSaison> filtres = new Filtres<>(lignesFiltrees, () -> {});
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    private final ReadOnlyStringWrapper signalement = new ReadOnlyStringWrapper(this, "signalement", "");
    private final ReadOnlyIntegerWrapper annee = new ReadOnlyIntegerWrapper(this, "annee", 0);

    /// Campagnes proposées au filtre (#2610), avec la sentinelle `null` « Toutes les campagnes » en
    /// tête. Vide si la fonctionnalité `campagne` est coupée : la vue n'affiche alors pas le sélecteur.
    private final ObservableList<Campagne> campagnes = FXCollections.observableArrayList();

    /// Campagne retenue, `null` = pas de restriction. Mémorisée entre deux chargements : changer
    /// d'année ne doit pas faire oublier la campagne qu'on suivait.
    private final ObjectProperty<Campagne> campagneSelectionnee =
            new SimpleObjectProperty<>(this, "campagneSelectionnee");

    public SaisonViewModel(ServiceSoldeSaison service, String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        // Changer de campagne recharge la saison affichée : le tableau ET le résumé se restreignent
        // ensemble, ils viennent du même solde (critère de #2356).
        campagneSelectionnee.addListener((obs, ancienne, nouvelle) -> rechargerSaisonCourante());
    }

    /// Charge le solde de la **saison courante** (année de l'horloge du service), et peuple la liste
    /// des campagnes proposables.
    public void chargerCourant() {
        chargerCampagnes();
        publier(service.soldeCourant(idUtilisateur, nomCampagneRetenue()));
    }

    /// Charge le solde de la saison `annee`, restreint à la campagne retenue s'il y en a une.
    public void charger(int annee) {
        publier(service.soldePour(idUtilisateur, annee, nomCampagneRetenue()));
    }

    /// Recharge la saison **déjà affichée** après un changement de campagne. Sans effet tant qu'aucune
    /// saison n'a été chargée (l'écran s'ouvre par [#chargerCourant]).
    private void rechargerSaisonCourante() {
        if (annee.get() != 0) {
            charger(annee.get());
        }
    }

    /// Peuple la liste des campagnes proposées, sentinelle « toutes » en tête. Vide si la
    /// fonctionnalité est coupée : la vue n'affiche alors pas le sélecteur.
    private void chargerCampagnes() {
        List<Campagne> proposables = service.campagnesProposables();
        campagnes.clear();
        if (!proposables.isEmpty()) {
            campagnes.add(null); // sentinelle « Toutes les campagnes »
            campagnes.addAll(proposables);
        }
    }

    /// Nom de la campagne retenue, ou `null` pour ne pas restreindre. Le service filtre sur le **nom**
    /// (correspondance partielle) : un nom exact issu de la liste y répond exactement.
    private String nomCampagneRetenue() {
        Campagne retenue = campagneSelectionnee.get();
        return retenue == null ? null : retenue.nom();
    }

    /// Campagnes proposées au sélecteur (vide = pas de sélecteur à afficher).
    public ObservableList<Campagne> campagnes() {
        return campagnes;
    }

    /// Campagne retenue par le filtre, `null` = toute la saison.
    public ObjectProperty<Campagne> campagneSelectionneeProperty() {
        return campagneSelectionnee;
    }

    private void publier(SoldeSaison solde) {
        lignes.setAll(solde.lignes());
        annee.set(solde.annee());
        resume.set(construireResume(solde));
        signalement.set(construireSignalement(solde));
    }

    /// Résumé d'en-tête : la **ventilation** des passages attendus, puis l'échéance.
    ///
    /// Il ventile au lieu d'annoncer « 5/10 » parce qu'une proportion seule laisse deviner où sont les
    /// manquants ; ici la somme des trois nombres vaut le total, garanti par [SoldeSaison]. Les nuits
    /// hors protocole se disent **à part** : elles ont eu lieu, mais ne sont pas des passages attendus.
    private static String construireResume(SoldeSaison solde) {
        StringBuilder resume = new StringBuilder()
                .append(solde.pointsSuivis())
                .append(solde.pointsSuivis() > 1 ? " points suivis · " : " point suivi · ")
                .append(solde.passagesFaits())
                .append(" faits, ")
                .append(solde.passagesARefaire())
                .append(" à refaire, ")
                .append(solde.passagesARealiser())
                .append(" à réaliser");
        long horsProtocole = solde.nuitsHorsProtocole();
        if (horsProtocole > 0) {
            resume.append(" · ")
                    .append(horsProtocole)
                    .append(horsProtocole > 1 ? " nuits hors protocole" : " nuit hors protocole");
        }
        return resume.append(" · fenêtre du second passage jusqu'au ")
                .append(solde.echeanceSecondPassage().format(JOUR_MOIS))
                .toString();
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

    /// Les lignes retenues par la recherche de lieu et la case « Reste à faire » (#3103) : ce que la
    /// table montre.
    public ObservableList<LigneSaison> lignesFiltrees() {
        return lignesFiltrees;
    }

    /// Socle de filtres composables (#537) sur les lignes de la saison. L'écran n'y branche que deux
    /// prédicats : l'année et la campagne restent des `ComboBox`, parce qu'elles disent la structure du
    /// travail et se lisent d'un coup d'œil (cf. [fr.univ_amu.iut.saison.view.CriteresSaison]).
    public Filtres<LigneSaison> filtres() {
        return filtres;
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
