package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.FuseauDuSite;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Lecture des bornes de nuit rendues par l'API (#1860).
///
/// L'enjeu n'est pas le parsing mais le **repère** : l'API rend un **instant** daté d'un décalage,
/// l'application raisonne en **heure murale locale**. Convertir, ce n'est pas jeter le décalage.
class ParticipationOrphelineTest {

    @Test
    @DisplayName("#1860 : une borne UTC devient l'heure LOCALE du même instant, pas le même cadran")
    void horodatage_convertit_au_lieu_de_tronquer() {
        Optional<LocalDateTime> lu = ParticipationOrpheline.horodatage("2026-07-04T19:00:00+00:00");

        // 19:00 UTC = 21:00 à Paris en juillet. Avant #1860 le code rendait 19:00 : l'observateur voyait
        // sa nuit commencer deux heures trop tôt.
        //
        // L'attente est ÉCRITE, plus calculée depuis `ZoneId.systemDefault()`. Elle l'était, et le
        // cas devenait alors tautologique : il vérifiait que la conversion emploie le fuseau de la
        // machine, ce qui restait vrai même quand ce n'était plus la bonne règle (#3406).
        assertThat(lu).contains(LocalDateTime.of(2026, 7, 4, 21, 0));
    }

    @Test
    @DisplayName("#1860 : l'aller-retour est STABLE - relire ce qu'on vient d'envoyer ne décale rien")
    void aller_retour_stable() {
        // Le défaut n'était pas un décalage ponctuel mais un CLIQUET : l'envoi retraduit l'heure locale
        // en UTC, donc chaque cycle reconstruire → envoyer retranchait un décalage. Une nuit de 21 h
        // était descendue à 15 h en quatre allers-retours. Ce test verrouille le point fixe.
        LocalDateTime nuit = LocalDateTime.of(2026, 7, 4, 21, 0);

        LocalDateTime apresUnCycle = unCycle(nuit);
        LocalDateTime apresQuatreCycles = unCycle(unCycle(unCycle(apresUnCycle)));

        assertThat(apresUnCycle).isEqualTo(nuit);
        assertThat(apresQuatreCycles)
                .as("quatre cycles ne doivent pas plus déplacer la nuit qu'un seul")
                .isEqualTo(nuit);
    }

    @Test
    @DisplayName("#1860 : une borne SANS décalage est déjà une heure murale, on n'y touche pas")
    void horodatage_sans_decalage_inchange() {
        assertThat(ParticipationOrpheline.horodatage("2026-07-04T21:00:00"))
                .contains(LocalDateTime.of(2026, 7, 4, 21, 0));
    }

    @Test
    @DisplayName("Borne absente ou illisible → vide (la nuit n'est pas écartée pour autant)")
    void horodatage_borne_inexploitable() {
        assertThat(ParticipationOrpheline.horodatage(null)).isEmpty();
        assertThat(ParticipationOrpheline.horodatage("  ")).isEmpty();
        assertThat(ParticipationOrpheline.horodatage("hier soir")).isEmpty();
    }

    /// Un cycle complet **envoi puis relecture** : l'heure locale part en UTC (ce que fait
    /// [CorrespondanceParticipation] au `PATCH`), et revient par [ParticipationOrpheline#horodatage].
    /// Un cycle complet : l'heure du site part vers la plateforme, puis en revient.
    ///
    /// L'aller emploie le fuseau du SITE, comme `CorrespondanceParticipation` (#3406) - l'employer ici
    /// et pas là ferait mesurer la boucle sur une moitié qui n'existe pas.
    private static LocalDateTime unCycle(LocalDateTime heureLocale) {
        String versLaPlateforme = heureLocale
                .atZone(FuseauDuSite.ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime()
                .toString();
        return ParticipationOrpheline.horodatage(versLaPlateforme).orElseThrow();
    }
}
