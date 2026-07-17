package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import fr.univ_amu.iut.commun.view.ActionFicheEspece;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import java.util.Objects;
import javafx.scene.control.MenuItem;

/// Regroupe les actions « externes » du menu ☰ de Sons & validation (fiche d’espèce #847, données
/// Vigie-Chiro #1124) : objet-paramètre qui garde le constructeur de [SonsValidationController]
/// sous le plafond `ExcessiveParameterList`, patron des objets `Campagne*`.
final class ActionsMenuAudio {

    private final ActionFicheEspece ficheEspece;
    private final ActionDonneesVigieChiro donneesVigieChiro;

    /// Item « Fiche de l'espèce » du **menu contextuel** de la table (#1795), tenu ici — comme l'item du ☰
    /// est tenu par le controller : instance distincte, un [MenuItem] n'appartenant qu'à un seul menu.
    private final MenuItem itemFicheContexte = new MenuItem();

    @Inject
    ActionsMenuAudio(ActionFicheEspece ficheEspece, ActionDonneesVigieChiro donneesVigieChiro) {
        this.ficheEspece = Objects.requireNonNull(ficheEspece, "ficheEspece");
        this.donneesVigieChiro = Objects.requireNonNull(donneesVigieChiro, "donneesVigieChiro");
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

    /// Ouvre la fiche de la proposition Tadarida de `ligne` dans le navigateur (double-clic, #1794) ;
    /// inerte si `ligne` est nulle ou si le taxon n'a pas de fiche (pseudo-taxon). Même cible que
    /// l'item « Fiche de l'espèce » du menu ☰.
    void ouvrirFiche(LigneObservationAudio ligne) {
        ficheEspece.ouvrir(especeDe(ligne));
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
