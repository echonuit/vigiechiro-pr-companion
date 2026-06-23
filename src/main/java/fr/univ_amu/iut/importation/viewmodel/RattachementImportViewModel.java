package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Sous-ViewModel de **M-Import** — étape 3 : **rattachement** de la nuit (site / point / année /
/// n° de passage) et **aperçu du préfixe** Vigie-Chiro (R6).
///
/// Extrait de [ImportationViewModel] (#183) pour le décharger : cet objet ne porte **que** l'état du
/// rattachement, sans rien savoir de l'inspection ni de l'exécution de l'import. L'orchestrateur
/// ([ImportationViewModel]) le **compose** : il lit [#estComplet()] pour `peutImporter`, assemble la
/// demande d'import depuis [#idPointSelectionne()] / [#prefixeCourant()], et lui fournit (via
/// [#definirExempleNom]) un **exemple de nom d'origine** servant à l'aperçu — une simple valeur dérivée,
/// pas le rapport d'inspection, pour ne pas coupler ce sous-VM à l'inspection.
///
/// VM agnostique de l'IHM (règle ArchUnit `viewmodel_sans_javafx_ui`) : seuls `javafx.beans` /
/// `javafx.collections` sont importés, jamais `javafx.scene`.
public class RattachementImportViewModel {

    private final ServiceSites serviceSites;
    private final String idUtilisateur;

    private final ObservableList<Site> sites = FXCollections.observableArrayList();
    private final ObjectProperty<Site> siteSelectionne = new SimpleObjectProperty<>(this, "siteSelectionne");
    private final ObservableList<PointDEcoute> points = FXCollections.observableArrayList();
    private final ObjectProperty<PointDEcoute> pointSelectionne = new SimpleObjectProperty<>(this, "pointSelectionne");
    private final IntegerProperty annee = new SimpleIntegerProperty(this, "annee");
    private final IntegerProperty numeroPassage = new SimpleIntegerProperty(this, "numeroPassage", 1);
    private final ReadOnlyStringWrapper apercuPrefixe = new ReadOnlyStringWrapper(this, "apercuPrefixe", "");

    /// Avertissement **non bloquant** (#33, #111) : non vide quand le dossier contient des originaux déjà
    /// préfixés dont le préfixe **ne concorde pas** avec le rattachement choisi (leurs noms seront
    /// conservés). Recalculé à chaque changement de rattachement ou de dossier inspecté.
    private final ReadOnlyStringWrapper avertissementPrefixe =
            new ReadOnlyStringWrapper(this, "avertissementPrefixe", "");

    /// Noms de **tous** les originaux inspectés, **fournis par l'orchestrateur** (dérivés de l'inspection).
    /// Servent à l'aperçu (le premier nom comme gabarit) **et** à la détection de discordance de préfixe
    /// (#111) sur l'ensemble du dossier. Liste vide tant qu'aucune inspection n'a réussi.
    private List<String> nomsOriginaux = List.of();

    public RattachementImportViewModel(ServiceSites serviceSites, Horloge horloge, String idUtilisateur) {
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        Objects.requireNonNull(horloge, "horloge");

        // Valeur initiale avant d'installer les écouteurs (évite un recalcul d'aperçu prématuré).
        annee.set(horloge.aujourdhui().getYear());

        // Changer de site recharge ses points et réinitialise le point sélectionné.
        siteSelectionne.addListener((obs, ancien, nouveau) -> {
            points.setAll(nouveau == null ? List.of() : serviceSites.listerPoints(nouveau.id()));
            pointSelectionne.set(null);
            rafraichir();
        });
        pointSelectionne.addListener((obs, ancien, nouveau) -> rafraichir());
        annee.addListener((obs, ancien, nouveau) -> rafraichir());
        numeroPassage.addListener((obs, ancien, nouveau) -> rafraichir());
    }

    /// Recharge les sites de l'utilisateur courant (à l'ouverture de l'écran ou après création d'un site).
    public void chargerSites() {
        sites.setAll(serviceSites.listerSites(idUtilisateur));
    }

    /// `true` si le rattachement est complet : site + point + n° de passage valides. Condition
    /// nécessaire de [ImportationViewModel#peutImporter()].
    public boolean estComplet() {
        return siteSelectionne.get() != null && pointSelectionne.get() != null && numeroPassage.get() >= 1;
    }

    /// Identifiant du point d'écoute sélectionné, pour assembler la demande d'import. Précondition :
    /// rattachement complet ([#estComplet()] vrai).
    public Long idPointSelectionne() {
        return pointSelectionne.get().id();
    }

    /// Préfixe Vigie-Chiro courant (R6) déduit du rattachement. Précondition : rattachement complet.
    public Prefixe prefixeCourant() {
        Site site = siteSelectionne.get();
        return new Prefixe(
                site.numeroCarre(),
                annee.get(),
                numeroPassage.get(),
                pointSelectionne.get().code());
    }

    /// Fournit (orchestrateur) les noms de **tous** les originaux inspectés — dérivés de l'inspection —
    /// ou une liste vide pour réinitialiser ; recalcule l'aperçu et l'avertissement de discordance.
    public void definirOriginaux(List<String> noms) {
        this.nomsOriginaux = List.copyOf(noms);
        rafraichir();
    }

    /// Liste observable des sites de l'utilisateur (combobox Site), alimentée par [#chargerSites()].
    public ObservableList<Site> sites() {
        return sites;
    }

    /// Site auquel rattacher la nuit (sélection dans la combobox).
    public ObjectProperty<Site> siteSelectionneProperty() {
        return siteSelectionne;
    }

    /// Points du site sélectionné (recalculés à chaque changement de site).
    public ObservableList<PointDEcoute> points() {
        return points;
    }

    /// Point d'écoute auquel rattacher la nuit.
    public ObjectProperty<PointDEcoute> pointSelectionneProperty() {
        return pointSelectionne;
    }

    /// Année du passage (préremplie à l'année de l'horloge applicative).
    public IntegerProperty anneeProperty() {
        return annee;
    }

    /// Numéro de passage dans l'année pour ce point (défaut 1, éditable).
    public IntegerProperty numeroPassageProperty() {
        return numeroPassage;
    }

    /// Aperçu du nom préfixé appliqué aux fichiers (R6), recalculé dès qu'un champ change ; vide tant
    /// que le site ou le point n'est pas choisi.
    public ReadOnlyStringProperty apercuPrefixeProperty() {
        return apercuPrefixe.getReadOnlyProperty();
    }

    /// Avertissement **non bloquant** de discordance de préfixe (#111) : non vide quand des originaux déjà
    /// préfixés ne correspondent pas au rattachement choisi (leurs noms seront conservés). La vue s'y lie
    /// directement (label dédié), comme aux avertissements « mélange »/« incohérence » de l'inspection.
    public ReadOnlyStringProperty avertissementPrefixeProperty() {
        return avertissementPrefixe.getReadOnlyProperty();
    }

    /// Recalcule les valeurs dérivées du rattachement (aperçu + avertissement de préfixe) après tout
    /// changement de site / point / année / n° ou de dossier inspecté.
    private void rafraichir() {
        majApercu();
        majAvertissementPrefixe();
    }

    private void majApercu() {
        String exemple = nomsOriginaux.isEmpty() ? null : nomsOriginaux.get(0);
        apercuPrefixe.set(ApercuPrefixe.calculer(
                siteSelectionne.get(), pointSelectionne.get(), annee.get(), numeroPassage.get(), exemple));
    }

    /// Discordance de préfixe (#111) : si le rattachement est désigné (site + point) et que des originaux
    /// portent déjà un préfixe R6 **différent** de celui attendu, on avertit (sur **tout** le dossier, pas
    /// seulement le premier fichier). Les noms existants ne sont pas corrigés (R7).
    private void majAvertissementPrefixe() {
        Site site = siteSelectionne.get();
        PointDEcoute point = pointSelectionne.get();
        if (site == null || point == null || nomsOriginaux.isEmpty()) {
            avertissementPrefixe.set("");
            return;
        }
        String attendu = prefixeCourant().prefixeFichier();
        boolean discordant =
                nomsOriginaux.stream().filter(Prefixe::estNomPrefixe).anyMatch(nom -> !nom.startsWith(attendu));
        avertissementPrefixe.set(
                discordant
                        ? "⚠ Certains fichiers sont déjà préfixés mais ne correspondent pas au rattachement"
                                + " choisi (préfixe attendu : " + attendu + "). Leurs noms seront conservés."
                        : "");
    }
}
