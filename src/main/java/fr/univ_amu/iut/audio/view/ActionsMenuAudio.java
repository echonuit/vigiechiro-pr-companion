package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import fr.univ_amu.iut.commun.view.ActionFicheEspece;
import fr.univ_amu.iut.commun.view.MenuCopier;
import fr.univ_amu.iut.commun.view.MenuLigne;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;

/// Regroupe les actions « externes » du menu ☰ de Sons & validation (fiche d’espèce #847, données
/// Vigie-Chiro #1124) : objet-paramètre qui garde le constructeur de [SonsValidationController]
/// sous le plafond `ExcessiveParameterList`, patron des objets `Campagne*`.
final class ActionsMenuAudio {

    private final ActionFicheEspece ficheEspece;
    private final ActionDonneesVigieChiro donneesVigieChiro;
    private final OuvrirPassage ouvrirPassage;

    /// Item « Fiche de l'espèce » du **menu contextuel** de la table (#1795), tenu ici — comme l'item du ☰
    /// est tenu par le controller : instance distincte, un [MenuItem] n'appartenant qu'à un seul menu.
    private final MenuItem itemFicheContexte = new MenuItem();

    @Inject
    ActionsMenuAudio(
            ActionFicheEspece ficheEspece, ActionDonneesVigieChiro donneesVigieChiro, OuvrirPassage ouvrirPassage) {
        this.ficheEspece = Objects.requireNonNull(ficheEspece, "ficheEspece");
        this.donneesVigieChiro = Objects.requireNonNull(donneesVigieChiro, "donneesVigieChiro");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    /// Item « Ouvrir le passage » du menu de ligne (#1796) : ouvre M-Passage sur le passage de la ligne
    /// sélectionnée, avec le contexte du site (carré/point) pour le fil d'Ariane. Désactivé sans sélection.
    MenuItem itemOuvrirPassage(TableView<LigneObservationAudio> table) {
        return MenuLigne.item(
                "Ouvrir le passage",
                table,
                ligne -> ouvrirPassage.ouvrir(
                        ligne.idPassage(), new ContexteSite(ligne.numeroCarre(), ligne.codePoint(), ligne.nomSite())));
    }

    /// Cible l'item « Fiche de l'espèce » (#847) sur la proposition Tadarida de la ligne sélectionnée
    /// (désactivé avec explication si aucune ligne, ou pseudo-taxon sans fiche).
    void configurerFiche(MenuItem item, LigneObservationAudio ligne) {
        ficheEspece.configurer(item, especeDe(ligne));
    }

    /// Item « Fiche de l'espèce » à installer dans le **menu contextuel** de la table (#1795), en plus de
    /// celui du ☰ (tenu par le controller).
    MenuItem itemFicheContexte() {
        return itemFicheContexte;
    }

    /// Configure sur `ligne` l'item du ☰ (`itemMenu`) **et** l'item du clic droit ([#itemFicheContexte]),
    /// pour qu'ils ciblent la même proposition Tadarida à chaque changement de sélection.
    void configurerFiches(MenuItem itemMenu, LigneObservationAudio ligne) {
        configurerFiche(itemMenu, ligne);
        configurerFiche(itemFicheContexte, ligne);
    }

    /// Ouvre la fiche de la proposition Tadarida de `ligne` dans le navigateur (double-clic, #1794), même
    /// cible que l'item « Fiche de l'espèce » du menu ☰.
    ///
    /// Quand le taxon n'a pas de fiche (pseudo-taxons « Bruit » / « Oiseau », couple sans binôme), rien ne
    /// s'ouvre et le motif part dans `signaler`, qui l'affiche dans le bandeau de retour. Le menu, lui, se
    /// contente de griser son item : il montre son état **avant** le clic, quand le double-clic n'a rien à
    /// montrer et passait donc pour cassé (#1834).
    void ouvrirFiche(LigneObservationAudio ligne, Consumer<String> signaler) {
        if (ligne != null) {
            ficheEspece.ouvrirOuSignaler(especeDe(ligne), signaler);
        }
    }

    /// Sous-menu « Copier ▸ » de la ligne audio (#1798) : nom latin de la proposition Tadarida et n° de
    /// carré, valeurs qu'on recoupe souvent hors de l'application.
    Menu menuCopier(TableView<LigneObservationAudio> table) {
        return MenuCopier.creer(
                table,
                new MenuCopier.Entree<>("Nom latin", LigneObservationAudio::latinTadarida),
                new MenuCopier.Entree<>("Carré", LigneObservationAudio::numeroCarre));
    }

    private static EspeceIdentifiee especeDe(LigneObservationAudio ligne) {
        return ligne == null
                ? new EspeceIdentifiee(null, null, null)
                : new EspeceIdentifiee(ligne.taxonTadarida(), ligne.latinTadarida(), ligne.nomTadarida());
    }

    ActionDonneesVigieChiro donneesVigieChiro() {
        return donneesVigieChiro;
    }
}
