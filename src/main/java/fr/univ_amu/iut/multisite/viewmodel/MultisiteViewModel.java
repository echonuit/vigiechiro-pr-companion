package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

/// ViewModel de l'écran **M-Multisite** (vue agrégée des passages de tous les sites de
/// l'utilisateur, parcours P5, story E5, statut **SHOULD**).
///
/// Expose le tableau des [lignes][LignePassage], le **tri** ([TriMultisite]) et l'**export CSV**.
///
/// **Filtrage côté client (#537).** Les passages sont chargés **une seule fois** ([#rafraichir()]) puis
/// filtrés **en mémoire** via le socle partagé [Filtres] : la **barre à puces** de la vue (#537 étape 6b)
/// branche/retire ses prédicats sur [#filtres()], sans ré-interroger le service. Le **tri nommé**
/// ré-ordonne la liste publiée. Les **vues mémorisées** ne sont plus gérées ici : elles vivent dans le
/// composant partagé `commun.view.GestionnaireVues` (onglets « à la Notion »), adossé à la barre de filtres.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls
/// `javafx.beans`/`javafx.collections`. Non-singleton (un VM frais par chargement de vue).
public class MultisiteViewModel {

    private final ServiceMultisite service;
    private final String idUtilisateur;

    /// File des déplacements de points en attente (mode édition des positions, #154). Responsabilité
    /// extraite : le ViewModel l'expose, la vue la pilote.
    private final PositionsEnAttente positionsEnAttente;

    private final ObjectProperty<TriMultisite> tri = new SimpleObjectProperty<>(this, "tri", TriMultisite.PAR_SITE);

    /// Tous les passages de l'utilisateur, chargés une fois ([#rafraichir()]). Source **non filtrée**
    /// du socle : les filtres et le tri travaillent dessus en mémoire, sans ré-interroger le service.
    private final ObservableList<LignePassage> tousLesPassages = FXCollections.observableArrayList();

    private final FilteredList<LignePassage> passagesFiltres = new FilteredList<>(tousLesPassages);

    /// Lignes **publiées** vers la vue : sous-ensemble filtré, ré-ordonné par le tri nommé. La vue y
    /// pose par-dessus un [javafx.collections.transformation.SortedList] pour le tri par clic
    /// d'en-tête (#145) ; cette liste reste donc la même instance au fil des rafraîchissements.
    private final ObservableList<LignePassage> lignes = FXCollections.observableArrayList();

    /// Agrégat des carrés pour la carte (#152) : vue d'ensemble **non filtrée** (carrés + points + statut).
    private final ObservableList<CarreAgrege> carresCarte = FXCollections.observableArrayList();

