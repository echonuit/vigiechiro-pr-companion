package fr.univ_amu.iut.importation.view;

import fr.univ_amu.iut.commun.view.LibelleRetour;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.VisibiliteGeree;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import fr.univ_amu.iut.importation.viewmodel.ImportationViewModel;
import fr.univ_amu.iut.sites.model.PointDEcoute;
import fr.univ_amu.iut.sites.model.Site;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/// La zone d'avertissement du **n° de passage** de l'assistant d'import : son message et ses gestes.
///
/// Elle ne se montre qu'en cas de problème, et le problème n'est pas toujours le même. Le cas historique
/// est le doublon de n° (R5, #108) : le message propose le prochain n° libre, avec deux gestes -
/// l'adopter, ou écraser le passage existant.
///
/// Le cas neuf est une nuit **déjà récupérée** de Vigie-Chiro (#2580) : elle est déjà là, avec ses
/// observations et son rattachement, mais sans son audio. La zone garde alors sa place et son texte, mais
/// **échange ses gestes**.
/// Les deux gestes habituels y deviennent des pièges :
///
///  - « Utiliser ce n° » importerait la nuit une seconde fois, sur un n° voisin : la nuit existerait
///    alors en deux moitiés, l'une avec ses observations et son rattachement, l'autre avec son son ;
///  - « Écraser et réimporter » supprimerait le passage en cascade - donc ses observations, les
///    validations déjà faites et son rattachement à la participation - pour réimporter un audio qu'on
///    peut lui rendre sans rien perdre.
///
/// Ni l'un ni l'autre n'est proposé. Reste « Ouvrir cette nuit ».
class ZoneNumeroPassage {

    private final ImportationViewModel viewModel;
    private final OuvrirPassage ouvrirPassage;

    ZoneNumeroPassage(ImportationViewModel viewModel, OuvrirPassage ouvrirPassage) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.ouvrirPassage = Objects.requireNonNull(ouvrirPassage, "ouvrirPassage");
    }

    /// Installe la zone : son message, sa visibilité, et le jeu de gestes qui correspond au problème.
    /// `traitement` gèle les gestes pendant un import en cours ; `inspecte` conditionne l'écrasement, qui
    /// n'a rien à réimporter sans nuit inspectée.
    void installer(
            Node zone,
            Label message,
            Boutons boutons,
            ObservableValue<Boolean> traitement,
            ObservableValue<Boolean> inspecte) {
        LibelleRetour.installer(message, viewModel.avertissementNumeroPassageProperty());
        VisibiliteGeree.lier(
                zone,
                Bindings.createBooleanBinding(
                        () -> viewModel
                                .avertissementNumeroPassageProperty()
                                .get()
                                .present(),
                        viewModel.avertissementNumeroPassageProperty()));
        BooleanBinding recuperee = Bindings.createBooleanBinding(
                this::estReconnue, viewModel.controleNumero().nuitRecupereeProperty());
        VisibiliteGeree.lier(boutons.numeroLibre(), recuperee.not());
        VisibiliteGeree.lier(boutons.ecraser(), recuperee.not());
        VisibiliteGeree.lier(boutons.ouvrirNuit(), recuperee);
        boutons.numeroLibre().disableProperty().bind(traitement);
        boutons.ouvrirNuit().disableProperty().bind(traitement);
        boutons.ecraser()
                .disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> traitement.getValue() || !Boolean.TRUE.equals(inspecte.getValue()),
                        traitement,
                        inspecte));
    }

    /// Les trois gestes de la zone, groupés pour ne pas aligner cinq paramètres de même type (#2483).
    record Boutons(Button numeroLibre, Button ecraser, Button ouvrirNuit) {}

    /// `true` quand la nuit inspectée est déjà là, récupérée de Vigie-Chiro.
    boolean estReconnue() {
        return viewModel.controleNumero().nuitRecupereeProperty().get().isPresent();
    }

    /// Quitte l'assistant pour la fiche de cette nuit. Sans effet si aucune n'est reconnue.
    ///
    /// Le contexte de site affiché en tête est celui du rattachement en cours de saisie : c'est
    /// l'identifiant du passage qui décide du contenu, le contexte ne fait que nommer le fil d'Ariane.
    void ouvrir() {
        viewModel
                .controleNumero()
                .nuitRecupereeProperty()
                .get()
                .ifPresent(idPassage -> ouvrirPassage.ouvrir(idPassage, contexteSite()));
    }

    private ContexteSite contexteSite() {
        Site site = viewModel.rattachement().siteSelectionneProperty().get();
        PointDEcoute point = viewModel.rattachement().pointSelectionneProperty().get();
        return new ContexteSite(
                site == null ? null : site.numeroCarre(),
                point == null ? null : point.code(),
                site == null ? null : site.nomConvivial());
    }
}
