package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.DescripteurReglage;
import fr.univ_amu.iut.lot.model.ModeDepot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Onglet « Dépôt » de l'écran Réglages (#1047) : les descripteurs pointent les clés lues par
/// `LotModule` : impossible de dériver. On vérifie clé, défaut, bornes et type.
class OngletReglagesDepotTest {

    @Test
    @DisplayName("l'onglet Dépôt déclare le plafond d'archive (700 Mo par défaut, borné [50, 700])")
    void declare_le_plafond() {
        OngletReglagesDepot onglet = new OngletReglagesDepot();

        assertThat(onglet.idFeature()).isEqualTo("lot");
        assertThat(onglet.titre()).isEqualTo("Dépôt");
        DescripteurReglage.Entier plafond = (DescripteurReglage.Entier) onglet.reglages().stream()
                .filter(DescripteurReglage.Entier.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(plafond.cle()).isEqualTo(OngletReglagesDepot.CLE_TAILLE_MAX);
        assertThat(plafond.defaut()).isEqualTo(OngletReglagesDepot.DEFAUT_TAILLE_MAX_MO);
        assertThat(plafond.min()).isEqualTo(50);
        assertThat(plafond.max()).isEqualTo(700);
    }

    @Test
    @DisplayName("#1997 : l'onglet déclare la forme du dépôt, ZIP par défaut, avec ses deux modes")
    void declare_le_mode_de_depot() {
        OngletReglagesDepot onglet = new OngletReglagesDepot();

        DescripteurReglage.Enumeration mode = (DescripteurReglage.Enumeration) onglet.reglages().stream()
                .filter(DescripteurReglage.Enumeration.class::isInstance)
                .findFirst()
                .orElseThrow();

        assertThat(mode.cle()).isEqualTo(OngletReglagesDepot.CLE_MODE_DEPOT);
        assertThat(mode.defaut())
                .as("le défaut reste le comportement établi")
                .isEqualTo(ModeDepot.ARCHIVES_ZIP.valeur());
        assertThat(mode.options())
                .extracting(DescripteurReglage.Enumeration.Option::valeur)
                .containsExactly(ModeDepot.ARCHIVES_ZIP.valeur(), ModeDepot.SEQUENCES_WAV.valeur());
    }

    @Test
    @DisplayName("#1997 : l'aide énonce la conséquence du choix sur l'audio en ligne (#1244)")
    void l_aide_enonce_la_consequence() {
        // Le point du lot n'est pas d'offrir un bouton de plus, c'est que l'utilisateur sache ce qu'il
        // engage : en ZIP, l'audio ne reste pas récupérable côté serveur et la participation ne pourra
        // pas être relancée. Une option muette ne vaudrait guère mieux qu'un choix subi.
        DescripteurReglage.Enumeration mode = (DescripteurReglage.Enumeration) new OngletReglagesDepot()
                .reglages().stream()
                        .filter(DescripteurReglage.Enumeration.class::isInstance)
                        .findFirst()
                        .orElseThrow();

        assertThat(mode.aide())
                .contains("supprime l'archive")
                .contains("relancée")
                .contains("reste en ligne");
    }
}
