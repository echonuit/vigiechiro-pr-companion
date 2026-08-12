package fr.univ_amu.iut.maj.model;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.VersionApplication;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La règle du service tient en une phrase - **on n'annonce que ce dont on est sûr** - et ces tests
/// exercent surtout les quatre chemins qui mènent au silence. Le cas nominal est le plus facile ;
/// c'est l'autre branche qui décide si la notification sera une aide ou une nuisance.
class VerificateurMiseAJourTest {

    private static final VersionDisponible PUBLIEE_2_22 =
            new VersionDisponible(new NumeroDeVersion(2, 22, 0), "https://exemple/releases/v2.22.0");

    @Test
    @DisplayName("une version plus récente est proposée, avec son adresse")
    void versionPlusRecenteEstProposee(@TempDir Path dossier) throws Exception {
        VerificateurMiseAJour verificateur =
                new VerificateurMiseAJour(JarDeVersion.annoncant(dossier, "2.21.3"), () -> Optional.of(PUBLIEE_2_22));

        assertThat(verificateur.versionAProposer())
                .as("l'annonce ne sert à rien si elle ne dit pas où aller")
                .contains(PUBLIEE_2_22);
    }

    @Test
    @DisplayName("à jour : rien à dire")
    void aJourNeDitRien(@TempDir Path dossier) throws Exception {
        VerificateurMiseAJour verificateur =
                new VerificateurMiseAJour(JarDeVersion.annoncant(dossier, "2.22.0"), () -> Optional.of(PUBLIEE_2_22));

        assertThat(verificateur.versionAProposer()).isEmpty();
    }

    @Test
    @DisplayName("version locale plus récente que la publiée : rien à dire non plus")
    void versionLocalePlusRecenteNeDitRien(@TempDir Path dossier) throws Exception {
        // Cas réel : un binaire construit depuis `main` avant publication. Proposer de « mettre à
        // jour » vers une version plus ANCIENNE serait absurde.
        VerificateurMiseAJour verificateur =
                new VerificateurMiseAJour(JarDeVersion.annoncant(dossier, "2.23.0"), () -> Optional.of(PUBLIEE_2_22));

        assertThat(verificateur.versionAProposer()).isEmpty();
    }

    @Test
    @DisplayName("hors d'un jar, on ne consulte même pas l'amont")
    void horsJarNeConsultePasLAmont() {
        // Sans version locale, il n'y a rien à comparer : interroger le réseau serait un appel pour
        // rien, au démarrage, sur la machine de chaque développeur.
        AtomicInteger appels = new AtomicInteger();
        DerniereVersionPubliee amont = () -> {
            appels.incrementAndGet();
            return Optional.of(PUBLIEE_2_22);
        };

        VerificateurMiseAJour verificateur = new VerificateurMiseAJour(new VersionApplication(), amont);

        assertThat(verificateur.versionAProposer()).isEmpty();
        assertThat(appels)
                .as("aucun appel réseau quand la comparaison est impossible d'avance")
                .hasValue(0);
    }

    @Test
    @DisplayName("amont muet (hors ligne, injoignable, limite de débit) : rien à dire")
    void amontMuetNeDitRien(@TempDir Path dossier) throws Exception {
        VerificateurMiseAJour verificateur =
                new VerificateurMiseAJour(JarDeVersion.annoncant(dossier, "2.21.3"), Optional::empty);

        assertThat(verificateur.versionAProposer()).isEmpty();
    }

    @Test
    @DisplayName("version locale illisible : rien à dire")
    void versionLocaleIllisibleNeDitRien(@TempDir Path dossier) throws Exception {
        // « 1.0-SNAPSHOT » est la valeur réelle du manifeste hors release.
        VerificateurMiseAJour verificateur = new VerificateurMiseAJour(
                JarDeVersion.annoncant(dossier, "1.0-SNAPSHOT"), () -> Optional.of(PUBLIEE_2_22));

        assertThat(verificateur.versionAProposer()).isEmpty();
    }
}
