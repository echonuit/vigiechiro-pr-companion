package fr.univ_amu.iut.qualification.view;

import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.commun.model.DispositionColonnesEnMemoire;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.OuvrirSite;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import fr.univ_amu.iut.qualification.viewmodel.QualificationViewModel;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;

/// Construit un [QualificationController] avec des doubles par défaut (#4767).
///
/// Le contrôleur porte neuf paramètres, et trois cas de garde les recopiaient à l'identique : un
/// paramètre de plus obligeait à quatre alignements. Il n'en touche plus qu'un, ici.
///
/// **Ce que cette fabrique ne prétend pas être** : la mesure de #4767 dit que l'alignement est peu
/// fréquent et **toujours désigné par le compilateur**. Elle n'a donc pas été écrite pour supprimer
/// une douleur mesurée, mais pour que les cas de garde disent ce qu'ils éprouvent plutôt que de
/// répéter un montage. Le vrai coût était le diagnostic, traité par
/// [fr.univ_amu.iut.commun.di.DiagnosticGuice].
final class ControleurQualificationDeTest {

    private ControleurQualificationDeTest() {}

    /// Le contrôleur, avec les deux ViewModels que le cas fait varier et des doubles pour le reste.
    ///
    /// @param verdictVm le ViewModel du verdict
    /// @param selectionVm le ViewModel de la sélection d'écoute
    /// @return le contrôleur monté
    static QualificationController avec(QualificationViewModel verdictVm, SelectionEcouteViewModel selectionVm) {
        return new QualificationController(
                verdictVm,
                selectionVm,
                (idPassage, contexte) -> {},
                ouvrirSiteNeutre(),
                new DispositionColonnesEnMemoire(),
                new ExecuteurTacheSynchrone(),
                mock(NavigationQualification.class),
                mock(ServiceEmport.class),
                mock(StockageConnexion.class));
    }

    /// Une ouverture de site qui ne fait rien : les cas de garde ne naviguent pas.
    private static OuvrirSite ouvrirSiteNeutre() {
        return new OuvrirSite() {
            @Override
            public void ouvrirListe() {}

            @Override
            public void ouvrirDetail(String numeroCarre) {}
        };
    }
}
