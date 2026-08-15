package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.RechercheCarreExistant;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import fr.univ_amu.iut.sites.model.SouhaitDeclaration;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/// ViewModel de la **modale de déclaration / d'édition d'un site** (`ModaleSite.fxml`, #1431). Jumeau
/// de [PointEditViewModel] : mêmes propriétés observables, même dualité création/édition (le titre et le
/// libellé du bouton s'y adaptent), même contrat d'enregistrement.
///
/// Il remplace **deux** `Dialog<T>` bâtis à la main dans `MesSitesController` (créer) et
/// `SiteDetailController` (modifier). Ces dialogues avaient trois défauts, tous liés :
///
/// - leur geste était **intestable** (un `showAndWait` fige un test headless) : déclarer un site, qui est
///   l'entrée du produit, n'était vérifié nulle part ;
/// - leur **validation** vivait dans la vue, donc n'était pas non plus testable ;
/// - leur **capture** de documentation était une **réplique** reconstruite à la main
///   (`CaptureDialogues`), qui pouvait dériver du vrai dialogue sans que rien ne le signale.
///
/// Ici, la validation est un binding observable ([#peutEnregistrer()]), donc vérifiable **sans IHM**, et
/// la modale est une vraie vue - que la capture rend telle qu'elle est.
public class SiteEditViewModel {

    private final ServiceSites service;

    /// Correspondances Vigie-Chiro : elles disent si le site édité existe déjà côté plateforme,
    /// donc si son renommage local créerait un écart visible (#1380).
    private final LienVigieChiroDao liens;

    /// Utilisateur propriétaire des sites : nécessaire à la création (R5, unicité du carré par
    /// utilisateur) ; l'édition, elle, part du site existant.
    private final String idUtilisateur;

    private final StringProperty numeroCarre = new SimpleStringProperty(this, "numeroCarre", "");
    private final StringProperty nom = new SimpleStringProperty(this, "nom", "");
    private final ObjectProperty<Protocole> protocole =
            new SimpleObjectProperty<>(this, "protocole", Protocole.STANDARD);
    private final StringProperty commentaire = new SimpleStringProperty(this, "commentaire", "");

    private final ReadOnlyStringWrapper titre = new ReadOnlyStringWrapper(this, "titre", "");
    private final ReadOnlyStringWrapper libelleBouton = new ReadOnlyStringWrapper(this, "libelleBouton", "Créer");

    /// Portée de l'édition en cours (#1380) : ce que le geste atteint, quand il n'atteint pas tout.
    /// Absent en déclaration et sur un site que la plateforme ne connaît pas - il n'y a alors aucun
    /// écart à annoncer, et un message permanent sur le cas nominal serait du bruit.
    private final ReadOnlyObjectWrapper<RetourOperation> porteeEdition =
            new ReadOnlyObjectWrapper<>(this, "porteeEdition", RetourOperation.AUCUN);
    /// Compte rendu de la dernière tentative d'enregistrement, avec sa sévérité (#1917). Il s'appelait
    /// `messageErreur` : la sévérité vivait dans son **nom**, ce qui l'empêchait de porter autre chose
    /// qu'un échec. Or un champ mal rempli n'est pas une panne, c'est un guidage.
    private final ReadOnlyObjectWrapper<RetourOperation> retour =
            new ReadOnlyObjectWrapper<>(this, "retour", RetourOperation.AUCUN);

    /// Le carré est le **seul** champ obligatoire : six chiffres (R1). Le nom, le protocole et le
    /// commentaire sont facultatifs - un observateur qui déclare un carré à la volée ne doit pas être
    /// bloqué par de la décoration.
    private final BooleanBinding carreValide;

    /// Carré saisi mais **encore incomplet** : c'est ce qui fait rougir le champ (#790), sans rougir
    /// avant que l'utilisateur ait tapé quoi que ce soit.
    private final BooleanBinding carreInvalideEtSaisi;

    /// Site en cours d'édition ; `null` en création.
    private Site siteEnEdition;

    /// « Ce carré existe-t-il déjà ? » (#3458). **Optionnel**, car il a besoin de la plateforme : absent
    /// (injecteurs partiels, feature de connexion éteinte), la vérification n'est simplement pas offerte
    /// et la déclaration reste entière. Même montage que `ControleCarreStoc`.
    private final Optional<RechercheCarreExistant> recherche;

