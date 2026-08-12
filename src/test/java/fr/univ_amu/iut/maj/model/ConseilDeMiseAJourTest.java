package fr.univ_amu.iut.maj.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// Le conseil de mise à jour est une **phrase unique**, rendue à l'identique par l'IHM et par la CLI.
/// C'est ce qui garantit la parité par construction : si chacune composait sa version du conseil,
/// elles divergeraient au premier ajustement, et la parité redeviendrait une intention.
///
/// Ces tests portent donc sur la phrase elle-même, et non sur ses deux rendus.
class ConseilDeMiseAJourTest {

    @ParameterizedTest
    @ValueSource(strings = {"Windows 11", "Windows 10", "windows server 2022", "WINDOWS"})
    @DisplayName("sous Windows, le conseil nomme le geste winget")
    void sousWindowsLeConseilNommeWinget(String systeme) {
        Optional<String> conseil = ConseilDeMiseAJour.pour(systeme);

        // La commande complète, paquet compris : un conseil qu'il faut compléter soi-même n'en est
        // pas un. C'est aussi ce qui rend le test insensible à une reformulation de la phrase.
        assertThat(conseil)
                .isPresent()
                .get(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("winget upgrade Echonuit.VigieChiroCompanion");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Linux", "Mac OS X", "FreeBSD"})
    @DisplayName("ailleurs, aucun geste alternatif n'est conseillé")
    void ailleursAucunGesteAlternatif(String systeme) {
        assertThat(ConseilDeMiseAJour.pour(systeme)).isEmpty();
    }

    @Test
    @DisplayName("un système inconnu ne fait pas conseiller au hasard")
    void systemeInconnuNeConseillePas() {
        // `os.name` est une propriété système : elle peut être absente, vide, ou surprenante. On ne
        // devine pas - ADR 2213, un dispositif qui ne peut pas conclure ne conclut pas.
        assertThat(ConseilDeMiseAJour.pour(null)).isEmpty();
        assertThat(ConseilDeMiseAJour.pour("")).isEmpty();
        assertThat(ConseilDeMiseAJour.pour("   ")).isEmpty();
    }

    @Test
    @DisplayName("le conseil PROPOSE le geste winget, il ne l'impose pas")
    void leConseilProposeSansImposer() {
        String conseil = ConseilDeMiseAJour.pour("Windows 11").orElseThrow();

        // Le point de la décision prise sur #3616 : on ne DÉTECTE pas le canal d'installation. Le
        // scope `user` étant une constante d'identité (ADR 0045), un MSI posé à la main et un paquet
        // winget vivent au même endroit : le chemin ne discrimine pas. Plutôt qu'un geste unique et
        // parfois faux, on en propose un second en disant à qui il s'adresse.
        assertThat(conseil).containsIgnoringCase("si vous");
    }
}
