package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.importation.model.PassageExistant;
import fr.univ_amu.iut.importation.model.RapportImport;
import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.VolumesImport;
import fr.univ_amu.iut.passage.model.Passage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Rendu texte de la fin d'import en ligne de commande (#2656).
///
/// Trois lignes de cette sortie n'étaient exercées par **aucun** test : les volumes, la participation
/// créée, et leurs conditions. `CliImportTest` construit l'injecteur applicatif réel, sans passerelle de
/// synchronisation - `participationCreee()` y est donc toujours faux, et la ligne jamais imprimée.
///
/// Le trou était **déclaré** à la clôture de #2350 plutôt que laissé tacite. Il est comblé en extrayant
/// le rendu en fonction pure, comme sa voisine `deposer-vigiechiro` le fait depuis toujours.
class ImporterRenduBilanTest {

    private static final String QUADRUPLET = "carré 640380 / point Z1 / 2026 / passage 2";

    @Test
    @DisplayName("#1488 : la participation créée est dite - l'écran le fait, la commande le taisait")
    void la_participation_creee_est_dite() {
        String texte = Importer.rendreBilan(resultat(VolumesImport.AUCUN, true), QUADRUPLET);

        assertThat(texte)
                .as("une écriture sur un serveur distant ne doit pas se découvrir ailleurs")
                .contains("Vigie-Chiro : participation créée (le dépôt la réutilisera)");
    }

    @Test
    @DisplayName("#1488 : sans participation créée, la ligne n'existe pas - pas une ligne vide")
    void sans_participation_rien_n_est_dit() {
        String texte = Importer.rendreBilan(resultat(VolumesImport.AUCUN, false), QUADRUPLET);

        assertThat(texte).doesNotContain("Vigie-Chiro");
    }

    @Test
    @DisplayName("#2350 : les volumes lus et écrits sont dits, comme la bande de l'écran")
    void les_volumes_sont_dits() {
        String texte = Importer.rendreBilan(resultat(new VolumesImport(7_000_000, 0, 7_000_000), false), QUADRUPLET);

        assertThat(texte).contains("Lu / écrit  : 7,0 Mo lus sur la source, 7,0 Mo écrits");
    }

    @Test
    @DisplayName("#2677 : la part des bruts n'est dite que si elle existe, jamais « dont 0 Ko »")
    void la_part_des_bruts_n_est_dite_que_si_elle_existe() {
        String sansBruts =
                Importer.rendreBilan(resultat(new VolumesImport(7_000_000, 0, 7_000_000), false), QUADRUPLET);
        String avecBruts =
                Importer.rendreBilan(resultat(new VolumesImport(7_000_000, 3_000_000, 4_000_000), false), QUADRUPLET);

        assertThat(sansBruts)
                .as("annoncer une part à zéro fait chercher ce qui n'a pas eu lieu")
                .doesNotContain("bruts conservés");
        assertThat(avecBruts).contains("dont 3,0 Mo de bruts conservés");
    }

    @Test
    @DisplayName("volumes non mesurés : aucune ligne, plutôt qu'une ligne à zéro")
    void volumes_non_mesures_ne_sont_pas_dits() {
        String texte = Importer.rendreBilan(resultat(VolumesImport.AUCUN, false), QUADRUPLET);

        assertThat(texte).doesNotContain("Lu / écrit");
    }

    @Test
    @DisplayName("#2004 : un doublon de nuit et une anomalie du journal sont montrés, pas transportés")
    void doublons_et_anomalies_sont_montres() {
        ResultatImport resultat = new ResultatImport(
                passage(),
                null,
                "1925492",
                6,
                6,
                List.of("Tension faible"),
                new RapportImport(List.of(), List.of(new PassageExistant(7, 2026, "640380", "Z1"))),
                VolumesImport.AUCUN,
                false);

        String texte = Importer.rendreBilan(resultat, QUADRUPLET);

        assertThat(texte).contains("Doublon     : nuit déjà importée en passage n° 7");
        assertThat(texte).contains("Anomalie    : Tension faible");
    }

    private static ResultatImport resultat(VolumesImport volumes, boolean participationCreee) {
        return new ResultatImport(
                passage(), null, "1925492", 6, 6, List.of(), new RapportImport(List.of()), volumes, participationCreee);
    }

    private static Passage passage() {
        return new Passage(
                1L,
                2,
                2026,
                "2026-06-22",
                "20:25",
                "07:47",
                null,
                StatutWorkflow.TRANSFORME,
                null,
                null,
                null,
                null,
                7L,
                "1925492",
                null);
    }
}