    /// Ce que la plateforme a répondu sur le carré saisi, avec sa gravité. Vide tant qu'on n'a rien
    /// demandé - et une **absence de réponse n'est pas une réponse** : voir
    /// [RechercheCarreExistant.Verdict.Indisponible].
    private final ReadOnlyObjectWrapper<RetourOperation> retourCarreExistant =
            new ReadOnlyObjectWrapper<>(this, "retourCarreExistant", RetourOperation.AUCUN);

    /// Le carré cherché existe sur la plateforme : il y a donc quelque chose à **récupérer** (#3806).
    /// Faux tant qu'on n'a pas demandé, faux si le carré est libre, et remis à faux dès que le numéro
    /// change - le geste ne survit pas au verdict qui l'a ouvert.
    private final ReadOnlyBooleanWrapper carreRecuperable = new ReadOnlyBooleanWrapper(this, "carreRecuperable", false);

    /// « Récupérer ce carré » (#3806). **Optionnel** pour la même raison que la recherche : il interroge
    /// la plateforme. Absent, le geste n'est pas offert et la déclaration reste entière.
    private final Optional<RapatriementCarre> rapatriement;

    public SiteEditViewModel(
            ServiceSites service,
            LienVigieChiroDao liens,
            String idUtilisateur,
            Optional<RechercheCarreExistant> recherche,
            Optional<RapatriementCarre> rapatriement) {
        this.liens = Objects.requireNonNull(liens, "liens");
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.recherche = Objects.requireNonNull(recherche, "recherche");
        this.rapatriement = Objects.requireNonNull(rapatriement, "rapatriement");
        carreValide = Bindings.createBooleanBinding(() -> numeroCarre.get().matches("\\d{6}"), numeroCarre);
        carreInvalideEtSaisi = Bindings.createBooleanBinding(
                () -> !numeroCarre.get().isEmpty() && !numeroCarre.get().matches("\\d{6}"), numeroCarre);
        // Un verdict porte sur LE numéro qu'on a cherché : dès qu'il change, il ne dit plus rien de ce
        // qui est à l'écran. Le laisser afficherait « ce carré n'existe pas encore » sous un carré que
        // personne n'a vérifié - la panne même de #3458, avec en plus la preuve visuelle du contraire.
        //
        // ⚠️ Le jumeau `PointEditViewModel` n'a pas ce besoin : son contrôle du carré STOC est
        // **automatique** et se relance à chaque frappe. Ici le geste est manuel - une requête réseau par
        // clic -, donc c'est l'effacement qui tient le rôle.
        numeroCarre.addListener((observable, avant, apres) -> {
            retourCarreExistant.set(RetourOperation.AUCUN);
            carreRecuperable.set(false);
        });
    }

    /// La vérification « ce carré existe-t-il déjà ? » est-elle **installée** (#3458) ? Faux hors de
    /// l'application complète : la modale n'affiche alors pas le geste, plutôt qu'un bouton mort.
    public boolean rechercheCarreDisponible() {
        return recherche.isPresent();
    }

    /// Ce qu'une interrogation rapporte : le verdict **et le numéro qui l'a demandé** (#3458).
    ///
    /// Le numéro voyage avec le verdict parce que la réponse revient **plus tard**, sur une modale que
    /// l'utilisateur a pu modifier entre-temps. Sans lui, rien ne permettrait de savoir que ce verdict ne
    /// juge plus ce qui est à l'écran.
    ///
    /// @param numeroInterroge le numéro de carré tel qu'il était au départ de la requête
    /// @param verdict ce que la plateforme en a dit
    public record ResultatRechercheCarre(String numeroInterroge, RechercheCarreExistant.Verdict verdict) {

        /// Aucune réponse exploitable pour `numeroInterroge` : panne technique, plutôt que refus de la
        /// plateforme (celui-là est déjà un [RechercheCarreExistant.Verdict.Indisponible] rendu par le
        /// modèle).
        public static ResultatRechercheCarre indisponible(String numeroInterroge) {
            return new ResultatRechercheCarre(numeroInterroge, new RechercheCarreExistant.Verdict.Indisponible());
        }
    }

