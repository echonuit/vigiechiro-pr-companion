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

    /// Le mode a-t-il déjà été choisi ? Une modale ne se prépare qu'**une fois** (#3801).
    private boolean prepare;

    /// Le versant « ce carré existe-t-il là-bas ? » (#3458, #3806), **extrait** en #3801 : il ne
    /// partage aucun état avec la saisie, et le portail qualité a fini par le dire.
    private final CarreExistantViewModel carre;

    /// La modale sert à **déclarer** (et non à modifier) : la distinction décide de ce qu'un verdict
    /// « ce carré existe déjà » entraîne. En déclaration il ferme l'enregistrement ; en édition il ne
    /// veut rien dire, puisque le site édité est par construction déjà déclaré.
    private final ReadOnlyBooleanWrapper enCreation = new ReadOnlyBooleanWrapper(this, "enCreation", true);

    /// Le formulaire est enregistrable : carré valide, et pas de carré à récupérer à la place (#3806).
    private final BooleanBinding peutEnregistrer;

    public SiteEditViewModel(
            ServiceSites service,
            LienVigieChiroDao liens,
            String idUtilisateur,
            Optional<RechercheCarreExistant> recherche,
            Optional<RapatriementCarre> rapatriement) {
        this.liens = Objects.requireNonNull(liens, "liens");
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        this.carre = new CarreExistantViewModel(recherche, rapatriement);
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
        // Composition d'observables plutôt qu'un calcul : chaque dépendance est déclarée par
        // construction, là où un `createBooleanBinding` obligerait à les énumérer sans que rien ne
        // vérifie l'énumération (ADR 3547).
        peutEnregistrer = carreValide.and(enCreation.and(carre.recuperable()).not());
        numeroCarre.addListener((observable, avant, apres) -> carre.oublier());
    }

    /// Le versant « ce carré existe-t-il là-bas ? » de cette modale (#3801).
    ///
    /// Exposé plutôt que délégué : re-publier ici six gestes qui n'appartiennent pas à la saisie
    /// recréerait la classe que l'extraction vient de défaire.
    public CarreExistantViewModel carre() {
        return carre;
    }

    /// Demande à la plateforme si le carré **saisi** existe déjà.
    ///
    /// **Bloquant** (réseau) : à appeler hors du fil JavaFX, puis passer le résultat à
    /// `carre().appliquer(...)`.
    ///
    /// Ce point d'entrée reste ici, et n'est pas parti avec le reste du concern : c'est la **saisie**
    /// qui décide s'il y a lieu de demander. Un carré incomplet ne fait partir aucune requête - le
    /// bouton est grisé, mais le ViewModel s'appelle aussi directement.
    public CarreExistantViewModel.ResultatRechercheCarre chercherCarreExistant() {
        String demande = numeroCarre.get();
        if (!carreValide.get()) {
            return CarreExistantViewModel.ResultatRechercheCarre.indisponible(demande);
        }
        return carre.chercher(demande);
    }

    /// Récupère le carré saisi depuis la plateforme, avec le protocole choisi dans la modale.
    ///
    /// **Bloquant** (réseau) : à appeler hors du fil JavaFX. Même raison qu'au-dessus pour la garde de
    /// validité, et c'est ici que vit le souhait de déclaration qu'on emporte.
    public RapatriementCarre.Resultat rapatrierCarre() {
        if (!carreValide.get()) {
            return new RapatriementCarre.Resultat.Indisponible();
        }
        return carre.rapatrier(
                new SouhaitDeclaration(numeroCarre.get(), protocole.get(), nom.get(), commentaire.get()));
    }

    /// Configure la modale en **mode déclaration** d'un nouveau site.
    ///
    /// ## Pourquoi il n'y a presque rien à faire ici
    ///
    /// **L'état construit EST l'état de déclaration** : le numéro, le nom et le commentaire naissent
    /// vides, le protocole à `STANDARD`, le bouton libellé « Créer », `enCreation` à vrai, les deux
    /// comptes rendus à `AUCUN`. Seul le titre en diffère.
    ///
    /// Ces affectations existaient, et **neuf mutants y survivaient** (#3801) : les supprimer une à une
    /// ne faisait rougir personne, parce qu'elles réécrivaient des valeurs déjà en place. Elles
    /// n'avaient de sens que si la même instance servait deux fois - ce que la production ne fait pas.
    ///
    /// Ce que ces lignes garantissaient est désormais tenu ailleurs :
    /// `un_view_model_neuf_est_deja_en_declaration` lit les valeurs de départ, si bien qu'un défaut ne
    /// peut plus se cacher derrière une réinitialisation redondante.
    public void preparerCreation() {
        exigerPremierePreparation();
        titre.set("Nouveau site de suivi");
    }

    /// Configure la modale en **mode édition** : champs pré-remplis depuis le site existant.
    public void preparerEdition(Site site) {
        exigerPremierePreparation();
        siteEnEdition = Objects.requireNonNull(site, "site");
        enCreation.set(false);
        numeroCarre.set(site.numeroCarre());
        nom.set(ouVide(site.nomConvivial()));
        protocole.set(site.protocole());
        commentaire.set(ouVide(site.commentaire()));
        titre.set("Modifier le site · Carré " + site.numeroCarre());
        libelleBouton.set("Enregistrer");
        porteeEdition.set(porteeDe(site));
    }

    /// Un ViewModel de modale ne sert **qu'une fois** (#3801).
    ///
    /// `NavigationSites` recharge le FXML à chaque ouverture : le ViewModel est neuf, et le mode se
    /// choisit une seule fois dans sa vie. Le refus est **explicite** plutôt que silencieux - sans lui,
    /// une seconde préparation laisserait les champs de la précédente en place, et l'écran mentirait
    /// sans que rien ne le signale.
    private void exigerPremierePreparation() {
        if (prepare) {
            throw new IllegalStateException("Ce ViewModel de site a déjà été préparé : il ne sert qu'une"
                    + " fois. Rechargez la modale plutôt que de rejouer sa préparation.");
        }
        prepare = true;
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
        return peutEnregistrer;
    }

    /// Le motif du grisage d'« Enregistrer », ou vide s'il n'est pas grisé (#1970, #3806). Deux causes
    /// distinctes, deux phrases : un carré incomplet ne se corrige pas comme un carré déjà pris.
    public String motifEnregistrementFerme() {
        if (!carreValide.get()) {
            return "Renseignez d'abord un numéro de carré à 6 chiffres.";
        }
        if (enCreation.get() && carre.recuperable().get()) {
            return "Ce carré existe déjà sur Vigie-Chiro : récupérez-le plutôt que de le redéclarer.";
        }
        return "";
    }

    /// La modale est-elle en **déclaration** ? (#3806)
    public ReadOnlyBooleanProperty enCreation() {
        return enCreation.getReadOnlyProperty();
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
