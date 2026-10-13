package fr.univ_amu.iut.e2e;

import fr.univ_amu.iut.audio.view.SonsValidationController;
import fr.univ_amu.iut.commun.view.EcranReglagesController;
import fr.univ_amu.iut.commun.view.MainController;
import fr.univ_amu.iut.commun.view.Navigateur;
import fr.univ_amu.iut.commun.viewmodel.AccueilViewModel;
import fr.univ_amu.iut.commun.viewmodel.EtatConnexion;
import fr.univ_amu.iut.commun.viewmodel.NavigationViewModel;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.RevisionDonnees;
import fr.univ_amu.iut.connexion.view.ConnexionModaleController;
import fr.univ_amu.iut.connexion.viewmodel.RefletDuJeton;
import fr.univ_amu.iut.importation.view.ImportationController;
import fr.univ_amu.iut.importation.viewmodel.PreferenceConservation;
import fr.univ_amu.iut.lot.view.LotController;
import fr.univ_amu.iut.qualification.view.QualificationController;
import fr.univ_amu.iut.sites.view.MesSitesController;
import fr.univ_amu.iut.sites.view.ModaleSiteController;
import fr.univ_amu.iut.sites.view.SiteDetailController;
import java.util.Map;
import java.util.Set;

/// L'inventaire des écrans qui reçoivent un objet qui vit autant que l'application et qu'on peut
/// écouter (#6184).
///
/// Un écran qui pose un écouteur fort sur un tel objet reste vivant jusqu'à la fermeture : la fiche d'un
/// carré le faisait sur `EtatConnexion`, et rien ne rougissait (#6170). Trois listes se tiennent l'une
/// l'autre. Les singletons surveillés, que la composition confirme ([EcranQuitteEstRenduTest]). Les
/// écrans qui les reçoivent, que le même test ouvre, quitte et veut rendus. Et ce que l'inventaire
/// laisse de côté en le disant.
public final class EcransSuivantUnSingleton {

    private EcransSuivantUnSingleton() {}

    /// Les singletons observables qu'une vue reçoit, par son constructeur ou par un collaborateur que la
    /// composition lui construit, son modèle de vue par exemple.
    public static final Set<Class<?>> SINGLETONS_SURVEILLES = Set.of(
            EtatConnexion.class,
            RefletDuJeton.class,
            ReglagesReactifs.class,
            PreferenceConservation.class,
            NavigationViewModel.class);

    /// Les singletons observables dont l'écoute est tenue ailleurs, avec la raison.
    public static final Map<Class<?>, String> SINGLETONS_TENUS_AILLEURS = Map.of(
            RevisionDonnees.class,
            "le Navigateur pose et rend l'abonnement de l'écran (ADR 3651)",
            Navigateur.class,
            "il tient les écrans lui-même, tant qu'ils sont dans son historique",
            AccueilViewModel.class,
            "le modèle de l'accueil, que seul l'accueil lit");

    /// Les écrans de l'historique qui reçoivent un singleton surveillé : chacun est ouvert, quitté et
    /// voulu rendu par [EcranQuitteEstRenduTest].
    public static final Set<Class<?>> ECRANS = Set.of(
            MesSitesController.class,
            SiteDetailController.class,
            EcranReglagesController.class,
            ImportationController.class,
            SonsValidationController.class,
            QualificationController.class,
            LotController.class);

    /// Les contrôleurs qui reçoivent un singleton surveillé sans passer par l'historique du navigateur,
    /// avec la raison. Le test d'exécution ne les ouvre pas : rien ici ne dit qu'ils sont rendus.
    public static final Map<Class<?>, String> HORS_HISTORIQUE = Map.of(
            ModaleSiteController.class,
            "une modale : elle vit le temps de sa fenêtre, et suit la connexion par liaison",
            ConnexionModaleController.class,
            "une modale : elle demande au reflet du jeton de se relire, et n'y pose aucun écouteur",
            MainController.class,
            "le chrome : il vit autant que l'application, comme le modèle de navigation qu'il affiche");
}