    /// Interroge la plateforme sur le carré saisi.
    ///
    /// **Bloquant** (réseau) : à appeler hors du fil JavaFX, puis passer le résultat à
    /// [#appliquerRechercheCarre]. Même découpage que `PointEditViewModel#controlerCarre`.
    public ResultatRechercheCarre chercherCarreExistant() {
        String demande = numeroCarre.get();
        if (recherche.isEmpty() || !carreValide.get()) {
            return ResultatRechercheCarre.indisponible(demande);
        }
        return new ResultatRechercheCarre(demande, recherche.get().chercher(demande));
    }

    /// Applique un résultat de [#chercherCarreExistant] aux propriétés observables, **sur le fil JavaFX**.
    ///
    /// Un résultat qui porte sur un **autre numéro** que celui affiché est **écarté** : l'appel a duré, et
    /// la saisie a changé pendant ce temps. L'afficher quand même mettrait un avertissement sous un carré
    /// qu'il ne juge pas - le jumeau, pris par l'autre bout, du verdict qu'on efface quand le numéro
    /// change.
    public void appliquerRechercheCarre(ResultatRechercheCarre resultat) {
        if (!resultat.numeroInterroge().equals(numeroCarre.get())) {
            return;
        }
        retourCarreExistant.set(new RetourOperation(
                resultat.verdict().message(), resultat.verdict().severite()));
        // Le geste suit le verdict : on ne propose de récupérer que ce qui est là-bas, et seulement si le
        // rapatriement est installé (sinon le bouton serait mort).
        carreRecuperable.set(
                rapatriement.isPresent() && resultat.verdict() instanceof RechercheCarreExistant.Verdict.DejaDeclare);
    }

    /// Y a-t-il un carré à récupérer, c'est-à-dire un verdict « il existe déjà » encore valable (#3806) ?
    public ReadOnlyBooleanProperty carreRecuperable() {
        return carreRecuperable.getReadOnlyProperty();
    }

    /// Récupère le carré saisi depuis la plateforme, avec le protocole choisi dans la modale.
    ///
    /// **Bloquant** (réseau) : à appeler hors du fil JavaFX. Rend un [RapatriementCarre.Resultat] que la
    /// vue rend, et dont elle tire le site à ouvrir quand il a été rapatrié.
    public RapatriementCarre.Resultat rapatrierCarre() {
        if (rapatriement.isEmpty() || !carreValide.get()) {
            return new RapatriementCarre.Resultat.Indisponible();
        }
        return rapatriement
                .get()
                .rapatrier(new SouhaitDeclaration(numeroCarre.get(), protocole.get(), nom.get(), commentaire.get()));
    }

    /// Affiche le compte rendu du rapatriement, **sur le fil JavaFX**.
    public void appliquerRapatriement(RapatriementCarre.Resultat resultat) {
        retourCarreExistant.set(new RetourOperation(resultat.message(), resultat.severite()));
        carreRecuperable.set(false);
    }

    /// Ce que la plateforme a dit du carré saisi, avec sa gravité.
    public ReadOnlyObjectProperty<RetourOperation> retourCarreExistantProperty() {
        return retourCarreExistant.getReadOnlyProperty();
    }

    /// Configure la modale en **mode déclaration** d'un nouveau site.
    public void preparerCreation() {
        siteEnEdition = null;
        // Le verdict du carré n'est pas effacé ici : vider le numéro juste en dessous s'en charge, par le
        // même chemin que toute autre correction de saisie. Un second effacement, écrit à la main, aurait
        // dit la même chose une seconde fois - et PIT l'a signalé en le supprimant sans faire rougir
        // personne.
        numeroCarre.set("");
        nom.set("");
        protocole.set(Protocole.STANDARD);
        commentaire.set("");
        retour.set(RetourOperation.AUCUN);
        titre.set("Nouveau site de suivi");
        libelleBouton.set("Créer");
        porteeEdition.set(RetourOperation.AUCUN);
    }

    /// Configure la modale en **mode édition** : champs pré-remplis depuis le site existant.
    public void preparerEdition(Site site) {
        siteEnEdition = Objects.requireNonNull(site, "site");
        numeroCarre.set(site.numeroCarre());
        nom.set(ouVide(site.nomConvivial()));
        protocole.set(site.protocole());
        commentaire.set(ouVide(site.commentaire()));
        retour.set(RetourOperation.AUCUN);
        titre.set("Modifier le site · Carré " + site.numeroCarre());
        libelleBouton.set("Enregistrer");
        porteeEdition.set(porteeDe(site));
    }

