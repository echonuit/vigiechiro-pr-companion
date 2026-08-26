package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.Prefixe;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.passage.model.Campagne;
import fr.univ_amu.iut.passage.model.PropositionCampagne;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/// Sous-ViewModel de **M-Import**, étape 3 : **rattachement** de la nuit (site / point / année /
/// n° de passage) et **aperçu du préfixe** Vigie-Chiro (R6).
///
/// Extrait de [ImportationViewModel] (#183) pour le décharger : cet objet ne porte **que** l'état du
/// rattachement, sans rien savoir de l'inspection ni de l'exécution de l'import. L'orchestrateur
/// ([ImportationViewModel]) le **compose** : il lit [#estComplet()] pour `peutImporter`, assemble la
/// demande d'import depuis [#idPointSelectionne()] / [#prefixeCourant()], et lui fournit (via
/// [#definirExempleNom]) un **exemple de nom d'origine** servant à l'aperçu : une simple valeur dérivée,
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

    /// Nature **opportuniste** (#2525) : la nuit importée est réalisée sur le carré d'un tiers, donc
    /// exemptée de R3/R4. Cochée par l'utilisateur, elle s'applique à tous les passages créés par cet
    /// import (une même demande cible un seul carré). N'influe ni sur le préfixe ni sur la numérotation.
    private final BooleanProperty opportuniste = new SimpleBooleanProperty(this, "opportuniste", false);
    /// Campagne (#2631), **optionnelle** : la fonctionnalité est désactivable. Absent le port, la liste
    /// reste vide, rien n'est proposé ni rattaché, et l'assistant se comporte comme avant.
    private final Optional<PropositionCampagne> campagnes;

    private final ObservableList<Campagne> campagnesProposees = FXCollections.observableArrayList();
    private final ObjectProperty<Campagne> campagneSelectionnee =
            new SimpleObjectProperty<>(this, "campagneSelectionnee");

    /// L'utilisateur a-t-il **choisi lui-même** une campagne sur cet écran ?
    ///
    /// Dès qu'il l'a fait, changer de point ne redéfinit plus la proposition : deviner est un service,
    /// écraser une décision est une faute. Le drapeau distingue les deux, ce que la seule valeur
    /// sélectionnée ne permet pas - « aucune campagne » choisi à la main et « rien proposé » ont la
    /// même valeur, `null`, et des sens opposés.
    private boolean campagneChoisieALaMain;

    private final ReadOnlyStringWrapper apercuPrefixe = new ReadOnlyStringWrapper(this, "apercuPrefixe", "");

    /// Discordance de préfixe (#111), **bloquante depuis #1493** : non vide quand le dossier contient des
    /// originaux déjà préfixés dont le préfixe **ne concorde pas** avec le rattachement choisi.
    ///
    /// Elle n'était qu'un avertissement - « leurs noms seront conservés » - et c'est précisément ce qui
    /// posait problème : des fichiers estampillés d'un carré, rattachés à un autre, **partiraient tels
    /// quels au dépôt**. Un avertissement qu'on écarte d'un clic ne protège pas d'une donnée fausse.
    /// Elle entre donc dans `peutImporter`, et son niveau passe d'avertissement à **erreur**.
    ///
    /// Recalculée à chaque changement de rattachement ou de dossier inspecté.
    private final ReadOnlyObjectWrapper<RetourOperation> avertissementPrefixe =
            new ReadOnlyObjectWrapper<>(this, "avertissementPrefixe", RetourOperation.AUCUN);

    /// Noms de **tous** les originaux inspectés, **fournis par l'orchestrateur** (dérivés de l'inspection).
    /// Servent à l'aperçu (le premier nom comme gabarit) **et** à la détection de discordance de préfixe
    /// (#111) sur l'ensemble du dossier. Liste vide tant qu'aucune inspection n'a réussi.
    private List<String> nomsOriginaux = List.of();

    /// Ceux des originaux que l'import RETIENDRA. Distincts des précédents dès qu'un dossier mélange
    /// deux enregistreurs : l'import ne garde que la série du journal (#1492).
    private List<String> nomsRetenus = List.of();

    public RattachementImportViewModel(
            ServiceSites serviceSites, Horloge horloge, String idUtilisateur, Optional<PropositionCampagne> campagnes) {
        this.serviceSites = Objects.requireNonNull(serviceSites, "serviceSites");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.campagnes = Objects.requireNonNull(campagnes, "campagnes");
        Objects.requireNonNull(horloge, "horloge");

        // Valeur initiale avant d'installer les écouteurs (évite un recalcul d'aperçu prématuré).
        annee.set(horloge.aujourdhui().getYear());

        // Changer de site recharge ses points et réinitialise le point sélectionné.
        siteSelectionne.addListener((obs, ancien, nouveau) -> {
            points.setAll(nouveau == null ? List.of() : serviceSites.listerPoints(nouveau.id()));
            pointSelectionne.set(null);
            rafraichir();
        });
        pointSelectionne.addListener((obs, ancien, nouveau) -> {
            proposerCampagnePour(nouveau);
            rafraichir();
        });
        annee.addListener((obs, ancien, nouveau) -> rafraichir());
        numeroPassage.addListener((obs, ancien, nouveau) -> rafraichir());
    }

    /// La fonctionnalité `campagne` est-elle active (donc la liste déroulante affichée) ?
    public boolean campagneActivee() {
        return campagnes.isPresent();
    }

    /// Charge les campagnes proposables. Appelée à l'ouverture de l'écran, comme [#chargerSites].
    public void chargerCampagnes() {
        campagnes.ifPresent(port -> campagnesProposees.setAll(port.campagnes()));
    }

    /// Enregistre que la campagne affichée vient d'un **choix de l'utilisateur**, et non d'une
    /// proposition. Appelée par la surface au changement de sélection.
    ///
    /// C'est la surface qui sait faire la différence : le ViewModel voit passer les deux par la même
    /// propriété. Sans ce signal, la proposition suivante écraserait le choix.
    public void marquerCampagneChoisie() {
        campagneChoisieALaMain = true;
    }

    /// Propose la campagne du dernier passage de `point`, **sauf** si l'utilisateur a déjà choisi.
    ///
    /// Un point sans passage rattaché ne propose rien, et cela vaut décision : la proposition passe
    /// alors à « aucune campagne » plutôt que de laisser en place celle du point précédent, qui n'a
    /// aucune raison de s'appliquer ici.
    private void proposerCampagnePour(PointDEcoute point) {
        if (campagneChoisieALaMain) {
            return;
        }
        campagnes.ifPresent(port -> campagneSelectionnee.set(
                point == null ? null : port.proposerPour(point.id()).orElse(null)));
    }

    /// Campagnes proposées à la liste déroulante (vide si la fonctionnalité est coupée).
    public ObservableList<Campagne> campagnesProposees() {
        return campagnesProposees;
    }

    /// Campagne retenue pour les nuits de cet import (`null` = aucune), liée en bidirectionnel.
    public ObjectProperty<Campagne> campagneSelectionneeProperty() {
        return campagneSelectionnee;
    }

    /// Identifiant de la campagne retenue, `null` si aucune ou fonctionnalité coupée : ce que le
    /// rattachement post-import applique aux nuits créées.
    public Long idCampagneRetenue() {
        Campagne retenue = campagneSelectionnee.get();
        return retenue == null ? null : retenue.id();
    }

    /// Recharge les sites de l'utilisateur courant (à l'ouverture de l'écran ou après création d'un site).
    public void chargerSites() {
        sites.setAll(serviceSites.listerSites(idUtilisateur));
    }

    /// Pré-sélectionne, dans la combobox site, le site d'identifiant `idSite` s'il figure dans la liste
    /// chargée (raccourci « Importer une nuit » depuis la fiche d'un site). Sans effet si `idSite` est
    /// nul ou ne correspond à aucun site de l'utilisateur. Le changement de site recharge ses points
    /// (listener du constructeur), comme une sélection manuelle.
    public void preselectionnerSite(Long idSite) {
        if (idSite == null) {
            return;
        }
        sites.stream().filter(site -> idSite.equals(site.id())).findFirst().ifPresent(siteSelectionne::set);
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

    /// Fournit (orchestrateur) les noms de **tous** les originaux inspectés, dérivés de l'inspection :
    /// ou une liste vide pour réinitialiser ; recalcule l'aperçu et l'avertissement de discordance.
    public void definirOriginaux(List<String> noms) {
        definirOriginaux(noms, noms);
    }

    /// Les mêmes, en distinguant ceux que l'import **retiendra** (#4021).
    ///
    /// Pourquoi deux listes. L'aperçu montre ce que deviendront les fichiers ; l'avertissement de
    /// discordance (#111) porte sur l'ensemble du dossier. Sur une carte qui mélange deux
    /// enregistreurs, l'aperçu prenait `nomsOriginaux.get(0)` - le premier dans l'ordre des noms - et
    /// désignait donc, une fois sur deux, un fichier que l'import allait **écarter**. Mesuré sur
    /// `sd-melange` : journal de série 1925492, aperçu annonçant `…PaRecPR1648011_…`, et trois
    /// fichiers de cette série ignorés au compte rendu.
    ///
    /// Sur le seul écran où l'utilisateur a besoin de savoir ce qui sera pris et ce qui sera laissé,
    /// l'aperçu désignait précisément ce qui serait laissé - en ayant l'air juste, puisque le carré,
    /// le passage et le point y étaient corrects.
    public void definirOriginaux(List<String> noms, List<String> retenus) {
        this.nomsOriginaux = List.copyOf(noms);
        this.nomsRetenus = List.copyOf(retenus);
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

    /// Nature **opportuniste** de la participation (#2525) : la nuit importée est sur le carré d'un
    /// tiers, donc exemptée de R3/R4. La vue s'y lie par une case à cocher.
    public BooleanProperty opportunisteProperty() {
        return opportuniste;
    }

    /// Raccourci booléen de [#opportunisteProperty()], pour l'orchestrateur qui marque les passages créés.
    public boolean estOpportuniste() {
        return opportuniste.get();
    }

    /// Aperçu du nom préfixé appliqué aux fichiers (R6), recalculé dès qu'un champ change ; vide tant
    /// que le site ou le point n'est pas choisi.
    public ReadOnlyStringProperty apercuPrefixeProperty() {
        return apercuPrefixe.getReadOnlyProperty();
    }

    /// Avertissement **non bloquant** de discordance de préfixe (#111) : non vide quand des originaux déjà
    /// préfixés ne correspondent pas au rattachement choisi (leurs noms seront conservés). La vue s'y lie
    /// directement (label dédié), comme aux avertissements « mélange »/« incohérence » de l'inspection.
    public ReadOnlyObjectProperty<RetourOperation> avertissementPrefixeProperty() {
        return avertissementPrefixe.getReadOnlyProperty();
    }

    /// Recalcule les valeurs dérivées du rattachement (aperçu + avertissement de préfixe) après tout
    /// changement de site / point / année / n° ou de dossier inspecté.
    private void rafraichir() {
        majApercu();
        majAvertissementPrefixe();
    }

    private void majApercu() {
        // Un RETENU, jamais un écarté. Et si rien n'est retenu, aucun exemple : promettre le nom
        // d'un fichier qu'on n'écrira pas est précisément le défaut de #4021, et le faire quand on
        // n'écrira rien du tout serait le pire des deux.
        String exemple = nomsRetenus.isEmpty() ? null : nomsRetenus.get(0);
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
            avertissementPrefixe.set(RetourOperation.AUCUN);
            return;
        }
        String attendu = prefixeCourant().prefixeFichier();
        boolean discordant =
                nomsOriginaux.stream().filter(Prefixe::estNomPrefixe).anyMatch(nom -> !nom.startsWith(attendu));
        avertissementPrefixe.set(
                discordant
                        ? RetourOperation.erreur("Certains fichiers sont déjà préfixés pour un autre"
                                + " rattachement (préfixe attendu ici : " + attendu + "). L'import est"
                                + " bloqué : leurs noms partiraient tels quels au dépôt, sous le nom d'un"
                                + " autre carré. Corrigez le rattachement pour qu'il corresponde à ces"
                                + " fichiers, ou repartez des originaux non préfixés.")
                        : RetourOperation.AUCUN);
    }
}
