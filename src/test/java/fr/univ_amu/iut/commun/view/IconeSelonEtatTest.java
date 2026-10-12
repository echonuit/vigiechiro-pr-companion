package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.recette.SansEcritureHorsDuFil;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests du socle [IconeSelonEtat] (#1933, lot 2). [ApplicationExtension] initialise le toolkit JavaFX
/// (un `FontIcon` est un nœud) ; aucune scène affichée.
///
/// Le contrat de cette classe **est** le défaut qu'elle corrige : trois boutons de l'application
/// changent de sens selon leur état - « Téléverser » devient « Reprendre le dépôt », « Marquer
/// déposé » devient « Lancer la participation », « Carte » devient « Tableau ». Leur libellé suivait
/// par un `Bindings.when(…)`, leur icône restait figée sur le premier des deux sens : elle disait donc
/// **le contraire du mot une fois sur deux**.
///
/// Ce test est le seul de la suite à regarder une **icône**. La conversion de #1933 a produit une
/// soixantaine de `FontIcon`, et un contrôle affiché avec la mauvaise icône ne fait rougir aucun test
/// d'intégration : ceux-ci vérifient le libellé et l'action, jamais le glyphe. La couverture des
/// icônes liées à un état est suivie par #1564.
@ExtendWith({ApplicationExtension.class, SansEcritureHorsDuFil.class})
class IconeSelonEtatTest {

    @Test
    @DisplayName("L'icône prend le glyphe du sens courant, dans les deux sens")
    void l_icone_suit_l_etat() {
        FontIcon icone = new FontIcon();
        SimpleBooleanProperty carteAffichee = new SimpleBooleanProperty(false);

        IconeSelonEtat.lier(icone, carteAffichee, FontAwesomeSolid.TABLE, FontAwesomeSolid.MAP);

        assertThat(icone.getIconCode())
                .as("carte masquée : le bouton propose « Carte », son icône doit être une carte")
                .isEqualTo(FontAwesomeSolid.MAP);

        carteAffichee.set(true);
        assertThat(icone.getIconCode())
                .as("carte affichée : le bouton propose « Tableau », son icône doit suivre le mot")
                .isEqualTo(FontAwesomeSolid.TABLE);

        carteAffichee.set(false);
        assertThat(icone.getIconCode())
                .as("retour à l'état initial : l'icône revient, la liaison n'est pas à sens unique")
                .isEqualTo(FontAwesomeSolid.MAP);
    }

    @Test
    @DisplayName("L'état initial est pris en compte à la liaison, pas seulement au premier changement")
    void l_etat_initial_est_pris_en_compte() {
        FontIcon icone = new FontIcon(FontAwesomeSolid.QUESTION);
        SimpleBooleanProperty depotEntame = new SimpleBooleanProperty(true);

        IconeSelonEtat.lier(icone, depotEntame, FontAwesomeSolid.REDO, FontAwesomeSolid.CLOUD);

        assertThat(icone.getIconCode())
                .as("un contrôle construit dans son second sens doit s'afficher juste dès l'ouverture "
                        + "de l'écran, sans attendre que l'état bouge")
                .isEqualTo(FontAwesomeSolid.REDO);
    }
}
