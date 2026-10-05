package fr.univ_amu.iut.lot.view;

import fr.univ_amu.iut.lot.viewmodel.DepotViewModel;
import fr.univ_amu.iut.lot.viewmodel.LotViewModel;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;

/// L'étape « Générer les archives » est-elle offerte par l'écran (#5824) ?
///
/// Connecté pour un dépôt en séquences WAV, rien ne produit d'archive : l'étape disparaît, avec ce qui
/// sert au dépôt manuel d'archives, et les suivantes se renumérotent. Le **fil d'étapes** en est la
/// source : il compte trois puces sans elle, quatre avec. La carte, les titres et les infobulles le
/// lisent ici, pour qu'il n'y ait qu'une façon de le demander.
final class EtapeDesArchives {

    /// Le nombre d'étapes d'un dépôt qui ne passe pas par les archives.
    private static final int ETAPES_SANS_ARCHIVES = 3;

    private EtapeDesArchives() {}

    /// Vraie tant que le fil d'étapes ne dit pas le contraire, écran vide compris.
    static BooleanBinding offerte(LotViewModel lot) {
        return Bindings.size(lot.etapes()).isNotEqualTo(ETAPES_SANS_ARCHIVES);
    }

    /// Le **repli manuel** est-il offert (#5867) ?
    ///
    /// Il l'est quand Vigie-Chiro a refusé des séquences sans recours, là où l'étape des archives n'est
    /// pas déjà une étape du fil, et tant que le passage n'est pas déposé. Hors connexion ou en forme ZIP
    /// la carte est déjà à l'écran : il n'y a rien à replier.
    static BooleanBinding enRepli(LotViewModel lot, DepotViewModel depot) {
        return offerte(lot)
                .not()
                .and(depot.sequencesRefuseesSansRecoursProperty().greaterThan(0))
                .and(lot.deposeProperty().not());
    }

    /// Les archives **servent**-elles, comme étape du fil ou comme repli manuel (#5867) ?
    ///
    /// C'est la question de la carte et des éléments du dépôt manuel. Celle des **numéros** reste
    /// [#offerte] : le repli n'ajoute pas d'étape, il s'offre sous celle qui vient d'être refusée.
    static BooleanBinding servent(LotViewModel lot, DepotViewModel depot) {
        return offerte(lot).or(enRepli(lot, depot));
    }
}
