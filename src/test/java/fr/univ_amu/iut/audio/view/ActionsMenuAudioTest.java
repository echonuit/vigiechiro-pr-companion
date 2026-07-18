package fr.univ_amu.iut.audio.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import fr.univ_amu.iut.commun.model.ConstructeurLienEspece;
import fr.univ_amu.iut.commun.view.ActionFicheEspece;
import fr.univ_amu.iut.commun.view.ExecuteurFicheSynchrone;
import fr.univ_amu.iut.commun.view.OuvrirPassage;
import fr.univ_amu.iut.commun.view.ResolveurFicheIdentite;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Vérifie que le double-clic **rend compte** quand il n'ouvre rien (#1834) : sans ce retour, le geste
/// reste muet et passe pour cassé, comme constaté sur une nuit réelle où la quasi-totalité des lignes
/// sont des pseudo-taxons « Bruit ».
///
/// Testé hors JavaFX : [ActionsMenuAudio#ouvrirFiche] ne fait que fournir la ligne et le canal de
/// signalement. Le motif lui-même est construit par `ActionFicheEspece` (mutualisé avec l'Inventaire,
/// #1837) ; qu'il atteigne vraiment le bandeau est couvert par `SonsValidationArchiveViewTest`.
class ActionsMenuAudioTest {

    /// Source universelle **muette** : seule la table PNA embarquée résout un lien, ce qui rend le test
    /// déterministe et hors réseau.
    private static final ConstructeurLienEspece SANS_SOURCE_UNIVERSELLE =
            new ConstructeurLienEspece(nomLatin -> Optional.empty());

    private final List<String> urlsOuvertes = new ArrayList<>();
    private final List<String> motifs = new ArrayList<>();

    private ActionsMenuAudio actions;

    @BeforeEach
    void preparer() {
        ActionFicheEspece fiche = new ActionFicheEspece(
                SANS_SOURCE_UNIVERSELLE,
                urlsOuvertes::add,
                new ResolveurFicheIdentite(),
                new ExecuteurFicheSynchrone());
        actions = new ActionsMenuAudio(fiche, mock(ActionDonneesVigieChiro.class), mock(OuvrirPassage.class));
    }

    private static LigneObservationAudio ligne(String tadarida, String nomTadarida) {
        return new LigneObservationAudio(
                1L,
                10L,
                7L,
                1,
                "2026-06-20",
                "640380",
                "A1",
                "Mon site",
                tadarida,
                0.9,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                nomTadarida,
                null,
                "Chiroptères",
                "PaRec_10_000.wav",
                0.20,
                0.32,
                null,
                false,
                null,
                null,
                null,
                null,
                0);
    }

    @Test
    @DisplayName("Un chiroptère à fiche PNA : la fiche s'ouvre, rien n'est signalé")
    void taxon_avec_fiche_ouvre_sans_signaler() {
        actions.ouvrirFiche(ligne("Pippip", "Pipistrelle commune"), motifs::add);

        assertThat(urlsOuvertes)
                .containsExactly(
                        "https://plan-actions-chiropteres.fr/les-chauves-souris/les-especes/pipistrelle-commune/");
        assertThat(motifs).as("rien à dire quand l'action aboutit").isEmpty();
    }

    @Test
    @DisplayName("Un pseudo-taxon : rien ne s'ouvre, et le motif le nomme comme la table l'affiche")
    void pseudo_taxon_signale_le_motif() {
        actions.ouvrirFiche(ligne("noise", "Bruit"), motifs::add);

        assertThat(urlsOuvertes).isEmpty();
        assertThat(motifs).containsExactly("Aucune fiche disponible pour « Bruit ».");
    }

    @Test
    @DisplayName("Sans libellé lisible, le motif se rabat sur le code Tadarida plutôt que de rester vague")
    void sans_libelle_le_motif_reprend_le_code() {
        actions.ouvrirFiche(ligne("Xyzabc", null), motifs::add);

        assertThat(motifs).containsExactly("Aucune fiche disponible pour « Xyzabc ».");
    }

    @Test
    @DisplayName("Sans libellé ni code, le motif reste compréhensible plutôt que de nommer du vide")
    void sans_libelle_ni_code_le_motif_reste_lisible() {
        actions.ouvrirFiche(ligne(null, "  "), motifs::add);

        assertThat(motifs).containsExactly("Aucune fiche disponible pour ce taxon.");
    }

    @Test
    @DisplayName("Aucune ligne sous le curseur : ni ouverture ni message")
    void ligne_absente_ne_fait_rien() {
        actions.ouvrirFiche(null, motifs::add);

        assertThat(urlsOuvertes).isEmpty();
        assertThat(motifs).isEmpty();
    }
}
