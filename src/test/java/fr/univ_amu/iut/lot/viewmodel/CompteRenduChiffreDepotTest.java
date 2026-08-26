package fr.univ_amu.iut.lot.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Severite;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Avertissement;
import fr.univ_amu.iut.commun.viewmodel.CompteRenduChiffre.Segment;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.CauseRefus;
import fr.univ_amu.iut.lot.model.EchecUnite;
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

        assertThat(textes(rendu)).anyMatch(texte -> texte.contains("4,5 Go téléversés"));
    }

    @Test
    @DisplayName("le volume est une MENTION, jamais une barre : seule, une barre est toujours pleine")
    void le_volume_n_est_pas_une_barre() {
        // Sur un dépôt interrompu, une barre unique afficherait 2,9 Go à 100 % alors qu'il en manque
        // autant. L'import peut se le permettre : il en a DEUX, à échelle commune, et comparer le lu à
        // l'écrit veut dire quelque chose.
        CompteRenduChiffre rendu = traduire(new BilanDepot("p-1", 9, List.of(), 2_900_000_000L), plan(14, 9, true));

        assertThat(rendu.volumes()).isEmpty();
        assertThat(textes(rendu)).anyMatch(texte -> texte.contains("2,9 Go téléversés"));
    }

    @Test
    @DisplayName("volume nul : rien n'est dit, plutôt qu'un « 0 Ko téléversés »")
    void volume_nul_n_est_pas_annonce() {
        CompteRenduChiffre rendu = traduire(
                new BilanDepot("p-1", 0, List.of(EchecUnite.rejouable("Car-1.zip", "HTTP 503")), 0), plan(1, 0, false));

        assertThat(rendu.volumes()).isEmpty();
        assertThat(textes(rendu)).noneMatch(texte -> texte.contains("téléversés"));
    }

    @Test
    @DisplayName("dépôt partiel : les échecs sont une erreur, et le remède est nommé")
    void depot_partiel() {
        BilanDepot bilan = new BilanDepot(
                "p-1",
                9,
                List.of(EchecUnite.rejouable("Car-10.zip", "HTTP 503"), EchecUnite.rejouable("Car-11.zip", "HTTP 503")),
                3_000_000_000L);

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
        BilanDepot bilan = new BilanDepot(
                "p-1",
                9,
                List.of(EchecUnite.rejouable("Car-10.zip", "HTTP 503"), EchecUnite.rejouable("Car-11.zip", "HTTP 503")),
                1L);

        CompteRenduChiffre rendu = traduire(bilan, plan(11, 9, false));

        assertThat(rendu.motifs()).hasSize(1);
        assertThat(rendu.motifs().get(0).sujets()).containsExactly("Car-10.zip", "Car-11.zip");
        assertThat(rendu.motifs().get(0).libelle())
                .as("le bilan ne porte pas les causes : elles vivent dans la table, et on y renvoie")
                .contains("table");
    }

    @Test
    @DisplayName("#3962 : un refus définitif ne se voit plus promettre « Reprendre le dépôt »")
    void un_refus_definitif_n_est_plus_promis() {
        // Dans cet état, le bouton de l'écran s'intitule « Téléverser sur Vigie-Chiro » (#3687) : citer
        // « Reprendre le dépôt » nommait un bouton absent de la vue.
        BilanDepot bilan = new BilanDepot(
                "p-1", 9, List.of(new EchecUnite("Car-10.zip", "HTTP 422", true, CauseRefus.CONTENU)), 3_000_000_000L);

        List<String> textes = textes(traduire(bilan, plan(10, 9, false)));

        assertThat(textes)
                .as("le compte rendu promet encore une reprise que le produit n'offre pas")
                .noneMatch(texte -> texte.contains("Reprendre le dépôt"));
        assertThat(textes).anyMatch(texte -> texte.contains("refusées par Vigie-Chiro"));
    }

    @Test
    @DisplayName("#3962 : la reconnexion n'est conseillée que si elle peut lever la cause")
    void le_geste_conseille_est_verifie() {
        BilanDepot droits = new BilanDepot(
                "p-1",
                9,
                List.of(new EchecUnite("Car-10.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION)),
                3_000_000_000L);
        BilanDepot contenu = new BilanDepot(
                "p-1", 9, List.of(new EchecUnite("Car-11.zip", "HTTP 422", true, CauseRefus.CONTENU)), 3_000_000_000L);

        assertThat(textes(traduire(droits, plan(10, 9, false))))
                .as("des droits refusés : une reconnexion les répare, on le dit")
                .anyMatch(texte -> texte.contains("Reconnectez-vous"));
        assertThat(textes(traduire(contenu, plan(10, 9, false))))
                .as("un contenu refusé : se reconnecter n'y changerait rien, on ne le conseille pas")
                .noneMatch(texte -> texte.contains("Reconnectez-vous"));
        // Mais on ne se tait pas pour autant. La mesure de #3946 établit que régénérer puis relancer
        // fait bien repartir l'unité : le geste est vérifié, donc il se nomme (ADR 3854). Dire ce qui
        // ne marche pas sans dire ce qui marche laisse l'utilisateur devant un mur.
        assertThat(textes(traduire(contenu, plan(10, 9, false))))
                .anyMatch(texte -> texte.contains("Régénérez les archives de la nuit"));
    }

    @Test
    @DisplayName("#3962 : reprenables et refusés se comptent séparément, et se disent tous deux")
    void les_deux_familles_se_disent() {
        BilanDepot bilan = new BilanDepot(
                "p-1",
                8,
                List.of(
                        EchecUnite.rejouable("Car-10.zip", "HTTP 503"),
                        new EchecUnite("Car-11.zip", "HTTP 422", true, CauseRefus.CONTENU)),
                3_000_000_000L);

        List<String> textes = textes(traduire(bilan, plan(10, 8, false)));

        assertThat(textes).anyMatch(texte -> texte.contains("1 archive(s) ne sont pas en ligne"));
        assertThat(textes).anyMatch(texte -> texte.contains("1 archive(s) ont été refusées"));
    }

    @Test
    @DisplayName("#3962 : un dépôt auquel il manque des archives ne s'intitule pas « Nuit déposée »")
    void le_titre_dit_l_etat_reel() {
        // Trouvé en ouvrant l'aperçu, pas en lisant le code : le titre valait « Nuit déposée sur
        // Vigie-Chiro » dès que le plan n'était pas interrompu, en gras, au-dessus d'une ventilation qui
        // disait 11/14. La CLI dit « INCOMPLET » pour le même état.
        BilanDepot incomplet = new BilanDepot(
                "p-1", 11, List.of(new EchecUnite("Car-14.zip", "HTTP 422", true, CauseRefus.CONTENU)), 3_400_000_000L);
        BilanDepot complet = new BilanDepot("p-1", 14, List.of(), 4_500_000_000L);

        assertThat(traduire(incomplet, plan(14, 11, false)).titre()).isEqualTo("Dépôt incomplet");
        assertThat(traduire(complet, plan(14, 14, false)).titre()).isEqualTo("Nuit déposée sur Vigie-Chiro");
    }

    @Test
    @DisplayName("#3962 : sur un lot mêlé, la reconnexion est nommée pour la part qu'elle répare")
    void le_geste_se_nomme_pour_la_part_qu_il_repare() {
        BitmapMele mele = new BitmapMele();
        List<String> textes = textes(traduire(mele.bilan(), plan(14, 11, false)));

        assertThat(textes)
                .as("deux des trois refus tenaient aux droits : se taire perd un geste vérifié")
                .anyMatch(texte -> texte.contains("2 d'entre elles tenaient à vos droits"));
    }

    /// Le lot que l'aperçu montre : deux refus de droits, un contenu refusé.
    private record BitmapMele() {
        BilanDepot bilan() {
            return new BilanDepot(
                    "p-1",
                    11,
                    List.of(
                            new EchecUnite("Car-12.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION),
                            new EchecUnite("Car-13.zip", "HTTP 403", true, CauseRefus.AUTHENTIFICATION),
                            new EchecUnite("Car-14.zip", "HTTP 422", true, CauseRefus.CONTENU)),
                    3_400_000_000L);
        }
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

    @Test
    @DisplayName("plan inconnu : le total se reconstitue de ce que la tentative a vu passer")
    void plan_inconnu_le_total_se_reconstitue_de_la_tentative() {
        BilanDepot bilan = new BilanDepot(
                "p-1",
                9,
                List.of(EchecUnite.rejouable("Car-10.zip", "HTTP 503"), EchecUnite.rejouable("Car-11.zip", "HTTP 503")),
                1L);

        // `unitesDuPlan = 0` : le plan n'a pas été relu. Le total reste vrai parce qu'il additionne
        // ce qui est en ligne ET ce qui a échoué - une soustraction rendrait « 7 sur 5 ».
        CompteRenduChiffre rendu = traduire(bilan, plan(0, 7, false));

        assertThat(rendu.resultat()).isEqualTo("7 / 9 déposées");
    }

    @Test
    @DisplayName("un dépôt vide ne s'annonce pas comme entièrement en ligne")
    void un_depot_vide_ne_s_annonce_pas_comme_en_ligne() {
        BilanDepot bilan = new BilanDepot("p-1", 0, List.of(), 0L);

        CompteRenduChiffre rendu = traduire(bilan, plan(0, 0, false));

        // 0 == 0 est vrai, et pourtant il n'y a rien en ligne : sans le garde sur le total, le succès
        // « Toutes les archives sont sur Vigie-Chiro » s'afficherait sur un dépôt qui n'a rien déposé.
        assertThat(textes(rendu)).noneMatch(texte -> texte.contains("Toutes les archives"));
    }

    @Test
    @DisplayName("un dépôt qui a échoué quelque part ne se dit pas complet")
    void un_depot_avec_echec_ne_se_dit_pas_complet() {
        assertThat(new BilanDepot("p-1", 9, List.of(EchecUnite.rejouable("Car-10.zip", "HTTP 503")), 1L).estComplet())
                .isFalse();
        assertThat(new BilanDepot("p-1", 9, List.of(), 1L).estComplet()).isTrue();
    }

    @Test
    @DisplayName("le titre dit la fin qu'on a eue : un dépôt interrompu ne s'intitule pas « nuit déposée »")
    void le_titre_dit_la_fin_qu_on_a_eue() {
        BilanDepot bilan = new BilanDepot("p-1", 9, List.of(), 1L);

        // Le titre est la première chose lue, avant les barres : s'il annonce une nuit déposée alors que
        // l'utilisateur a interrompu, le reste du panneau argumente contre son propre en-tête.
        assertThat(traduire(bilan, plan(9, 4, true)).titre()).isEqualTo("Dépôt interrompu");
        assertThat(traduire(bilan, plan(9, 9, false)).titre()).isEqualTo("Nuit déposée sur Vigie-Chiro");
    }
}
