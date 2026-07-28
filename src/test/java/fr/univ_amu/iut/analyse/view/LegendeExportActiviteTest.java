package fr.univ_amu.iut.analyse.view;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.view.DescripteurCritere;
import fr.univ_amu.iut.commun.view.DescripteurFiltre;
import fr.univ_amu.iut.commun.view.LegendeExport;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.ContexteSite;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/// Vérifie la **légende de contexte** estampillée sur l'image exportée ([LegendeExportActivite]) : identité
/// (carré, point, passage), réglages (tranche, filtres en clair) et provenance. Une image sans ces mentions
/// devient inexploitable dès qu'elle quitte l'application.
class LegendeExportActiviteTest {

    private static final ContextePassage PASSAGE =
            new ContextePassage(1L, 2, new ContexteSite("640380", "A1", "Étang"));

    @Test
    void l_identite_nomme_le_carre_le_point_et_le_passage() {
        assertThat(LegendeExport.identite(PASSAGE)).isEqualTo("Carré 640380 · Point A1 · Passage N° 2");
    }

    @Test
    void sans_passage_l_identite_dit_la_portee_transverse() {
        assertThat(LegendeExport.identite(null))
                .as("la vue transverse couvre tous les passages : le dire, plutôt que laisser un blanc")
                .isEqualTo("Tous les passages");
    }

    @Test
    void un_passage_sans_numero_ne_l_invente_pas() {
        // ContextePassage porte 0 quand le numéro est inconnu : « Passage N° 0 » serait un faux.
        ContextePassage sansNumero = new ContextePassage(1L, 0, new ContexteSite("640380", "A1", null));

        assertThat(LegendeExport.identite(sansNumero))
                .isEqualTo("Carré 640380 · Point A1")
                .doesNotContain("N° 0");
    }

    @Test
    void un_critere_sans_valeur_est_nomme_sans_egal() {
        // Une puce fraîchement ajoutée, ou un critère booléen, n'a pas de valeur : on nomme le critère
        // plutôt que d'écrire « nuit =  » suivi de rien.
        DescripteurFiltre filtres = new DescripteurFiltre("", List.of(new DescripteurCritere("nuit", List.of())));

        assertThat(LegendeExportActivite.reglages(30, filtres)).isEqualTo("Tranche 30 min · Filtres : nuit");
    }

    @Test
    void les_reglages_disent_la_tranche_et_les_filtres_en_clair() {
        DescripteurFiltre filtres =
                new DescripteurFiltre("kuhl", List.of(new DescripteurCritere("nuit", List.of("2026-06-21"))));

        assertThat(LegendeExportActivite.reglages(30, filtres))
                .isEqualTo("Tranche 30 min · Filtres : recherche « kuhl », nuit = 2026-06-21");
    }

    @Test
    void sans_filtre_les_reglages_le_disent_plutot_que_de_se_taire() {
        DescripteurFiltre aucun = new DescripteurFiltre("", List.of());

        assertThat(LegendeExportActivite.reglages(15, aucun))
                .as("un silence se lirait comme « total non filtré » ou comme une information manquante")
                .isEqualTo("Tranche 15 min · Filtres : aucun filtre");
    }

    @Test
    void la_provenance_porte_la_version_et_la_date() {
        assertThat(LegendeExport.provenance("1.4.0", LocalDate.of(2026, 7, 26)))
                .isEqualTo("VigieChiro Companion 1.4.0 · exporté le 2026-07-26");
    }
}
