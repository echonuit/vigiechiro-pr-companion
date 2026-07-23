package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.view.DemandeurDeChoix;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.Notificateur;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.view.SuiviOperation;
import fr.univ_amu.iut.importation.model.ServiceImportReference;
import fr.univ_amu.iut.importation.model.ServiceImportReference.ResultatImportReference;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.stage.Window;

/// Action IHM « importer un dossier de séquences déjà transformées » (#2258, parité avec la commande
/// `importer-transformes`), extraite de [ImportationController] (pur câblage, PMD GodClass) sur le modèle
/// de [fr.univ_amu.iut.passage.view.ActionReactivation] : elle **compose des dialogues existants** (aucun
/// nouveau FXML) et lance l'opération **hors du fil JavaFX** via [SuiviOperation].
///
/// Enchaînement : demander le dossier des transformés, choisir le point de rattachement, demander s'il faut
/// **copier ou référencer** (recommandation déduite de l'emplacement du dossier), puis créer le passage via
/// [ServiceImportReference]. Le geste **ajoute** de l'audio (aucune confirmation destructive) ; le compte
/// rendu passe par le [Notificateur].
///
/// Tous les collaborateurs de dialogue sont **injectés** (donc substituables en test) : un `showAndWait`
/// natif figerait un test TestFX headless, et un `DirectoryChooser` en dur empêcherait même de jouer le
/// geste.
public final class ActionImportTransformes {

    private final ServiceImportReference service;
    private final Workspace workspace;
    private final Supplier<Window> proprietaire;
    private final SelecteurFichier selecteur;
    private final Supplier<List<PointRattachable>> pointsDisponibles;
    private final DemandeurDeChoix<PointRattachable> demandeur;
    private final DemandeurDeChoix<ModeImport> demandeurMode;
    private final Notificateur notificateur;
    private final SuiviOperation dialogue;
    private final Runnable recharger;

    /// @param service service métier qui crée le passage référençant les transformés
    /// @param workspace espace de travail : sert à recommander « laisser en place » quand le dossier est
    ///     en dehors (ces fichiers appartiennent alors à l'utilisateur)
    /// @param proprietaire fenêtre propriétaire des dialogues, lue **au moment du geste**
    /// @param selecteur porteur de désignation du dossier des transformés (double répondant en test)
    /// @param pointsDisponibles source des points de rattachement (aplatis site → points, avec le carré)
    /// @param demandeur choix du point parmi les points disponibles
    /// @param demandeurMode choix **copier ou référencer** : un bouton par option, plus le renoncement
    /// @param notificateur compte rendu de l'issue (succès / échec)
    /// @param dialogue exécution **hors du fil JavaFX** avec barre de progression annulable
    /// @param recharger rafraîchissement de l'écran après un import réussi
    ActionImportTransformes(
            ServiceImportReference service,
            Workspace workspace,
            Supplier<Window> proprietaire,
            SelecteurFichier selecteur,
            Supplier<List<PointRattachable>> pointsDisponibles,
            DemandeurDeChoix<PointRattachable> demandeur,
            DemandeurDeChoix<ModeImport> demandeurMode,
            Notificateur notificateur,
            SuiviOperation dialogue,
            Runnable recharger) {
        this.service = Objects.requireNonNull(service, "service");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.proprietaire = Objects.requireNonNull(proprietaire, "proprietaire");
        this.selecteur = Objects.requireNonNull(selecteur, "selecteur");
        this.pointsDisponibles = Objects.requireNonNull(pointsDisponibles, "pointsDisponibles");
        this.demandeur = Objects.requireNonNull(demandeur, "demandeur");
        this.demandeurMode = Objects.requireNonNull(demandeurMode, "demandeurMode");
        this.notificateur = Objects.requireNonNull(notificateur, "notificateur");
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.recharger = Objects.requireNonNull(recharger, "recharger");
    }

    /// Ce que l'utilisateur décide pour ses fichiers déjà transformés : les **référencer** en place (aucune
    /// copie) ou les **copier** dans l'espace de travail. Chaque valeur porte le **libellé de son bouton**,
    /// pour que le choix se lise sur les boutons plutôt que derrière un « OK / Annuler » ambigu.
    public enum ModeImport {
        REFERENCER("Les référencer"),
        COPIER("Les copier");

        private final String libelle;

        ModeImport(String libelle) {
            this.libelle = libelle;
        }

        /// Libellé du bouton de ce mode.
        public String libelle() {
            return libelle;
        }
    }

    /// Un point de rattachement **présenté à l'utilisateur** : le point d'écoute et le carré de son site,
    /// pour un libellé « carré <carre> / point <code> » sans avoir à retrouver le site à l'affichage.
    ///
    /// @param point le point d'écoute cible
    /// @param numeroCarre numéro de carré du site parent (pour le libellé)
    public record PointRattachable(PointDEcoute point, String numeroCarre) {

        public PointRattachable {
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(numeroCarre, "numeroCarre");
        }

        /// Libellé affiché dans la liste de choix : « carré 640380 / point Z1 ».
        public String libelle() {
            return "carré " + numeroCarre + " / point " + point.code();
        }
    }

