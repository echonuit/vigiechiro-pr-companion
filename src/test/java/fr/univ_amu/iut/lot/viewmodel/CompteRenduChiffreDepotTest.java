package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.viewmodel.CompteRenduChiffreDepot.Plan;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Traduction de la fin d'un dépôt en compte rendu chiffré (#2653).
///
/// Le dépôt a **trois fins** et non deux : complet, en échec, et **interrompu à la demande**. La
/// troisième est celle qui piège, parce que son bilan peut n'avoir aucun échec alors qu'il manque des
/// archives en ligne.
class CompteRenduChiffreDepotTest {

    @Test
    @DisplayName("dépôt complet : ventilation pleine, registre du succès, et l'étape suivante est dite")
    void depot_complet() {
        CompteRenduChiffre rendu = traduire(new BilanDepot("p-1", 14, List.of(), 4_500_000_000L), plan(14, 14, false));

        assertThat(rendu.resultat()).isEqualTo("14 déposées");
        assertThat(rendu.severite()).isEqualTo(Severite.SUCCES);
        assertThat(rendu.ventilation().segments()).extracting(Segment::libelle).containsExactly("Déposées");
        assertThat(textes(rendu))
                .as("« Lancer la participation » est l'étape ④, et rien ne la désignait à la fin du dépôt")
                .anyMatch(texte -> texte.contains("lancer la participation"));
    }

    @Test
    @DisplayName("#2653 : le volume téléversé est dit - aucune surface ne le disait")
    void le_volume_est_dit() {
        CompteRenduChiffre rendu = traduire(new BilanDepot("p-1", 14, List.of(), 4_500_000_000L), plan(14, 14, false));

        assertThat(rendu.volumes()).hasSize(1);
        assertThat(rendu.volumes().get(0).segments())
                .extracting(Segment::valeurLisible)
                .containsExactly("4,2 Go");
    }

    @Test
    @DisplayName("volume nul : aucune barre, plutôt qu'une barre à zéro")
    void volume_nul_n_affiche_pas_de_barre() {
        CompteRenduChiffre rendu = traduire(new BilanDepot("p-1", 0, List.of("Car-1.zip"), 0), plan(1, 0, false));

        assertThat(rendu.volumes()).isEmpty();
    }

    @Test
    @DisplayName("dépôt partiel : les échecs sont une erreur, et le remède est nommé")
    void depot_partiel() {
        BilanDepot bilan = new BilanDepot("p-1", 9, List.of("Car-10.zip", "Car-11.zip"), 3_000_000_000L);

        CompteRenduChiffre rendu = traduire(bilan, plan(11, 9, false));

        assertThat(rendu.resultat()).isEqualTo("9 / 11 déposées");
        assertThat(rendu.severite()).isEqualTo(Severite.ERREUR);
        assertThat(rendu.ventilation().segments()).extracting(Segment::libelle).containsExactly("Déposées", "En échec");
        assertThat(textes(rendu)).anyMatch(texte -> texte.contains("ne renverra que celles-là"));
    }

    @Test
    @DisplayName("dépôt INTERROMPU sans aucun échec : ne doit surtout pas se lire comme un succès")
    void depot_interrompu_n_est_pas_un_succes() {
        // Le piège de #1044 : la tentative n'a rien raté, l'utilisateur a arrêté. Le bilan est donc
        // « sans échec » alors qu'il manque 5 archives sur la plateforme.
        BilanDepot bilan = new BilanDepot("p-1", 9, List.of(), 3_000_000_000L);

        CompteRenduChiffre rendu = traduire(bilan, plan(14, 9, true));

        assertThat(rendu.severite())
                .as("ni un succès (des archives manquent), ni une erreur (rien n'a raté)")
                .isEqualTo(Severite.AVERTISSEMENT);
        assertThat(rendu.ventilation().segments())
                .as("la part « Restantes » est ce qui empêche la barre d'être pleine et verte")
                .extracting(Segment::libelle, Segment::quantite)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Déposées", 9L),
                        org.assertj.core.api.Assertions.tuple("Restantes", 5L));
        assertThat(textes(rendu)).anyMatch(texte -> texte.contains("5 archive(s) manquante(s)"));
        assertThat(textes(rendu))
                .as("rien n'est complet : la bonne nouvelle n'a pas sa place ici")
                .noneMatch(texte -> texte.contains("Toutes les archives"));
    }

    @Test
    @DisplayName("les archives en échec sont listées, avec renvoi à la table pour leur cause")
    void les_echecs_renvoient_a_la_table() {
        BilanDepot bilan = new BilanDepot("p-1", 9, List.of("Car-10.zip", "Car-11.zip"), 1L);

        CompteRenduChiffre rendu = traduire(bilan, plan(11, 9, false));

        assertThat(rendu.motifs()).hasSize(1);
        assertThat(rendu.motifs().get(0).sujets()).containsExactly("Car-10.zip", "Car-11.zip");
        assertThat(rendu.motifs().get(0).libelle())
                .as("le bilan ne porte pas les causes : elles vivent dans la table, et on y renvoie")
                .contains("table");
    }

    private static Plan plan(int unitesDuPlan, int enLigne, boolean interrompu) {
        return new Plan(unitesDuPlan, enLigne, interrompu);
    }

    private static CompteRenduChiffre traduire(BilanDepot bilan, Plan plan) {
        return CompteRenduChiffreDepot.de(bilan, plan, List.of());
    }

    private static List<String> textes(CompteRenduChiffre rendu) {
        return rendu.avertissements().stream().map(Avertissement::texte).toList();
    }
}
