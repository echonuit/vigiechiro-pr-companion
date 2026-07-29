package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.Certitude;
import fr.univ_amu.iut.validation.model.LigneObservationAudio;
import fr.univ_amu.iut.validation.model.MessageObservation;
import fr.univ_amu.iut.validation.model.StatutObservation;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Affichage du **troisième avis** (#1417) : ce que le validateur du MNHN a tranché, et le fil qui
/// l'entoure.
///
/// L'enjeu tient en une phrase : jusqu'ici, l'écran présentait la correction de l'observateur comme le
/// dernier mot. Un expert avait pu la réviser sans qu'on le voie **jamais**. Ces tests figent le fait que
/// l'avis s'affiche, et que le **désaccord** se distingue de l'accord — c'est là que se joue la qualité
/// de la donnée déposée.
class FormatAvisValidateurTest {

    @Test
    @DisplayName("#1417 : aucun expert ne s'est prononcé (le cas courant) → tiret, badge muet")
    void aucun_avis() {
        LigneObservationAudio ligne = ligne("Pipkuh", "Pippip", null, null);

        assertThat(FormatAvisValidateur.avis(ligne)).isEqualTo("—");
        assertThat(FormatAvisValidateur.classeBadge(ligne))
                .as("l'absence d'avis est la norme : elle ne doit pas crier")
                .isEqualTo("badge-validateur-absent");
    }

    @Test
    @DisplayName("#1417 : le validateur CONFIRME l'observateur → badge d'accord, discret")
    void avis_en_accord() {
        LigneObservationAudio ligne = ligne("Pipkuh", "Pippip", "Pippip", Certitude.SUR);

        assertThat(FormatAvisValidateur.avis(ligne))
                .as("l'avis se lit d'un bloc : le taxon et sa certitude, pas deux colonnes")
                .isEqualTo("Pipistrelle commune · Sûr");
        assertThat(FormatAvisValidateur.classeBadge(ligne)).isEqualTo("badge-validateur-accord");
    }

    @Test
    @DisplayName("#1417 : le validateur CONTREDIT l'observateur → badge de désaccord : c'est CE cas qu'on"
            + " doit voir en premier")
    void avis_en_desaccord() {
        LigneObservationAudio ligne = ligne("Pipkuh", "Pippip", "Pipnat", Certitude.PROBABLE);

        assertThat(FormatAvisValidateur.classeBadge(ligne))
                .as("un expert qui confirme ne demande rien ; un expert qui contredit doit sauter aux yeux")
                .isEqualTo("badge-validateur-desaccord");
        assertThat(FormatAvisValidateur.avis(ligne)).isEqualTo("Pipistrelle commune · Probable");
    }

    @Test
    @DisplayName("#1417 : taxon de validateur hors référentiel → on affiche son CODE plutôt que rien")
    void taxon_sans_vernaculaire() {
        LigneObservationAudio ligne = new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-07-03",
                "130711",
                "Z41",
                null,
                "Pipkuh",
                0.9,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                null,
                null,
                null,
                "f.wav",
                0.1,
                0.4,
                null,
                false,
                null,
                "Especeinconnue",
                Certitude.SUR,
                null,
                0,
                null);

        assertThat(FormatAvisValidateur.avis(ligne))
                .as("une souche hors référentiel n'a pas de nom vernaculaire : le code vaut mieux qu'un vide")
                .isEqualTo("Especeinconnue · Sûr");
    }

    @Test
    @DisplayName("#1417 : la colonne du fil reste VIDE quand personne n'a écrit (et non « 0 »)")
    void marque_du_fil() {
        assertThat(FormatAvisValidateur.marqueFil(ligne("Pipkuh", null, null, null)))
                .as("une colonne d'indicateurs doit rester silencieuse tant qu'il n'y a rien à signaler")
                .isEmpty();
        assertThat(FormatAvisValidateur.marqueFil(avecFil(3))).isEqualTo("3");
    }

    @Test
    @DisplayName("#1417 : « Vous » se déduit de l'objectid du profil connecté — sans appel réseau ; un"
            + " auteur inconnu n'est PAS attribué")
    void auteur_dun_message() {
        MessageObservation deMoi = new MessageObservation(1L, 7L, 0, "u-moi", "Je doute.", null);
        MessageObservation delExpert = new MessageObservation(2L, 7L, 1, "u-validateur", "C'est Pipnat.", null);
        MessageObservation anonyme = new MessageObservation(3L, 7L, 2, null, "Vu.", null);

        assertThat(FormatAvisValidateur.auteur(deMoi, "u-moi")).isEqualTo("Vous");
        assertThat(FormatAvisValidateur.auteur(delExpert, "u-moi")).isEqualTo("Le validateur");
        assertThat(FormatAvisValidateur.auteur(anonyme, "u-moi"))
                .as("mieux vaut un message anonyme qu'un message faussement signé")
                .isEqualTo("Auteur inconnu");
        assertThat(FormatAvisValidateur.auteur(deMoi, null))
                .as("hors connexion, on ne sait pas qui on est : on n'attribue rien")
                .isEqualTo("Le validateur");
    }

    @Test
    @DisplayName("#1417 : un message que le serveur n'a pas daté reste lisible — on n'invente pas de date")
    void date_dun_message() {
        MessageObservation sansDate = new MessageObservation(1L, 7L, 0, "u-moi", "Vu.", null);
        MessageObservation date =
                new MessageObservation(2L, 7L, 1, "u-moi", "Vu.", Instant.parse("2026-07-11T21:04:00Z"));

        assertThat(FormatAvisValidateur.quand(sansDate)).isEmpty();
        assertThat(FormatAvisValidateur.quand(date)).contains("2026");
    }

    // --- Fixtures ----------------------------------------------------------------------------------

    /// « Pipistrelle commune » est le vernaculaire semé pour Pippip ; on projette ici celui du **validateur**
    /// (`nomValidateur`), qui est le seul que la colonne affiche.
    private static LigneObservationAudio ligne(
            String tadarida, String observateur, String validateur, Certitude certitudeValidateur) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-07-03",
                "130711",
                "Z41",
                null,
                tadarida,
                0.9,
                observateur,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                null,
                null,
                null,
                "f.wav",
                0.1,
                0.4,
                null,
                false,
                null,
                validateur,
                certitudeValidateur,
                validateur == null ? null : "Pipistrelle commune",
                0,
                null);
    }

    private static LigneObservationAudio avecFil(int messages) {
        return new LigneObservationAudio(
                1L,
                1L,
                1L,
                1,
                "2026-07-03",
                "130711",
                "Z41",
                null,
                "Pipkuh",
                0.9,
                null,
                null,
                StatutObservation.NON_TOUCHEE,
                false,
                null,
                45,
                null,
                null,
                null,
                null,
                "f.wav",
                0.1,
                0.4,
                null,
                false,
                null,
                null,
                null,
                null,
                messages,
                null);
    }
}
