package fr.univ_amu.iut.lot.view;

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
}
