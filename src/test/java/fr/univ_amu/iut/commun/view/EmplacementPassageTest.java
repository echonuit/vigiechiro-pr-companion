package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La fabrique des segments du fil d'Ariane autour d'un passage (#3521).
///
/// Elle est partagée par **sept contrôleurs** - passage, qualification, diagnostic, lot, activité,
/// synthèse, chrome audio - et sa raison d'être est que le fil soit **identique quelle que soit la
/// route**. Aucun test ne l'atteignait : la mesure de mutation comptait 13 mutants sans couverture.
///
/// ⚠️ Ce que l'absence de test laissait passer, ce n'est pas un fil mal formé - un écran l'aurait
/// montré - mais un fil **cliquable qui n'ouvre pas ce qu'il annonce**. Un segment porte un `Runnable` ;
/// le supprimer ne change rien à l'affichage.
class EmplacementPassageTest {

    private static final ContexteSite SITE = new ContexteSite("640380", "A1", "Étang de la Tuilière");

    // ------------------------------------------------------------------ ancêtres du site

    @Test
    @DisplayName("#3521 : les ancêtres sont « Mes sites › Carré N », et chacun ouvre ce qu'il annonce")
    void ancetres_ouvrent_ce_qu_ils_annoncent() {
        OuvrirSite ouvrirSite = mock(OuvrirSite.class);

        List<Lieu> ancetres = EmplacementPassage.ancetresSite(SITE, ouvrirSite);

        assertThat(ancetres).extracting(Lieu::libelle).containsExactly("Mes sites", "Carré 640380");

        ancetres.get(0).ouvrir().run();
        verify(ouvrirSite).ouvrirListe();

        ancetres.get(1).ouvrir().run();
        verify(ouvrirSite).ouvrirDetail("640380");
    }

    @Test
    @DisplayName("#3521 : sans carré connu, il n'y a pas d'ancêtre - l'appelant retombe sur un fil minimal")
    void ancetres_vides_quand_le_carre_est_inconnu() {
        OuvrirSite ouvrirSite = mock(OuvrirSite.class);

        assertThat(EmplacementPassage.ancetresSite(null, ouvrirSite)).isEmpty();
        assertThat(EmplacementPassage.ancetresSite(new ContexteSite(null, "A1", "Sans carré"), ouvrirSite))
                .isEmpty();
    }

    // ------------------------------------------------------------------ libellé du passage

    @Test
    @DisplayName("#3521 : le numéro n'apparaît qu'à partir de 1, la borne comprise")
    void libelle_du_passage_a_sa_borne() {
        // La même règle « numéro > 0 » vit à trois endroits : ici, dans `ContextePassage.identiteStatut`
        // et dans `LegendeExport`. Les deux autres ont leur test ; celui-ci manquait.
        assertThat(EmplacementPassage.libellePassage(0)).isEqualTo("Détails du passage");
        assertThat(EmplacementPassage.libellePassage(1)).isEqualTo("Détails du passage N° 1");
        assertThat(EmplacementPassage.libellePassage(3)).isEqualTo("Détails du passage N° 3");
    }

    // ------------------------------------------------------------------ emplacement d'un écran enfant

    @Test
    @DisplayName("#3521 : un écran enfant porte le fil complet, dont seul le dernier segment n'est pas cliquable")
    void emplacement_enfant_complet() {
        OuvrirSite ouvrirSite = mock(OuvrirSite.class);
        OuvrirPassage ouvrirPassage = mock(OuvrirPassage.class);
        ContextePassage passage = new ContextePassage(42L, 3, SITE);

        List<Lieu> fil = EmplacementPassage.emplacementEnfant(passage, ouvrirSite, ouvrirPassage, "Vérification");

        assertThat(fil)
                .extracting(Lieu::libelle)
                .containsExactly("Mes sites", "Carré 640380", "Détails du passage N° 3", "Vérification");
        assertThat(fil.get(3).ouvrir())
                .as("l'écran courant n'est pas cliquable")
                .isNull();

        // Le segment du passage rouvre l'écran pivot AVEC son contexte : c'est ce qui rend le fil
        // identique depuis M-Sites et depuis M-Multisite, la promesse de la classe.
        fil.get(2).ouvrir().run();
        verify(ouvrirPassage).ouvrir(42L, SITE);
    }

    @Test
    @DisplayName("#3521 : sans passage ou sans carré, le fil se réduit à l'écran courant")
    void emplacement_enfant_minimal() {
        OuvrirSite ouvrirSite = mock(OuvrirSite.class);
        OuvrirPassage ouvrirPassage = mock(OuvrirPassage.class);

        assertThat(EmplacementPassage.emplacementEnfant(null, ouvrirSite, ouvrirPassage, "Diagnostic"))
                .extracting(Lieu::libelle)
                .containsExactly("Diagnostic");

        ContextePassage sansCarre = new ContextePassage(42L, 3, new ContexteSite(null, "A1", "Sans carré"));
        assertThat(EmplacementPassage.emplacementEnfant(sansCarre, ouvrirSite, ouvrirPassage, "Diagnostic"))
                .extracting(Lieu::libelle)
                .containsExactly("Diagnostic");
    }
}
