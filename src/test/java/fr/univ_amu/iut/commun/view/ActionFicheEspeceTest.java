package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.ConstructeurLienEspece;
import fr.univ_amu.iut.commun.model.EspeceIdentifiee;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

/// Tests de l'action réutilisable [ActionFicheEspece]. [ApplicationExtension] initialise le toolkit
/// JavaFX (construction du [MenuItem]) ; aucune scène affichée. Le navigateur est un **faux**
/// [OuvreurDeLien] qui enregistre l'URL demandée : on vérifie le câblage **sans réseau**.
@ExtendWith(ApplicationExtension.class)
class ActionFicheEspeceTest {

    private final List<String> urlsOuvertes = new ArrayList<>();
    private final ActionFicheEspece action = new ActionFicheEspece(new ConstructeurLienEspece(), urlsOuvertes::add);

    @Test
    @DisplayName("Chiroptère connu : item actif, libellé enrichi, clic ouvre la fiche PNA")
    void chiroptere_ouvre_la_fiche_au_clic() {
        MenuItem item =
                action.creerItem(new EspeceIdentifiee("Pippip", "Pipistrellus pipistrellus", "Pipistrelle commune"));

        assertThat(item.isDisable()).isFalse();
        assertThat(item.getText()).isEqualTo("Fiche de l'espèce (Pipistrelle commune)");

        item.fire();
        assertThat(urlsOuvertes)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }

    @Test
    @DisplayName("Taxon avec lien mais sans nom vernaculaire : libellé générique, clic ouvre GBIF")
    void taxon_sans_nom_vernaculaire_garde_le_libelle_generique() {
        MenuItem item = action.creerItem(new EspeceIdentifiee("Numarq", "Numenius arquata", null));

        assertThat(item.isDisable()).isFalse();
        assertThat(item.getText()).isEqualTo("Fiche de l'espèce");

        item.fire();
        assertThat(urlsOuvertes).containsExactly("https://www.gbif.org/species/search?q=Numenius+arquata");
    }

    @Test
    @DisplayName("Pseudo-taxon : item désactivé, libellé explicatif, clic sans effet")
    void pseudo_taxon_desactive_et_explique() {
        MenuItem item = action.creerItem(new EspeceIdentifiee("noise", null, null));

        assertThat(item.isDisable()).isTrue();
        assertThat(item.getText()).isEqualTo("Fiche de l'espèce (aucune fiche disponible)");

        item.fire();
        assertThat(urlsOuvertes).isEmpty();
    }

    @Test
    @DisplayName("configurer réutilise un item en place, réversible entre fiche disponible et absente")
    void configurer_reutilise_l_item_en_place() {
        MenuItem item = new MenuItem();

        // Sans fiche : désactivé, libellé explicatif, action retirée.
        action.configurer(item, new EspeceIdentifiee("noise", null, null));
        assertThat(item.isDisable()).isTrue();
        assertThat(item.getText()).isEqualTo("Fiche de l'espèce (aucune fiche disponible)");
        item.fire();
        assertThat(urlsOuvertes).isEmpty();

        // Reconfiguré vers un chiroptère : réactivé, libellé enrichi, clic ouvrant la fiche.
        action.configurer(item, new EspeceIdentifiee("Pippip", "Pipistrellus pipistrellus", "Pipistrelle commune"));
        assertThat(item.isDisable()).isFalse();
        assertThat(item.getText()).isEqualTo("Fiche de l'espèce (Pipistrelle commune)");
        item.fire();
        assertThat(urlsOuvertes)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
    }
}
