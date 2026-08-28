package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le carroyage national embarqué (#4621), descendu du paquet de vue où il vivait depuis #325.
@DisplayName("Carroyage national embarqué (#4621)")
class CarroyageNationalTest {

    @Test
    @DisplayName("le centroïde d'un carré connu est celui du référentiel carrenat")
    void centroide_d_un_carre_connu() {
        PositionGeo centre = CarroyageNational.embarque().centroide("640380").orElseThrow();

        // Centroïde carrenat réel de 640380 (Pyrénées-Atlantiques), converti L2é → WGS84.
        assertThat(centre.latitude()).isCloseTo(43.403072, within(1e-4));
        assertThat(centre.longitude()).isCloseTo(-1.570834, within(1e-4));
    }

    @Test
    @DisplayName("le sens inverse : la position d'une nuit rend le carré qui la couvre, sans réseau")
    void position_vers_carre() {
        // Position mesurée le 2026-08-27 contre `GET /grille_stoc/cercle`, qui rendait « 040110 ».
        // Le référentiel ampute le zéro comme le serveur, et [NumeroDeCarre] le rétablit ici : les deux
        // sources rendent désormais la même forme, celle que R1 impose (#4577).
        var candidats = CarroyageNational.embarque().candidats(44.44674980384396, 6.298116860416506);

        assertThat(candidats).isNotEmpty();
        assertThat(candidats.getFirst().numero()).isEqualTo("040110");
        assertThat(candidats.getFirst().distanceMetres())
                .as("l'API mesurait ce point à moins de 500 m de son centre")
                .isCloseTo(374.9, within(1.0));
    }

    @Test
    @DisplayName("au milieu d'un côté commun, DEUX carrés sont candidats, et à distance quasi égale")
    void milieu_d_un_cote_rend_deux_candidats() {
        // Mesuré le 2026-08-27 : la plateforme rendait 997,7 m pour chacune des deux mailles. Sur une
        // frontière, « le plus proche » ne désigne rien : c'est ce que l'appelant doit dire.
        var candidats = CarroyageNational.embarque().candidats(44.444990, 6.306335);

        assertThat(candidats).hasSize(2);
        assertThat(candidats).extracting(CarreCandidat::numero).containsExactlyInAnyOrder("040110", "040111");
        assertThat(candidats.get(1).distanceMetres() - candidats.getFirst().distanceMetres())
                .as("les deux centres sont à quelques mètres l'un de l'autre en distance")
                .isLessThan(20.0);
    }

    @Test
    @DisplayName("à un coin, QUATRE carrés sont candidats, tous à environ 1 412 m")
    void coin_rend_quatre_candidats() {
        // Mesuré le 2026-08-27 : la plateforme rendait quatre mailles à 1 412 m. C'est le cas qui
        // impose la portée de 1 500 m : à 1 400, un point de coin ne rendrait rien.
        var candidats = CarroyageNational.embarque().candidats(44.453971, 6.306936);

        assertThat(candidats).hasSize(4);
        assertThat(candidats).allSatisfy(c -> assertThat(c.distanceMetres()).isCloseTo(1412.0, within(2.0)));
    }

    @Test
    @DisplayName("hors métropole, aucun candidat : le plus proche est à 1 233 km, pas un carré")
    void hors_metropole_ne_rend_aucun_candidat() {
        // Plein Atlantique. Sans portée, la maille la plus proche serait rendue et proposée : un numéro
        // parfaitement plausible, et faux de mille kilomètres.
        assertThat(CarroyageNational.embarque().candidats(45.0, -20.0)).isEmpty();
    }

    @Test
    @DisplayName("le centroïde se cherche sur SIX chiffres, la forme que R1 impose aux appelants")
    void centroide_se_cherche_sur_six_chiffres() {
        // Le référentiel ampute le zéro des départements 01 a 09, et `candidats` le rembourre déjà.
        // Sans le rembourrer ici aussi, la classe parlerait deux langues : ce qu'elle rend ne pourrait
        // pas lui être redonné. Trouvé à la passe 0 de la clôture du chantier #4573.
        assertThat(CarroyageNational.embarque().centroide("040110"))
                .as("un appelant tient un numéro à six chiffres : c'est la seule forme que R1 autorise")
                .isPresent();
    }

    @Test
    @DisplayName("une ligne au centroïde non numérique est ignorée, et le journal NOMME le carré fautif")
    void ligne_non_numerique_parle_dans_le_journal() throws Exception {
        // Le catch parle depuis #4619, et un message vide vaudrait un catch muet : c'est le jour où le
        // référentiel se dégrade qu'on lira cette ligne, et il doit dire QUEL carré.
        List<java.util.logging.LogRecord> captures = new java.util.ArrayList<>();
        Logger logger = Logger.getLogger(CarroyageNational.class.getName());
        Level niveauInitial = logger.getLevel();
        Handler sonde = new Handler() {
            @Override
            public void publish(java.util.logging.LogRecord enregistrement) {
                captures.add(enregistrement);
            }

            @Override
            public void flush() {
                // Rien à vider : la sonde accumule en mémoire.
            }

            @Override
            public void close() {
                // Rien à fermer.
            }
        };
        logger.addHandler(sonde);
        logger.setLevel(Level.FINE);
        try {
            CarroyageNational.depuis("010001;pas un nombre;0.0");
        } finally {
            logger.removeHandler(sonde);
            logger.setLevel(niveauInitial);
        }

        assertThat(captures).singleElement().satisfies(trace -> {
            assertThat(trace.getLevel()).isEqualTo(Level.FINE);
            assertThat(trace.getMessage())
                    .as("le message doit nommer le carré fautif, pas seulement signaler un incident")
                    .contains("010001");
        });
    }

    @Test
    @DisplayName("le référentiel couvre toute la métropole")
    void couverture_nationale() {
        assertThat(CarroyageNational.embarque().taille()).isGreaterThan(100_000);
    }

    @Test
    @DisplayName("les candidats sortent du plus proche au plus éloigné, et pas dans l'ordre de lecture")
    void candidats_ordonnes_par_distance() {
        // Écrites du plus LOIN au plus PRÈS : sans tri, l'ordre de lecture les rendrait à l'envers.
        // « Le premier » est ce que lit l'appelant pour proposer un carré : l'ordre est le contrat.
        CarroyageNational grille = CarroyageNational.depuis("""
                010003;45.0090;0.0
                010002;45.0045;0.0
                010001;45.0000;0.0
                """);

        var candidats = grille.candidats(45.0, 0.0);

        assertThat(candidats).extracting(CarreCandidat::numero).containsExactly("010001", "010002", "010003");
    }

    @Test
    @DisplayName("la portée encadre 1 500 m : 1 400 m est candidat, 1 600 m ne l'est plus")
    void portee_encadre_le_seuil() {
        // La borne EXACTE n'est pas atteignable : écrire un centre à « 45,0 plus 1500/111132 » puis lui
        // resoustraire 45,0 perd des chiffres par annulation, et la distance retombe à 1500,000000001.
        // C'est le fond du mutant « changed conditional boundary » que PIT laisse survivre ici : entre
        // « <= » et « < », rien ne les distingue sur des doubles. Ce qui se teste est la VALEUR du
        // seuil, et deux mailles l'encadrent.
        CarroyageNational grille = CarroyageNational.depuis("010001;" + (45.0 + 1_400.0 / 111_132.0) + ";0.0\n"
                + "010002;" + (45.0 + 1_600.0 / 111_132.0) + ";0.0");

        var candidats = grille.candidats(45.0, 0.0);

        assertThat(candidats).extracting(CarreCandidat::numero).containsExactly("010001");
    }

    @Test
    @DisplayName("la lecture écarte commentaires, en-tête, lignes courtes et colonnes non numériques")
    void lecture_ecarte_ce_qui_n_est_pas_une_maille() {
        CarroyageNational grille = CarroyageNational.depuis("""
                # un commentaire
                numero;lat;lon
                010001;45.0;0.0
                010002;45.0
                010003;pas un nombre;0.0

                """);

        assertThat(grille.taille()).isEqualTo(1);
        assertThat(grille.centroide("010001")).isPresent();
    }

    @Test
    @DisplayName("le flux gzip se lit ligne à ligne, comme la ressource embarquée")
    void lecture_d_un_flux_gzip() throws Exception {
        // Le porteur statique ne charge qu'une fois par JVM : sans ce point d'entrée, aucune mutation
        // du chargement ne pourrait être vue par un test.
        var octets = new java.io.ByteArrayOutputStream();
        try (var gz = new java.util.zip.GZIPOutputStream(octets)) {
            gz.write("# entete\n010001;45.0;0.0\n010002;45.0090;0.0\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        CarroyageNational grille = CarroyageNational.depuisGzip(new java.io.ByteArrayInputStream(octets.toByteArray()));

        assertThat(grille.taille()).isEqualTo(2);
        assertThat(grille.candidats(45.0, 0.0))
                .extracting(CarreCandidat::numero)
                .containsExactly("010001", "010002");
    }
}
