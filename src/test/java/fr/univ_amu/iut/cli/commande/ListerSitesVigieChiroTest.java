package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.cli.GesteAttenduCli;
import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.LotPagine;
import fr.univ_amu.iut.commun.api.PointVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SiteVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `lister-sites-vigiechiro` : ce qu'elle affiche, ce qu'elle compte, et surtout **ce qu'elle avoue**.
///
/// Le test central est celui du dénominateur : une lecture partielle doit se dire partielle. Sans lui,
/// la commande pourrait annoncer trois pages comme la plateforme entière - le défaut de #1277, cette
/// fois-ci fabriqué à la main.
class ListerSitesVigieChiroTest {

    private final ClientVigieChiro client = mock(ClientVigieChiro.class);
    private final StringWriter sortie = new StringWriter();
    private final StringWriter erreur = new StringWriter();

    @AfterEach
    void nettoyer() {
        System.clearProperty("vigiechiro.token");
    }

    private int executer(String... args) {
        CommandLine ligne = new CommandLine(new ListerSitesVigieChiro(client));
        // `Cli.executer` pose ce drapeau ; une CommandLine montée à la main ne l'a pas, et « --portee
        // plateforme » échouerait alors sur la casse, pour une raison sans rapport avec le test.
        ligne.setCaseInsensitiveEnumValuesAllowed(true);
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreur, true));
        // Le handler qui traduit un refus métier en code 2 vit dans `Cli` : sans lui, picocli rendrait
        // son code générique (1) et le test mesurerait le harnais, pas la commande. On rejoue donc ici
        // la même traduction, en affichant le refus comme la CLI le fait - par le GESTE attendu.
        ligne.setExecutionExceptionHandler((exception, commande, parse) -> {
            if (exception instanceof RegleMetierException refus) {
                commande.getErr().println("Refus : " + GesteAttenduCli.message(refus));
                return 2;
            }
            throw exception;
        });
        return ligne.execute(args);
    }

    private static SiteVigieChiro site(String id, String carre, String... points) {
        List<PointVigieChiro> localites = new ArrayList<>();
        for (String point : points) {
            localites.add(new PointVigieChiro(point, 43.5, 5.4));
        }
        return new SiteVigieChiro(id, "Vigiechiro - Point Fixe-" + carre, false, carre, "u-1", localites);
    }

    @Test
    @DisplayName("Collection complète : la sortie le dit, sans parler d'échantillon")
    void collection_complete() {
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(List.of(site("s1", "130711", "Z1")), 1, 1, true)));

        int code = executer("--portee", "plateforme", "--tout");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("130711")
                .contains("collection complète")
                .doesNotContain("Échantillon");
    }

    @Test
    @DisplayName("Lecture partielle : le dénominateur apparaît, et le mot « échantillon » avec lui")
    void lecture_partielle_dit_son_denominateur() {
        // LE test du lot : trois pages lues sur deux cent six, annoncées comme telles. Sans cette ligne,
        // un utilisateur (ou un script) prendrait 300 sites pour la plateforme entière.
        List<SiteVigieChiro> lus = List.of(site("s1", "130711", "Z1"), site("s2", "010434", "Z1"));
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(lus, 20517, 3, false)));

        executer("--portee", "plateforme", "--pages", "3");

        assertThat(sortie.toString())
                .contains("2 site(s) lu(s) sur 20517 annoncés")
                .contains("3 page(s) sur 206")
                .contains("Échantillon");
    }

    @Test
    @DisplayName("--recenser compte les sites par code de point, et la part porte sur ce qui a été lu")
    void recensement_compte_les_sites_par_code() {
        // Reproduction, en test, de la mesure qui a motivé le chantier : un code porté par presque tous
        // les sites lus.
        List<SiteVigieChiro> lus = new ArrayList<>();
        for (int numero = 0; numero < 4; numero++) {
            lus.add(site("s" + numero, "13071" + numero, "Z1"));
        }
        lus.add(site("s9", "999999", "Z2"));
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(lus, 20517, 1, false)));

        executer("--portee", "plateforme", "--recenser");

        assertThat(sortie.toString()).contains("Z1").contains("80,0 %").contains("Z2");
    }

    @Test
    @DisplayName("Le recensement classe du plus partagé au moins partagé : c'est ce qu'on vient y chercher")
    void recensement_est_classe_par_partage_decroissant() {
        // Sans tri, la réponse existe mais ne se lit pas : sur des dizaines de codes, on cherche
        // précisément celui qui revient partout.
        List<SiteVigieChiro> lus = List.of(
                site("s1", "130711", "Z9"),
                site("s2", "010434", "Z1"),
                site("s3", "010435", "Z1"),
                site("s4", "010436", "Z1"));
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(lus, 20517, 1, false)));

        executer("--portee", "plateforme", "--recenser");

        String texte = sortie.toString();
        assertThat(texte.indexOf("Z1"))
                .as("Z1 (3 sites) doit précéder Z9 (1 site)")
                .isLessThan(texte.indexOf("Z9"));
    }

    @Test
    @DisplayName("Un code de point vide ne compte pas : il n'est le nom de rien")
    void code_vide_nest_pas_recense() {
        List<SiteVigieChiro> lus = List.of(new SiteVigieChiro(
                "s1",
                "Site-130711",
                false,
                "130711",
                "u-1",
                List.of(new PointVigieChiro("  ", 43.5, 5.4), new PointVigieChiro("Z1", 43.5, 5.4))));
        when(client.sitesPlateforme(anyInt(), any())).thenReturn(ReponseApi.succes(new LotPagine<>(lus, 1, 1, true)));

        executer("--portee", "plateforme", "--recenser");

        assertThat(RecensementPoints.de(lus))
                .as("seul « Z1 » est un code ; une chaîne blanche n'en est pas un")
                .extracting(RecensementPoints.Ligne::code)
                .containsExactly("Z1");
    }

    @Test
    @DisplayName("Les sites sans point ponctuel (transects) sont comptés à part, jamais passés sous silence")
    void les_sites_sans_point_sont_annonces() {
        List<SiteVigieChiro> lus = List.of(site("s1", "130711", "Z1"), site("s2", "010434"));
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(lus, 20517, 1, false)));

        executer("--portee", "plateforme", "--recenser");

        assertThat(sortie.toString())
                .as("un recensement qui ignorerait ces sites sans le dire sous-compterait en silence")
                .contains("1 site(s) sans point d'écoute ponctuel");
    }

    @Test
    @DisplayName("La part ne se dilue pas dans les transects : elle porte sur les sites qui peuvent porter un point")
    void la_part_exclut_les_sites_sans_point() {
        // Mesuré sur le catalogue réel : 219 des 300 sites lus sont des transects routiers. Les garder
        // au dénominateur annonçait « Z1 : 24,3 % » là où la proportion est de 90,1 % parmi les sites
        // qui pouvaient le porter. L'erreur allait dans le sens rassurant, sur le chiffre même que ce
        // comptage existe pour produire (#2993).
        List<SiteVigieChiro> lus = List.of(site("s1", "130711", "Z1"), site("s2", "010434"));
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(lus, 20517, 1, false)));

        executer("--portee", "plateforme", "--recenser");

        assertThat(sortie.toString())
                .as("un site sans point ponctuel ne peut pas porter Z1 : il n'a pas à diviser la part")
                .contains("100,0 %")
                .doesNotContain("50,0 %");
        assertThat(sortie.toString())
                .as("et le bilan dit sur quoi la part porte, pour qu'on ne le devine pas")
                .contains("les parts portent sur les 1 autre(s)");
    }

    @Test
    @DisplayName("--json rend une enveloppe qui porte ce qui a été lu, pas un tableau nu")
    void json_est_une_enveloppe() {
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(List.of(site("s1", "130711", "Z1")), 20517, 3, false)));

        executer("--portee", "plateforme", "--pages", "3", "--json");

        assertThat(sortie.toString())
                .as("un script qui lit la sortie standard doit y trouver de quoi savoir que c'est partiel")
                .contains("\"complet\": false")
                .contains("\"totalAnnonce\": 20517")
                .contains("\"pagesLues\": 3")
                .contains("\"sites\"");
    }

    @Test
    @DisplayName("--point filtre chez nous, et le dénominateur reste celui de ce qui a été lu")
    void filtre_client_conserve_le_denominateur() {
        List<SiteVigieChiro> lus = List.of(site("s1", "130711", "Z1"), site("s2", "010434", "Z9"));
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(lus, 20517, 1, false)));

        executer("--portee", "plateforme", "--point", "z1");

        assertThat(sortie.toString())
                .as("le filtre est insensible à la casse, et il ne change pas ce qui a été lu")
                .contains("130711")
                .doesNotContain("010434");
        assertThat(sortie.toString()).contains("1 site(s) retenu(s) sur 2 site(s) lu(s)");
    }

    @Test
    @DisplayName("Sans jeton : refus motivé (code 2), et le serveur n'est pas sollicité pour rien")
    void non_connecte_refuse_avec_le_geste() {
        when(client.sitesPlateforme(anyInt(), any())).thenReturn(ReponseApi.nonConnecte());

        int code = executer("--portee", "plateforme");

        assertThat(code).isEqualTo(2);
        assertThat(erreur.toString()).contains("Non connecté").contains("jeton");
    }

    @Test
    @DisplayName("Serveur injoignable ou refus : code 2, la cause citée")
    void injoignable_et_refus_sont_des_refus() {
        when(client.sitesPlateforme(anyInt(), any())).thenReturn(ReponseApi.injoignable("délai dépassé"));
        assertThat(executer("--portee", "plateforme")).isEqualTo(2);
        assertThat(erreur.toString()).contains("injoignable").contains("délai dépassé");

        when(client.sitesPlateforme(anyInt(), any())).thenReturn(ReponseApi.refuse(422, "max_results"));
        assertThat(executer("--portee", "plateforme")).isEqualTo(2);
        assertThat(erreur.toString()).contains("422");
    }

    @Test
    @DisplayName("Zéro site est une réponse, pas un échec : code 0")
    void collection_vide_reste_un_succes() {
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(List.of(), 0, 0, true)));

        assertThat(executer("--portee", "plateforme")).isZero();
    }

    @Test
    @DisplayName("--portee mes lit vos sites et ne touche pas au catalogue")
    void portee_mes_ne_lit_pas_le_catalogue() {
        when(client.mesSites()).thenReturn(ReponseApi.succes(List.of(site("s1", "130711", "Z1"))));

        int code = executer("--portee", "mes");

        assertThat(code).isZero();
        verify(client, never()).sitesPlateforme(anyInt(), any());
        assertThat(sortie.toString()).contains("collection complète");
    }

    @Test
    @DisplayName("--token pose le jeton pour la durée de la commande")
    void token_est_pose() {
        when(client.sitesPlateforme(anyInt(), any()))
                .thenReturn(ReponseApi.succes(new LotPagine<>(List.of(), 0, 0, true)));

        executer("--portee", "plateforme", "--token", "ABC123");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("ABC123");
    }

    @Test
    @DisplayName("#3769 : « --carre » demande au SERVEUR, et ne lit plus deux cents pages")
    void carre_interroge_le_serveur() {
        when(client.chercherCarre("130711")).thenReturn(ReponseApi.succes(List.of(site("s1", "130711", "Z41"))));

        int code = executer("--portee", "PLATEFORME", "--carre", "130711");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("130711");
        // Le catalogue n'est plus parcouru : c'était une à deux minutes pour une question que le serveur
        // tranche en une requête (mesuré le 2026-08-14, six GET consignés sur #3458).
        verify(client, never()).sitesPlateforme(anyInt(), any());
    }

    @Test
    @DisplayName("#3769 : le faux « aucun » disparaît : un carré existant n'est plus manqué")
    void un_carre_existant_n_est_plus_manque() {
        // Avant : sans « --tout », la commande lisait UNE page sur 208 puis filtrait chez elle. Le carré
        // 130711, qui existe, n'était pas dans cette page : le tableau sortait vide. La ligne de bilan
        // l'avouait, mais qui lit le tableau y lit « ce carré n'existe pas ».
        when(client.chercherCarre("130711")).thenReturn(ReponseApi.succes(List.of(site("s1", "130711", "Z41"))));

        executer("--portee", "PLATEFORME", "--carre", "130711");

        assertThat(sortie.toString())
                .as("le carré cherché est là, sans qu'on ait eu à demander --tout")
                .contains("130711");
    }

    @Test
    @DisplayName("#3769 : « --tout » avec « --carre » est REFUSÉ, plutôt qu'accepté puis ignoré")
    void tout_avec_carre_est_refuse() {
        int code = executer("--portee", "PLATEFORME", "--carre", "130711", "--tout");

        // Un paramètre honoré en apparence et ignoré en fait est exactement ce que ce dépôt reproche à
        // `where=`. La recherche par carré est complète par construction : le dire vaut mieux que de
        // laisser croire qu'on a demandé quelque chose.
        assertThat(code).isNotZero();
        assertThat(erreur.toString()).contains("--carre").contains("--tout");
        verify(client, never()).sitesPlateforme(anyInt(), any());
    }

    @Test
    @DisplayName("#3769 : le bilan dit que la réponse vient d'une RECHERCHE, pas d'un catalogue lu")
    void le_bilan_dit_qu_il_s_agit_d_une_recherche() {
        when(client.chercherCarre("130711")).thenReturn(ReponseApi.succes(List.of(site("s1", "130711", "Z41"))));

        executer("--portee", "PLATEFORME", "--carre", "130711");

        // « 1 site lu : collection complète » serait exact et pourtant trompeur : on n'a pas lu la
        // collection, on a posé une question au serveur. Le dénominateur d'une recherche est celui de la
        // recherche - c'est la garde que #1277 a laissée derrière elle, et qu'on ne perd pas en route.
        assertThat(sortie.toString()).containsIgnoringCase("recherche").doesNotContain("collection complète");
    }
}
