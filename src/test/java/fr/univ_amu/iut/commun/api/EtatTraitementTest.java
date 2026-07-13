package fr.univ_amu.iut.commun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture tolérante des états de traitement du serveur, et sémantique des trois questions que
/// l'application leur pose : « puis-je importer ? », « dois-je attendre ? », « est-ce en échec ? » (#1260).
class EtatTraitementTest {

    @Test
    @DisplayName("depuis : les 5 états du backend sont reconnus, sans sensibilité à la casse ni aux espaces")
    void depuis_etats_connus() {
        assertThat(EtatTraitement.depuis("PLANIFIE")).contains(EtatTraitement.PLANIFIE);
        assertThat(EtatTraitement.depuis("EN_COURS")).contains(EtatTraitement.EN_COURS);
        assertThat(EtatTraitement.depuis("FINI")).contains(EtatTraitement.FINI);
        assertThat(EtatTraitement.depuis("ERREUR")).contains(EtatTraitement.ERREUR);
        assertThat(EtatTraitement.depuis("RETRY")).contains(EtatTraitement.RETRY);

        assertThat(EtatTraitement.depuis(" fini ")).contains(EtatTraitement.FINI);
    }

    @Test
    @DisplayName("depuis : absent, vide ou inconnu → vide, jamais d'exception")
    void depuis_tolerante() {
        // Une participation jamais calculée n'a pas de bloc traitement ; le serveur peut aussi
        // introduire un état que cette version de l'application ignore. Dans les deux cas : pas de crash.
        assertThat(EtatTraitement.depuis(null)).isEmpty();
        assertThat(EtatTraitement.depuis("")).isEmpty();
        assertThat(EtatTraitement.depuis("   ")).isEmpty();
        assertThat(EtatTraitement.depuis("ETAT_INEDIT")).isEmpty();
    }

    @Test
    @DisplayName("resultatsDisponibles : FINI seulement (les observations n'existent pas avant)")
    void resultats_disponibles() {
        assertThat(EtatTraitement.FINI.resultatsDisponibles()).isTrue();

        assertThat(EtatTraitement.PLANIFIE.resultatsDisponibles()).isFalse();
        assertThat(EtatTraitement.EN_COURS.resultatsDisponibles()).isFalse();
        assertThat(EtatTraitement.RETRY.resultatsDisponibles()).isFalse();
        assertThat(EtatTraitement.ERREUR.resultatsDisponibles()).isFalse();
    }

    @Test
    @DisplayName("enAttente : PLANIFIE, EN_COURS et RETRY (le serveur travaille, il n'y a qu'à patienter)")
    void en_attente() {
        assertThat(EtatTraitement.PLANIFIE.enAttente()).isTrue();
        assertThat(EtatTraitement.EN_COURS.enAttente()).isTrue();
        assertThat(EtatTraitement.RETRY.enAttente()).isTrue();

        assertThat(EtatTraitement.FINI.enAttente()).isFalse();
        assertThat(EtatTraitement.ERREUR.enAttente()).isFalse();
    }

    @Test
    @DisplayName("enEchec : ERREUR seulement, RETRY étant un échec rattrapé par le serveur")
    void en_echec() {
        assertThat(EtatTraitement.ERREUR.enEchec()).isTrue();

        assertThat(EtatTraitement.RETRY.enEchec()).isFalse();
        assertThat(EtatTraitement.FINI.enEchec()).isFalse();
        assertThat(EtatTraitement.PLANIFIE.enEchec()).isFalse();
        assertThat(EtatTraitement.EN_COURS.enEchec()).isFalse();
    }

    @Test
    @DisplayName("les trois questions sont exclusives : un état connu répond « oui » à une seule d'entre elles")
    void questions_exclusives() {
        for (EtatTraitement etat : EtatTraitement.values()) {
            long vraies = java.util.stream.Stream.of(etat.resultatsDisponibles(), etat.enAttente(), etat.enEchec())
                    .filter(Boolean::booleanValue)
                    .count();
            assertThat(vraies).as("état %s", etat).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("motifCourt : la PREMIERE ligne de la trace serveur, ni la pile entiere ni le silence")
    void motif_court() {
        Traitement echec = new Traitement(
                EtatTraitement.ERREUR,
                null,
                null,
                null,
                "RuntimeError: tadarida a plante\n  at ligne 12\n  at ligne 13",
                1);

        assertThat(echec.motifCourt()).contains("RuntimeError: tadarida a plante");
    }

    @Test
    @DisplayName("motifCourt : vide quand le serveur n a rien a dire (etat sain, trace absente ou blanche)")
    void motif_court_absent() {
        assertThat(Traitement.absent().motifCourt()).isEmpty();
        assertThat(new Traitement(EtatTraitement.FINI, null, null, null, null, null).motifCourt())
                .isEmpty();
        assertThat(new Traitement(EtatTraitement.ERREUR, null, null, null, "   \n  ", 1).motifCourt())
                .isEmpty();
    }

    @Test
    @DisplayName("Traitement.absent : le « rien » substitué au null quand la participation n'a jamais été calculée")
    void traitement_absent() {
        Traitement absent = Traitement.absent();

        assertThat(absent.estInconnu()).isTrue();
        assertThat(absent.resultatsDisponibles()).isFalse();
        assertThat(absent.enAttente()).isFalse();
        assertThat(absent.enEchec()).isFalse();
        assertThat(absent.etat()).isNull();
        assertThat(Optional.of(absent)).contains(Traitement.absent()); // valeur partagée, comparable
    }
}
