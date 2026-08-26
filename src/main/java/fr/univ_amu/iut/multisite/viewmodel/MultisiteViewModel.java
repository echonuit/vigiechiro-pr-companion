package fr.univ_amu.iut.multisite.viewmodel;

import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.Filtres;
import fr.univ_amu.iut.commun.viewmodel.ResteDeRestauration;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.multisite.model.CarreAgrege;
import fr.univ_amu.iut.multisite.model.LignePassage;
import fr.univ_amu.iut.multisite.model.ServiceMultisite;
import fr.univ_amu.iut.multisite.model.TriMultisite;
import fr.univ_amu.iut.sites.model.ServiceCommunes;
import fr.univ_amu.iut.sites.model.ServiceSites;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
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

    /// Communes des points (#2791) : rattrapées après l'enregistrement de positions déplacées.
    private final ServiceCommunes communes;
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

    /// Compte rendu chiffré du dernier relevé groupé (#2757), ou `null` s'il n'y en a pas. Distinct de
    /// [#retour] : un retour est **borné** (ADR 0031), un compte rendu grandit avec ce qu'il a à nommer.
    private final ReadOnlyObjectWrapper<CompteRenduChiffre> compteRenduReleve =
            new ReadOnlyObjectWrapper<>(this, "compteRenduReleve", null);

    /// Socle de filtres composables (#537) : recompose la conjonction sur [#passagesFiltres] puis
    /// publie via [#publierLignes()]. Déclaré après ses dépendances (la liste filtrée).
    private final Filtres<LignePassage> filtres = new Filtres<>(passagesFiltres, this::publierLignes);

    /// Relevé groupé de l'état des analyses (#1338), **optionnel** : présent seulement quand l'observateur
    /// est connecté à VigieChiro (même liaison que l'import). Absent, l'action de relève ne s'offre pas.
    private final Optional<SuiviTraitement> suivi;

    public MultisiteViewModel(
            ServiceMultisite service,
            ServiceSites serviceSites,
            ServiceCommunes communes,
            Optional<SuiviTraitement> suivi,
            String idUtilisateur) {
        this.service = Objects.requireNonNull(service, "service");
        this.communes = Objects.requireNonNull(communes, "communes");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.suivi = Objects.requireNonNull(suivi, "suivi");
        this.positionsEnAttente = new PositionsEnAttente(serviceSites, this::rafraichirCarte, this::rapporterPosition);
        // Le tri nommé ne re-filtre pas : il ré-ordonne la liste publiée. Les filtres sont posés sur
        // [#filtres] par la barre à puces de la vue (#537 étape 6b).
        tri.addListener((obs, ancien, nouveau) -> publierLignes());
    }

    /// `true` si le **relevé groupé des analyses** (#1338) a un sens ici, c'est-à-dire si le module qui
    /// le fournit est **composé** dans cette application. La vue n'offre l'action que dans ce cas
    /// (sinon, il n'y a rien à interroger).
    ///
    /// Ce n'est **pas** un état de session : la valeur est fixée à la construction de l'injecteur et
    /// ne bouge plus. Le mot « connecté » figurait ici et se lisait comme le contraire (#3545).
    public boolean releveAnalysesDisponible() {
        return suivi.isPresent();
    }

    /// Identifiants des passages **déposés** actuellement chargés (source non filtrée) : ce sont les seules
    /// nuits dont l'analyse serveur existe. À lire **sur le fil JavaFX** (la liste observable), pour passer
    /// l'instantané à [#releverAnalyses(List, Consumer, JetonAnnulation)] qui, lui, part en tâche de fond.
    public List<Long> nuitsDeposees() {
        return tousLesPassages.stream()
                // Une nuit récupérée a, elle aussi, une analyse serveur à relever (#2581) : c'est
                // même souvent la seule chose qu'on ait d'elle en attendant son audio.
                .filter(ligne -> ligne.statut().estSurLaPlateforme())
                .map(LignePassage::idPassage)
                .toList();
    }

    /// Relève l'état des analyses des `nuitsDeposees` fournies, **à la demande** et **hors du fil JavaFX**
    /// (#1338). Aucun sondage automatique : c'est un geste explicite. Best-effort, nuit par nuit (une qui
    /// échoue n'écrase pas son dernier état connu).
    ///
    /// Reçoit la liste en paramètre (capturée sur le fil JavaFX par l'appelant, cf. [#nuitsDeposees()])
    /// plutôt que de lire la liste observable depuis le fil de fond.
    ///
    /// **Vide** si la liste l'est : il n'y a alors rien à ventiler, donc pas de compte rendu à rendre. Ce
    /// n'est pas un échec mais un **guidage**, et c'est la surface qui le formule - comme elle formule
    /// déjà celui de l'interruption. Précondition : [#releveAnalysesDisponible()] vrai (l'appelant garde
    /// le bouton).
    public Optional<CompteRenduChiffre> releverAnalyses(
            List<Long> nuitsDeposees, Consumer<Progression> progres, JetonAnnulation jeton) {
        Objects.requireNonNull(nuitsDeposees, "nuitsDeposees");
        SuiviTraitement moteur = suivi.orElseThrow(
                () -> new IllegalStateException("Relevé des analyses indisponible : connectez-vous à Vigie-Chiro."));
        if (nuitsDeposees.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(CompteRenduChiffreReleve.de(moteur.releverTout(nuitsDeposees, progres, jeton), List.of()));
    }

    /// Résultat d'un relevé groupé : le compte rendu à afficher **et** les données rechargées, pour que la
    /// vue applique les deux en une fois (#1338). `compteRendu` est vide quand il n'y avait rien à relever.
    public record ResultatReleve(Optional<CompteRenduChiffre> compteRendu, DonneesMultisite donnees) {}

    /// Relève l'état des analyses **puis relit** l'écran, le tout **hors du fil JavaFX** (#1338) : le
    /// nouvel état du cache doit se voir dans la colonne « Analyse » dès le retour, sans imbriquer une
    /// seconde occupation ni laisser le compte rendu se faire effacer par un rechargement concurrent.
    public ResultatReleve releverPuisCharger(
            List<Long> nuitsDeposees, Consumer<Progression> progres, JetonAnnulation jeton) {
        Optional<CompteRenduChiffre> compteRendu = releverAnalyses(nuitsDeposees, progres, jeton);
        return new ResultatReleve(compteRendu, charger());
    }

    /// Applique le résultat d'un relevé groupé **sur le fil JavaFX** : recompose le tableau (badges
    /// « Analyse » à jour), puis publie le compte rendu. L'ordre importe : [#appliquer] efface le message
    /// via `publierLignes`, donc le compte rendu est posé **après**.
    public void appliquerReleve(ResultatReleve resultat) {
        appliquer(resultat.donnees());
        resultat.compteRendu().ifPresent(compteRenduReleve::set);
    }

    /// Le compte rendu chiffré du dernier relevé, ou `null`. La vue y branche son panneau.
    public ReadOnlyObjectProperty<CompteRenduChiffre> compteRenduReleveProperty() {
        return compteRenduReleve.getReadOnlyProperty();
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
    /// Passages **filtrés** (conjonction des filtres actifs), tels que le tableau les montre. Exposé
    /// pour la puce « Lieu » (#2968), qui liste les lieux **présents dans ce sous-ensemble** et non dans
    /// toute la saison : une liste qui proposerait des communes déjà écartées par un autre filtre ferait
    /// cliquer dans le vide.
    public ObservableList<LignePassage> passagesFiltres() {
        return passagesFiltres;
    }

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

    /// Applique des données rechargées à la suite d'une mutation venue d'**ailleurs** (#3599) : une
    /// synchronisation, un import, une restauration. Le tableau et la carte se mettent à jour, et le
    /// **compte rendu du relevé survit**.
    ///
    /// [#publierLignes] efface le compte rendu à chaque reprojection, ce qui est juste quand l'utilisateur a
    /// demandé le rechargement (#2757) ; ici il n'a rien demandé, et ce compte rendu a été payé par une
    /// attente réseau. Un compte rendu rapporte un fait passé
    /// ([ADR 0031](../../../../../../../dev-docs/decisions/0031-un-retour-n-est-pas-un-compte-rendu.md)),
    /// dont « 9 / 12 relevées » porte son propre dénominateur. Il est donc **repris par-dessus** la
    /// reprojection : un drapeau dans le rappel du socle rendrait celui-ci dépendant d'un état invisible.
    public void appliquerDepuisLaDonnee(DonneesMultisite donnees) {
        CompteRenduChiffre aConserver = compteRenduReleve.get();
        appliquer(donnees);
        compteRenduReleve.set(aConserver);
    }

    /// Publie un état **neutre** dans le message de l'écran : ni un succès, ni une erreur. Sert au
    /// renoncement (#2636), où rien n'a raté et où l'utilisateur a simplement arrêté.
    public void signalerInfo(String message) {
        retour.set(RetourOperation.info(message));
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
        // Le compte rendu s'efface avec le retour : un changement de filtre ou un rechargement le rendrait
        // périmé, et un compte rendu périmé se lit comme un compte rendu vrai.
        compteRenduReleve.set(null);
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

    /// Comble les communes des points en attente (#2791) - typiquement après l'enregistrement de
    /// positions déplacées, le déplacement ayant invalidé la commune mémorisée. **Bloquant** (réseau) :
    /// à appeler hors du fil JavaFX. Best-effort : un raté laisse simplement les points en attente.
    public void rattraperCommunes() {
        communes.rattraper();
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
            retour.set(RetourOperation.erreur(echec));
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
    /// Signale qu'une **vue mémorisée** vient d'être rejouée **amputée** de valeurs devenues
    /// introuvables (#3056) : elle filtre moins large qu'à son enregistrement, et l'utilisateur ne
    /// peut pas s'en apercevoir autrement.
    public void signalerVueAmputee(String nomVue, ResteDeRestauration reste) {
        retour.set(RetourOperation.vueAmputee(nomVue, reste));
    }

    /// Les filtres de la **mémoire de session** (#484, étendue en #3098) que la réouverture de l'écran
    /// n'a pas su remettre en place (#3093). Les données ont changé depuis la dernière visite : c'est
    /// le chemin le plus discret des trois, puisque personne n'a rien demandé.
    public void signalerFiltresDeSessionAmputes(ResteDeRestauration reste) {
        retour.set(RetourOperation.filtresDeSessionAmputes(reste));
    }

    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }
}
