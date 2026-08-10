package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le choix du régime de restauration, **sans toucher au disque** (#3563).
///
/// C'est la pièce pure du dispositif : le manifeste porte les octets, le port porte la place, et cette
/// classe n'en tire qu'une décision. Elle se mesure donc en entier, bornes comprises - un `>=` changé
/// en `>` doit faire tomber un test nommé.
class BesoinDePlaceTest {

    private static final Path TRAVAIL = Path.of("/home/moi/VigieChiro-Companion");
    private static final Path DISQUE = Path.of("/media/disque-terrain");

    @Test
    @DisplayName("la place suffit pour tout étaler : régime d'ensemble")
    void tout_tient() {
        BesoinDePlace besoin = surUnSeulDossier(30, 70);

        assertThat(besoin.regimePour(dossier -> 100)).isEqualTo(RegimeRestauration.ENSEMBLE);
    }

    @Test
    @DisplayName("la place vaut exactement le total : encore le régime d'ensemble")
    void la_borne_du_total_est_inclusive() {
        BesoinDePlace besoin = surUnSeulDossier(30, 70);

        assertThat(besoin.regimePour(dossier -> 100))
                .as("exiger un octet de plus que le besoin dégraderait sans raison")
                .isEqualTo(RegimeRestauration.ENSEMBLE);
        assertThat(besoin.regimePour(dossier -> 99))
                .as("un octet de moins, et on ne peut plus tout étaler")
                .isEqualTo(RegimeRestauration.RACINE_PAR_RACINE);
    }

    @Test
    @DisplayName("la place vaut exactement la plus grosse nuit : dégradé, pas refusé")
    void la_borne_de_la_plus_grosse_est_inclusive() {
        BesoinDePlace besoin = surUnSeulDossier(30, 70);

        assertThat(besoin.regimePour(dossier -> 70))
                .as("elle tient tout juste : refuser ici serait la rigidité que l'ADR 2727 reprochait")
                .isEqualTo(RegimeRestauration.RACINE_PAR_RACINE);
    }

    @Test
    @DisplayName("en dessous de la plus grosse nuit : refus, et le message chiffre ce qui manque")
    void refus_chiffre() {
        BesoinDePlace besoin = surUnSeulDossier(30, 70);

        assertThatThrownBy(() -> besoin.regimePour(dossier -> 69))
                .isInstanceOf(RefusAvantEcriture.class)
                .as("« espace insuffisant » n'aide personne : il faut savoir combien libérer")
                .hasMessageContaining("Libérez")
                .hasMessageContaining("Rien n'a été touché");
    }

    @Test
    @DisplayName("le manque annoncé n'est jamais nul : « Libérez 0 Ko » serait un refus absurde")
    void le_manque_annonce_n_est_jamais_nul() {
        BesoinDePlace besoin = surUnSeulDossier(30, 70);

        assertThatThrownBy(() -> besoin.regimePour(dossier -> 69))
                .as("le formateur tronque au kilo-octet, et un manque de quelques centaines d'octets"
                        + " demanderait de libérer rien du tout")
                .hasMessageContaining("Libérez 1 Ko");
    }

    @Test
    @DisplayName("le manque annoncé est la différence, pas une autre opération")
    void le_manque_annonce_est_la_difference() {
        // Aux valeurs minuscules des tests voisins, le plancher de 1 Ko masque l'arithmétique : une
        // addition rendrait le même libellé qu'une soustraction. PIT l'a montré ; il faut donc un
        // manque franchement au-dessus du kilo-octet.
        BesoinDePlace besoin =
                new BesoinDePlace(Map.of(TRAVAIL, new BesoinDePlace.Besoin(20L * 1024 * 1024, 5L * 1024 * 1024)));

        assertThatThrownBy(() -> besoin.regimePour(dossier -> 1024L * 1024)).hasMessageContaining("Libérez 4 Mo");
    }

    @Test
    @DisplayName("un dossier d'accueil plein suffit à dégrader, même si l'autre est vaste")
    void chaque_dossier_d_accueil_compte() {
        // Une nuit revient sur son disque externe, l'autre atterrit dans le dossier de travail. Un
        // total unique confronté à la seule place du dossier de travail annoncerait « tout tient ».
        BesoinDePlace besoin = new BesoinDePlace(Map.of(
                TRAVAIL, new BesoinDePlace.Besoin(10, 10),
                DISQUE, new BesoinDePlace.Besoin(100, 60)));

        assertThat(besoin.regimePour(dossier -> dossier.equals(TRAVAIL) ? 1_000_000 : 60))
                .as("le disque externe ne peut étaler qu'une nuit : c'est lui qui commande")
                .isEqualTo(RegimeRestauration.RACINE_PAR_RACINE);
    }

    @Test
    @DisplayName("place illisible : on refuse plutôt que de tenter à l'aveugle")
    void place_illisible_refuse() {
        BesoinDePlace besoin = surUnSeulDossier(30, 70);

        assertThatThrownBy(() -> besoin.regimePour(dossier -> {
                    throw new IOException("système de fichiers muet");
                }))
                .as("le port le dit : un appelant sur le point d'écrire refuse plutôt que d'échouer à"
                        + " mi-parcours en laissant des fichiers partiels")
                .isInstanceOf(RefusAvantEcriture.class)
                .hasMessageContaining("Rien n'a été touché");
    }

    @Test
    @DisplayName("le besoin se lit dans le manifeste, groupé par dossier d'accueil")
    void le_besoin_se_lit_dans_le_manifeste() {
        ManifesteSauvegarde manifeste = manifeste(
                new RacineSauvegardee("a", DISQUE + "/Nuit-01", 1, 40, "x"),
                new RacineSauvegardee("b", DISQUE + "/Nuit-02", 1, 60, "y"),
                new RacineSauvegardee("c", TRAVAIL + "/Nuit-03", 1, 5, "z"));

        BesoinDePlace besoin = BesoinDePlace.de(manifeste, origine -> origine);

        assertThat(besoin.parDossierDAccueil())
                .containsEntry(DISQUE, new BesoinDePlace.Besoin(100, 60))
                .containsEntry(TRAVAIL, new BesoinDePlace.Besoin(5, 5));
    }

    @Test
    @DisplayName("une sauvegarde sans aucune nuit ne demande rien, même sur un disque plein")
    void aucune_nuit_ne_demande_rien() {
        BesoinDePlace besoin = BesoinDePlace.de(manifeste(), origine -> origine);

        assertThat(besoin.regimePour(dossier -> 0))
                .as("refuser une restauration qui n'a rien à replacer serait absurde : il n'y a pas de"
                        + " dossier d'accueil, donc pas de place à trouver")
                .isEqualTo(RegimeRestauration.ENSEMBLE);
    }

    private static BesoinDePlace surUnSeulDossier(long petite, long grosse) {
        return new BesoinDePlace(Map.of(TRAVAIL, new BesoinDePlace.Besoin(petite + grosse, grosse)));
    }

    private static ManifesteSauvegarde manifeste(RacineSauvegardee... racines) {
        return new ManifesteSauvegarde(1, List.of(racines));
    }
}
