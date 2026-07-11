package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.PreferenceSourceEspece;
import fr.univ_amu.iut.commun.persistence.ServicePurgeOriginaux;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;

/// Dépendances du menu ☰ (outils) regroupées, injectées d'un bloc dans [MainController].
///
/// Double intérêt : matérialiser la **cohésion** « ce que le menu Outils manipule » (sauvegarde /
/// restauration de la base, purge des originaux, connexion VigieChiro, source des fiches espèces,
/// écran Réglages), et **garder compact** le constructeur de [MainController] (déjà au plafond de
/// paramètres) au lieu de l'allonger d'un cran à chaque nouvelle entrée de menu.
///
/// Guice injecte le **constructeur canonique** du record (annoté [Inject]).
public record DependancesMenu(
        ServiceSauvegarde sauvegarde,
        ServicePurgeOriginaux purge,
        OuvrirConnexion connexion,
        PreferenceSourceEspece sourceEspece,
        NavigationReglages reglages) {

    @Inject
    public DependancesMenu {}
}
