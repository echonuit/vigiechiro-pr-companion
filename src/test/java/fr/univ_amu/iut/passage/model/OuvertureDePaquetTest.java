package fr.univ_amu.iut.passage.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Ce que l'ouverture d'un paquet garantit sur l'**attribution** de ce qui sera jugé (#4626).
///
/// L'identité s'appose **à l'ouverture**, et non au moment du jugement : l'identité locale expire
/// avec le jeton de la plateforme, à quatorze jours, et un avis dont on ne sait plus qui l'a rendu
/// n'a plus de sens sous le régime de la copie signée (ADR 4517).
class OuvertureDePaquetTest {

    private static final String MANIFESTE = "{\"nuit\":1}";
    private static final ProfilVigieChiro RELECTEUR =
            new ProfilVigieChiro("507f1f77bcf86cd799439011", "chiro-pierre", "Observateur");

    @TempDir
    Path dossier;

    @Test
    @DisplayName("Sans identité valide, l'ouverture est refusée et nomme la cause")
    void sans_identite_l_ouverture_est_refusee() throws IOException {
        Path paquet = paquetDe("seq.wav");

        assertThatThrownBy(() -> OuvertureDePaquet.ouvrir(paquet, Optional.empty()))
                .as("recueillir des verdicts anonymes serait pire que refuser d'ouvrir")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connect");
    }

    @Test
    @DisplayName("Avec une identité valide, l'ouverture retient le pseudo, pas l'objectid")
    void avec_identite_l_ouverture_retient_le_pseudo() throws IOException {
        Path paquet = paquetDe("seq.wav");

        PaquetOuvert ouvert = OuvertureDePaquet.ouvrir(paquet, Optional.of(RELECTEUR));

        assertThat(ouvert.pseudoRelecteur())
                .as("c'est le nom lisible qui signera les verdicts, pas l'identifiant de la plateforme")
                .isEqualTo("chiro-pierre");
        assertThat(ouvert.manifeste()).isEqualTo(MANIFESTE);
        assertThat(ouvert.sequences()).containsExactly("sequences/seq.wav");
    }

    @Test
    @DisplayName("Le pseudo relevé à l'ouverture ne dépend plus de l'identité ensuite")
    void le_pseudo_releve_ne_depend_plus_de_l_identite_ensuite() throws IOException {
        Path paquet = paquetDe("seq.wav");

        PaquetOuvert ouvert = OuvertureDePaquet.ouvrir(paquet, Optional.of(RELECTEUR));

        assertThat(ouvert.pseudoRelecteur())
                .as("l'identité a été relevée une fois : sa péremption ultérieure ne l'efface pas")
                .isEqualTo("chiro-pierre");
    }

    @Test
    @DisplayName("Un paquet sans manifeste n'est pas un paquet, et le refus le dit")
    void un_paquet_sans_manifeste_est_refuse() throws IOException {
        Path faux = dossier.resolve("faux.zip");
        Files.write(faux, new byte[] {80, 75, 5, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        assertThatThrownBy(() -> OuvertureDePaquet.ouvrir(faux, Optional.of(RELECTEUR)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PlanDePaquet.NOM_MANIFESTE);
    }

    private Path paquetDe(String nomSequence) throws IOException {
        Path sequence = dossier.resolve(nomSequence);
        Files.write(sequence, new byte[] {1, 2, 3});
        Path paquet = dossier.resolve("paquet.zip");
        PlanDePaquet plan = PlanDePaquet.pour(paquet, MANIFESTE, List.of(sequence));
        EcrivainPaquet.ecrire(paquet, plan, MANIFESTE, List.of(sequence));
        return paquet;
    }
}