    private final ReadOnlyBooleanWrapper nonVide = new ReadOnlyBooleanWrapper(this, "nonVide", false);
    private final ReadOnlyStringWrapper resume = new ReadOnlyStringWrapper(this, "resume", "");
    /// Retour de la dernière opération, avec sa sévérité, rendu dans le bandeau partagé (ADR 0023).
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Socle de filtres composables (#537) : recompose la conjonction sur [#passagesFiltres] puis
    /// publie via [#publierLignes()]. Déclaré après ses dépendances (la liste filtrée).
    private final Filtres<LignePassage> filtres = new Filtres<>(passagesFiltres, this::publierLignes);

    /// Relevé groupé de l'état des analyses (#1338), **optionnel** : présent seulement quand l'observateur
    /// est connecté à VigieChiro (même liaison que l'import). Absent, l'action de relève ne s'offre pas.
    private final Optional<SuiviTraitement> suivi;

    public MultisiteViewModel(
            ServiceMultisite service,
            ServiceSites serviceSites,
            Optional<SuiviTraitement> suivi,
            String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.suivi = Objects.requireNonNull(suivi, "suivi");
        this.positionsEnAttente = new PositionsEnAttente(serviceSites, this::rafraichirCarte, this::rapporterPosition);
        // Le tri nommé ne re-filtre pas : il ré-ordonne la liste publiée. Les filtres sont posés sur
        // [#filtres] par la barre à puces de la vue (#537 étape 6b).
        tri.addListener((obs, ancien, nouveau) -> publierLignes());
    }

    /// `true` si le **relevé groupé des analyses** (#1338) a un sens ici : l'observateur est connecté à
    /// VigieChiro. La vue n'offre l'action que dans ce cas (sinon, il n'y a rien à interroger).
    public boolean releveAnalysesDisponible() {
        return suivi.isPresent();
    }

    /// Identifiants des passages **déposés** actuellement chargés (source non filtrée) : ce sont les seules
    /// nuits dont l'analyse serveur existe. À lire **sur le fil JavaFX** (la liste observable), pour passer
    /// l'instantané à [#releverAnalyses(List)] qui, lui, part en tâche de fond.
    public List<Long> nuitsDeposees() {
        return tousLesPassages.stream()
                .filter(ligne -> ligne.statut() == StatutWorkflow.DEPOSE)
                .map(LignePassage::idPassage)
                .toList();
    }

    /// Relève l'état des analyses des `nuitsDeposees` fournies, **à la demande** et **hors du fil JavaFX**
    /// (#1338). Aucun sondage automatique : c'est un geste explicite. Best-effort, nuit par nuit (une qui
    /// échoue n'écrase pas son dernier état connu).
    ///
    /// Reçoit la liste en paramètre (capturée sur le fil JavaFX par l'appelant, cf. [#nuitsDeposees()])
    /// plutôt que de lire la liste observable depuis le fil de fond. Renvoie le compte rendu prêt à
    /// afficher — ou, si la liste est vide, un message qui le dit plutôt qu'un « 0 relevé » sec.
    /// Précondition : [#releveAnalysesDisponible()] vrai (l'appelant garde le bouton).
    public RetourOperation releverAnalyses(List<Long> nuitsDeposees) {
        Objects.requireNonNull(nuitsDeposees, "nuitsDeposees");
        SuiviTraitement moteur = suivi.orElseThrow(
                () -> new IllegalStateException("Relevé des analyses indisponible : connectez-vous à Vigie-Chiro."));
        if (nuitsDeposees.isEmpty()) {
            // Rien à relever n'est pas un échec : c'est un guidage.
            return RetourOperation.info("Aucune nuit déposée : il n'y a pas encore d'analyse à relever.");
        }
        return retourReleve(moteur.releverTout(nuitsDeposees));
    }

    /// Résultat d'un relevé groupé : le compte rendu à afficher **et** les données rechargées, pour que la
    /// vue applique les deux en une fois (#1338).
    public record ResultatReleve(RetourOperation retour, DonneesMultisite donnees) {}

    /// Relève l'état des analyses **puis relit** l'écran, le tout **hors du fil JavaFX** (#1338) : le
    /// nouvel état du cache doit se voir dans la colonne « Analyse » dès le retour, sans imbriquer une
    /// seconde occupation ni laisser le compte rendu se faire effacer par un rechargement concurrent.
    public ResultatReleve releverPuisCharger(List<Long> nuitsDeposees) {
        RetourOperation retour = releverAnalyses(nuitsDeposees);
        return new ResultatReleve(retour, charger());
    }

    /// Applique le résultat d'un relevé groupé **sur le fil JavaFX** : recompose le tableau (badges
    /// « Analyse » à jour), puis publie le compte rendu. L'ordre importe : [#appliquer] efface le message
    /// via `publierLignes`, donc le compte rendu est posé **après**.
    public void appliquerReleve(ResultatReleve resultat) {
        appliquer(resultat.donnees());
        retour.set(resultat.retour());
    }

    /// Compte rendu du relevé groupé : ce qui a été rafraîchi, et ce qui a échoué **sans mentir** sur une
    /// fraîcheur non obtenue (les nuits en échec gardent leur dernier état connu).
    private static RetourOperation retourReleve(SuiviTraitement.BilanReleveGroupe bilan) {
        if (bilan.echecs() == 0) {
            return RetourOperation.succes(
                    "État des analyses relevé pour " + bilan.rafraichis() + " nuit(s)" + " déposée(s).");
        }
        // Relevé **partiel** : rien n'a échoué au sens technique, mais tout n'a pas été rafraîchi. Ni un
        // succès (ce serait mentir sur la fraîcheur), ni une erreur (les données restent affichées).
        return RetourOperation.info("État relevé pour " + bilan.rafraichis() + " nuit(s) sur " + bilan.total() + " : "
                + bilan.echecs() + " injoignable(s), leur dernier état connu reste affiché.");
    }

    /// (Re)charge **tous** les passages de l'utilisateur, puis ré-applique filtres et tri courants.
    /// À appeler à l'ouverture de l'écran et après une modification des données (retour d'un passage
    /// édité). Les changements de filtre ou de tri **ne rechargent pas** : ils re-filtrent /
    /// ré-ordonnent en mémoire.
    public void rafraichir() {
        tousLesPassages.setAll(service.listerPassages(idUtilisateur));
        filtres.appliquer();
    }

    /// Données de l'écran chargées **hors du fil JavaFX** (#1209) : passages du tableau + agrégat des
    /// carrés de la carte, les deux requêtes base réunies pour une seule occupation.
    public record DonneesMultisite(List<LignePassage> passages, List<CarreAgrege> carte) {}

    /// **Lecture seule** des données de l'écran (deux requêtes base). Sans effet sur l'état observable :
    /// sûre à exécuter **hors du fil JavaFX** (#1209, déport via `IndicateurOccupation`).
    public DonneesMultisite charger() {
        return new DonneesMultisite(service.listerPassages(idUtilisateur), service.agregerPourCarte(idUtilisateur));
    }

    /// Applique des données chargées : recompose le tableau (avec filtres) et l'agrégat de la carte.
    /// **Mutations observables** : à exécuter **sur le fil JavaFX**.
    public void appliquer(DonneesMultisite donnees) {
        tousLesPassages.setAll(donnees.passages());
        filtres.appliquer();
        carresCarte.setAll(donnees.carte());
    }

    /// Route l'échec d'un chargement vers le message de l'écran (filet #795), à la place d'une exception
    /// non capturée remontant du fil de fond.
    public void signalerErreur(Throwable erreur) {
        String detail = erreur.getMessage();
        retour.set(RetourOperation.erreur(
                detail != null && !detail.isBlank() ? detail : "Chargement des passages impossible."));
    }

    /// Callback du socle (`apresApplication`) : ré-ordonne le sous-ensemble filtré selon le tri
    /// nommé, le publie dans [#lignes], et met à jour le résumé et l'indice d'état vide.
    private void publierLignes() {
        List<LignePassage> triees = new ArrayList<>(passagesFiltres);
        triees.sort(tri.get().comparateur());
        lignes.setAll(triees);
        nonVide.set(!lignes.isEmpty());
        resume.set(lignes.size() + " passage(s) affiché(s).");
        retour.set(RetourOperation.AUCUN);
    }

    /// (Re)charge l'agrégat des carrés pour la **carte** (#152), vue d'ensemble **non filtrée**.
    /// **Séparé** de [#rafraichir()] : la carte ne dépend ni des filtres ni du tri du tableau, donc on ne
    /// la recalcule pas à chaque changement de filtre/tri (coût inutile), mais seulement aux moments où les
    /// données changent (ouverture de l'écran, retour après modification d'un passage), à la charge de la
    /// vue (controller).
    public void rafraichirCarte() {
        carresCarte.setAll(service.agregerPourCarte(idUtilisateur));
    }

    /// File des déplacements de points **en attente** (mode édition de la carte, #154) : la vue y met les
    /// marqueurs glissés, puis enregistre ou abandonne. Voir [PositionsEnAttente].
    public PositionsEnAttente positionsEnAttente() {
        return positionsEnAttente;
    }

    /// Exporte les lignes **internes** du tableau (sous-ensemble filtré, tri nommé) en CSV vers
    /// `destination`. La vue préfère [#exporter(Path, List)] pour exporter l'ordre **affiché** (tri par
    /// clic d'en-tête inclus).
    public boolean exporter(Path destination) {
        return exporter(destination, lignes);
    }

    /// Exporte les **lignes fournies** en CSV vers `destination` (P5-CA5). Permet à la vue d'exporter
    /// l'ordre **réellement affiché** (le tri par clic d'en-tête vit côté `TableView`, pas dans le
    /// ViewModel, cf. #291). Sans dossier, l'appel est ignoré ; le bilan (ou l'erreur) va dans
    /// [#messageProperty()].
    ///
    /// @param destination fichier cible choisi par l'observateur
    /// @param lignesAExporter lignes à écrire, dans l'ordre voulu
    /// @return `true` si le fichier a été écrit
    public boolean exporter(Path destination, List<LignePassage> lignesAExporter) {
        if (destination == null) {
            return false;
        }
        try {
            service.exporterCsvVers(destination, lignesAExporter);
            retour.set(RetourOperation.succes("Tableau exporté vers " + destination.getFileName() + " ("
                    + lignesAExporter.size() + " ligne(s))."));
            return true;
        } catch (RuntimeException echec) {
            retour.set(RetourOperation.erreur(echec.getMessage()));
            return false;
        }
    }

    public ObservableList<LignePassage> lignes() {
        return lignes;
    }

    /// Agrégat des carrés pour la **carte** (#152) : carrés + points (GPS, statut dominant) de l'utilisateur,
    /// vue d'ensemble non filtrée. La couche `view` le traduit en marqueurs/emprises.
    public ObservableList<CarreAgrege> carresCarte() {
        return carresCarte;
    }

    /// Socle de filtres composables (#537) sur les passages : la **barre à puces** de la vue (#537 étape 6b)
    /// y branche/retire ses prédicats (carré, statut, verdict, année), et la **carte** y pose une puce carré
    /// au clic. Le callback `publierLignes` ré-ordonne et publie à chaque changement.
    public Filtres<LignePassage> filtres() {
        return filtres;
    }

    public ObjectProperty<TriMultisite> triProperty() {
        return tri;
    }

    public ReadOnlyBooleanProperty nonVideProperty() {
        return nonVide.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty resumeProperty() {
        return resume.getReadOnlyProperty();
    }

    /// Reçoit le rapport de [PositionsEnAttente], qui ne parle que d'échecs (motif) ou de leur levée
    /// (chaîne vide) : le collaborateur reste agnostique de la sévérité, c'est ici qu'elle se décide.
    private void rapporterPosition(String motif) {
        retour.set(motif == null || motif.isBlank() ? RetourOperation.AUCUN : RetourOperation.erreur(motif));
    }

    /// Retour de la **dernière opération** avec sa sévérité, pour le bandeau de l'écran.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }
}
