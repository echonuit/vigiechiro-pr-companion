package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.PanneauCompteRendu;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Action;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.viewmodel.CompteRenduChiffreImport;
import fr.univ_amu.iut.importation.viewmodel.EtatImport;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import java.util.List;
import java.util.Objects;

/// Publication du **compte rendu chiffré** de fin d'import (#2358) dans la bande de l'écran.
///
/// Extrait de [ImportationController], déjà volumineux, pour le garder sous le plafond de taille (PMD
/// `NcssCount`), comme [FormatsImport] avant lui. Ce n'est pas qu'une question de mesure : brancher un
/// compte rendu, c'est décider ce qu'il dit ET où mène son action suivante, deux choix qui se lisent
/// mieux ensemble qu'éparpillés au milieu de quarante liaisons de contrôles.
///
/// La bande **remplace trois surfaces** qui disaient la même chose en trois formes : la phrase de succès,
/// le compte rendu textuel (#2004) et la liste des fichiers rejetés (#155), dont la dernière s'affichait
/// à hauteur nulle (2ᵉ constat de #1486).
final class CompteRenduDeFinImport {

    private final ImportationViewModel viewModel;

    private final OuvrirPassage ouvrirPassage;

    CompteRenduDeFinImport(ImportationViewModel viewModel, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    /// Branche `panneau` sur le résultat de l'import et publie l'état courant.
    ///
    /// Le compte rendu est **reconstruit** à chaque changement plutôt que mis à jour en place : il est
    /// immuable et publié d'un bloc, il n'y a rien à rafraîchir.
    void brancher(PanneauCompteRendu panneau) {
        viewModel.resultatProperty().addListener((observable, avant, apres) -> publier(panneau));
        viewModel.resultatNuitsProperty().addListener((observable, avant, apres) -> publier(panneau));
        viewModel.etatProperty().addListener((observable, avant, apres) -> publier(panneau));
        publier(panneau);
    }

    private void publier(PanneauCompteRendu panneau) {
        var multiNuits = viewModel.resultatNuitsProperty().get();
        var mono = viewModel.resultatProperty().get();
        boolean aRendre = viewModel.etatProperty().get() == EtatImport.TERMINE && (multiNuits != null || mono != null);
        if (aRendre) {
            panneau.afficher(
                    multiNuits != null
                            ? CompteRenduChiffreImport.de(multiNuits, actionsApresImport(multiNuits.premier()))
                            : CompteRenduChiffreImport.de(mono, actionsApresImport(mono)));
        }
        panneau.setVisible(aRendre);
        panneau.setManaged(aRendre);
    }

    /// L'action suivante : ouvrir le passage créé. Un compte rendu ne se termine pas sur « Fermer » - la
    /// question réelle de l'utilisateur à cet instant est « et maintenant ? », et la réponse est l'écran
    /// pivot de la nuit qu'il vient d'importer.
    ///
    /// En multi-nuits, elle ouvre le **premier** passage : c'est le point d'entrée de la série, et proposer
    /// N boutons dans un pied ferait de l'action suivante une liste de plus.
    private List<Action> actionsApresImport(ResultatImport resultat) {
        if (resultat == null || resultat.passage() == null || resultat.passage().id() == null) {
            return List.of();
        }
        Long idPassage = resultat.passage().id();
        return List.of(new Action("Ouvrir le passage", true, () -> ouvrirPassage.ouvrir(idPassage, contexteSite())));
    }

    /// Le contexte de site que l'écran pivot affiche en tête, pris du rattachement choisi pour cet import.
    private ContexteSite contexteSite() {
        Site site = viewModel.rattachement().siteSelectionneProperty().get();
        PointDEcoute point = viewModel.rattachement().pointSelectionneProperty().get();
        return new ContexteSite(
                site == null ? null : site.numeroCarre(),
                point == null ? null : point.code(),
                site == null ? null : site.nomConvivial());
    }
}
