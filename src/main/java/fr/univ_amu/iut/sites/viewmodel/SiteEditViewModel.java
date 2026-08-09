package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.viewmodel.RetourOperation;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
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

    public SiteEditViewModel(ServiceSites service, LienVigieChiroDao liens, String idUtilisateur) {
        this.liens = Objects.requireNonNull(liens, "liens");
        this.service = Objects.requireNonNull(service, "service");
        this.idUtilisateur = Objects.requireNonNull(idUtilisateur, "idUtilisateur");
        carreValide = Bindings.createBooleanBinding(() -> numeroCarre.get().matches("\\d{6}"), numeroCarre);
        carreInvalideEtSaisi = Bindings.createBooleanBinding(
                () -> !numeroCarre.get().isEmpty() && !numeroCarre.get().matches("\\d{6}"), numeroCarre);
    }

    /// Configure la modale en **mode déclaration** d'un nouveau site.
    public void preparerCreation() {
        siteEnEdition = null;
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