    /// Tente d'enregistrer le site (déclaration ou édition).
    ///
    /// @return `true` si l'enregistrement a réussi (la vue peut fermer la modale) ; `false` si une règle
    ///     métier a refusé - le motif est alors dans [#retourProperty()], **dans la modale**, à
    ///     côté du champ fautif, et non dans une alerte qui s'ouvre après coup par-dessus
    public boolean enregistrer() {
        if (!carreValide.get()) {
            return false;
        }
        try {
            if (siteEnEdition == null) {
                service.creerSite(
                        numeroCarre.get(), vide(nom.get()), protocole.get(), vide(commentaire.get()), idUtilisateur);
            } else {
                service.modifierSite(
                        siteEnEdition.id(),
                        numeroCarre.get(),
                        vide(nom.get()),
                        protocole.get(),
                        vide(commentaire.get()));
            }
            retour.set(RetourOperation.AUCUN);
            return true;
        } catch (RegleMetierException | IllegalArgumentException refus) {
            retour.set(RetourOperation.erreur(refus));
            return false;
        }
    }

    public StringProperty numeroCarreProperty() {
        return numeroCarre;
    }

    public StringProperty nomProperty() {
        return nom;
    }

    public ObjectProperty<Protocole> protocoleProperty() {
        return protocole;
    }

    public StringProperty commentaireProperty() {
        return commentaire;
    }

    public ReadOnlyStringProperty titreProperty() {
        return titre.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty libelleBoutonProperty() {
        return libelleBouton.getReadOnlyProperty();
    }

    /// Compte rendu de la dernière tentative d'enregistrement, rendu par le bandeau partagé (ADR 0023).
    /// [RetourOperation#AUCUN] en nominal.
    public ReadOnlyObjectProperty<RetourOperation> retourProperty() {
        return retour.getReadOnlyProperty();
    }

    /// Efface le retour (l'utilisateur a lu le bandeau et le ferme).
    public void effacerRetour() {
        retour.set(RetourOperation.AUCUN);
    }

    /// Le bouton d'enregistrement n'est ouvert que si le carré est valide (#790) : on **empêche** au lieu
    /// d'avertir après coup.
    public BooleanBinding peutEnregistrer() {
        return carreValide;
    }

    /// Le numéro de carré a ses **six chiffres** : il y a donc quelque chose à chercher sur la
    /// plateforme (#3458).
    ///
    /// ⚠️ Exposé à part de [#peutEnregistrer()], qui vaut la même chose **aujourd'hui**. Les deux
    /// questions sont distinctes : « ce carré est-il cherchable » et « ce formulaire est-il
    /// enregistrable ». Les confondre ferait griser la recherche le jour où l'enregistrement gagnera une
    /// condition qui ne la concerne pas.
    public BooleanBinding carreValide() {
        return carreValide;
    }

    /// Le champ « carré » doit rougir : saisi, mais pas encore aux six chiffres.
    public BooleanBinding carreInvalideEtSaisi() {
        return carreInvalideEtSaisi;
    }

    /// Un champ facultatif laissé vide vaut `null` en base, pas chaîne vide.
    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur;
    }

    private static String ouVide(String valeur) {
        return valeur == null ? "" : valeur;
    }
    /// Ce que l'édition de `site` atteint (#1380).
    ///
    /// Sur un site que Vigie-Chiro connaît déjà - enregistré **ou** verrouillé - le renommage local ne
    /// remonte rien : le portail continuera d'afficher l'ancien nom. L'édition reste utile (un nom
    /// d'usage aide à s'y retrouver), c'est le **silence** qui créait une incohérence perçue.
    ///
    /// Les deux états sont traités ensemble à dessein : dès qu'une correspondance existe, l'écart entre
    /// les deux noms est visible. Ne le dire que sur les verrouillés laisserait la moitié des cas muets.
    private RetourOperation porteeDe(Site site) {
        StatutPlateforme statut = StatutPlateforme.duSite(site.id(), liens);
        if (statut == StatutPlateforme.ABSENT) {
            return RetourOperation.AUCUN;
        }
        return RetourOperation.info("Modification locale : le nom ne sera pas transmis à Vigie-Chiro.");
    }

    /// Portée de l'édition en cours, à afficher à côté des champs qu'elle concerne (#1380).
    public ReadOnlyObjectProperty<RetourOperation> porteeEditionProperty() {
        return porteeEdition.getReadOnlyProperty();
    }
}
