package fr.univ_amu.iut.sites.viewmodel;

import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.sites.model.ServiceSites;
import fr.univ_amu.iut.sites.model.Site;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
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
    private final ReadOnlyStringWrapper messageErreur = new ReadOnlyStringWrapper(this, "messageErreur", "");

    /// Le carré est le **seul** champ obligatoire : six chiffres (R1). Le nom, le protocole et le
    /// commentaire sont facultatifs - un observateur qui déclare un carré à la volée ne doit pas être
    /// bloqué par de la décoration.
    private final BooleanBinding carreValide;

    /// Carré saisi mais **encore incomplet** : c'est ce qui fait rougir le champ (#790), sans rougir
    /// avant que l'utilisateur ait tapé quoi que ce soit.
    private final BooleanBinding carreInvalideEtSaisi;

    /// Site en cours d'édition ; `null` en création.
    private Site siteEnEdition;

    public SiteEditViewModel(ServiceSites service, String idUtilisateur) {
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
        messageErreur.set("");
        titre.set("Nouveau site de suivi");
        libelleBouton.set("Créer");
    }

    /// Configure la modale en **mode édition** : champs pré-remplis depuis le site existant.
    public void preparerEdition(Site site) {
        siteEnEdition = Objects.requireNonNull(site, "site");
        numeroCarre.set(site.numeroCarre());
        nom.set(ouVide(site.nomConvivial()));
        protocole.set(site.protocole());
        commentaire.set(ouVide(site.commentaire()));
        messageErreur.set("");
        titre.set("Modifier le site · Carré " + site.numeroCarre());
        libelleBouton.set("Enregistrer");
    }

    /// Tente d'enregistrer le site (déclaration ou édition).
    ///
    /// @return `true` si l'enregistrement a réussi (la vue peut fermer la modale) ; `false` si une règle
    ///     métier a refusé - le motif est alors dans [#messageErreurProperty()], **dans la modale**, à
    ///     côté du champ fautif, et non dans une alerte qui s'ouvre après coup par-dessus
    public boolean enregistrer() {
        if (!carreValide.get()) {
            messageErreur.set("Le numéro de carré doit comporter 6 chiffres.");
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
            messageErreur.set("");
            return true;
        } catch (RegleMetierException | IllegalArgumentException refus) {
            messageErreur.set(refus.getMessage());
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

    public ReadOnlyStringProperty messageErreurProperty() {
        return messageErreur.getReadOnlyProperty();
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
}