    /// Tous les points de l'utilisateur, aplatis site → points, avec le carré de chaque site pour le
    /// libellé. Fonction pure du service `sites`, appelée par le contrôleur pour bâtir la source de points
    /// (garde la logique d'aplatissement hors du contrôleur).
    static List<PointRattachable> pointsDe(ServiceSites serviceSites, String idUtilisateur) {
        List<PointRattachable> points = new ArrayList<>();
        for (Site site : serviceSites.listerSites(idUtilisateur)) {
            for (PointDEcoute point : serviceSites.listerPoints(site.id())) {
                points.add(new PointRattachable(point, site.numeroCarre()));
            }
        }
        return points;
    }

    /// Demande le dossier, le point, le mode copie/référence, puis crée le passage. Rien ne se passe si
    /// l'utilisateur renonce au dossier ou au point ; l'absence de point disponible est signalée.
    void importer() {
        Optional<Path> choixDossier =
                selecteur.choisirDossier("Dossier des séquences déjà transformées", Optional.empty());
        if (choixDossier.isEmpty()) {
            return;
        }
        Path dossier = choixDossier.orElseThrow();

        List<PointRattachable> points = pointsDisponibles.get();
        if (points.isEmpty()) {
            notificateur.notifier(
                    NiveauNotification.AVERTISSEMENT,
                    "Aucun point d'écoute",
                    "Créez d'abord un site et un point d'écoute pour y rattacher ces séquences.");
            return;
        }
        Optional<PointRattachable> choixPoint = demandeur.choisir(
                "À quel point rattacher ces séquences ?", "Point d'écoute", points, PointRattachable::libelle);
        if (choixPoint.isEmpty()) {
            return;
        }

        // Copier ou référencer : un bouton par décision (le libellé porte le choix), plus le renoncement
        // (« Annuler » → Optional vide), auquel cas rien n'est importé. Bien plus clair qu'un « OK / Annuler »
        // où « Annuler » signifierait « copier » : ici les boutons DISENT ce qu'ils font.
        Optional<ModeImport> mode = demandeurMode.choisir(
                ENTETE_MODE,
                questionMode(horsEspaceDeTravail(dossier)),
                List.of(ModeImport.REFERENCER, ModeImport.COPIER),
                ModeImport::libelle);
        if (mode.isEmpty()) {
            return;
        }
        lancer(dossier, choixPoint.orElseThrow().point().id(), mode.orElseThrow() == ModeImport.REFERENCER);
    }

    /// En-tête de la question copier / référencer. Exposé (avec [#questionMode]) pour que l'outil de capture
    /// rende le message **réel** au lieu de le recomposer (ADR 0025).
    public static final String ENTETE_MODE = "Copier ces fichiers, ou les référencer là où ils sont ?";

    /// L'explication de la question copier / référencer, selon que le dossier appartient à l'utilisateur
    /// (hors de l'espace de travail) ou non. Le **choix** se fait sur les boutons ([ModeImport#libelle]) ;
    /// ce texte n'énonce que la conséquence, sans jamais renvoyer à un « oui / non » qui n'existe pas.
    public static String questionMode(boolean horsEspaceDeTravail) {
        return horsEspaceDeTravail
                ? "Ce dossier est en dehors de votre dossier de travail : ces fichiers sont les vôtres.\n\n"
                        + "Les référencer les laisse où ils sont (recommandé), sans rien copier. Cette nuit ne"
                        + " sera plus écoutable si ce support n'est pas accessible (disque débranché, dossier"
                        + " réseau hors ligne), et le redeviendra dès qu'il le sera.\n\n"
                        + "Les copier en place un double dans votre dossier de travail."
                : "Les référencer les laisse où ils sont, sans copie.\n\n"
                        + "Les copier en place un double à l'emplacement que l'application attend.";
    }

    /// `true` si `dossier` est **en dehors** de l'espace de travail : ses fichiers appartiennent alors à
    /// l'utilisateur, ce qui oriente la recommandation vers « laisser en place ».
    private boolean horsEspaceDeTravail(Path dossier) {
        return !dossier.toAbsolutePath().normalize().startsWith(workspace.racine());
    }

    /// Lance la création du passage **hors du fil JavaFX** (barre de progression annulable), puis restitue
    /// l'issue sur le fil JavaFX : compte rendu de succès (et rafraîchissement) ou message d'échec.
    private void lancer(Path dossier, Long idPoint, boolean referencer) {
        dialogue.lancer(
                proprietaire.get(),
                "Import de transformés",
                (progres, jeton) -> service.importer(dossier, idPoint, null, null, referencer, progres, jeton),
                resultat -> annoncerSucces(resultat, referencer),
                () -> {},
                echec -> notificateur.notifier(
                        NiveauNotification.AVERTISSEMENT, "Import impossible", "Échec : " + echec.getMessage()));
    }

    /// Rend compte du succès et rafraîchit l'écran. Le passage est créé, l'audio est en place : aucun
    /// redémarrage n'est nécessaire.
    private void annoncerSucces(ResultatImportReference resultat, boolean referencer) {
        String sequences = resultat.nombreSequences()
                + (referencer
                        ? " séquence(s) référencées en place (aucun octet audio recopié)"
                        : " séquence(s) copiées dans l'espace de travail");
        notificateur.notifier(
                NiveauNotification.INFORMATION,
                "Import des transformés réussi",
                "Passage #" + resultat.idPassage() + " créé, " + sequences + ". Redémarrage inutile.");
        recharger.run();
    }
}
