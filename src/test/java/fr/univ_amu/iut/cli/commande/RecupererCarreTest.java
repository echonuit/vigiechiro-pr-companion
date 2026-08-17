package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.cli.GesteAttenduCli;
import fr.univ_amu.iut.commun.model.Protocole;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.sites.model.RapatriementCarre;
import fr.univ_amu.iut.sites.model.Site;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `recuperer-carre` : le geste de #3806, offert à la ligne de commande (#3856).
///
/// Le service n'est pas réimplémenté ici : c'est **le même** [RapatriementCarre] que la fenêtre de
/// déclaration, mocké pour n'éprouver que ce que la commande en fait - ce qu'elle écrit, où, et avec
/// quel code de sortie.
class RecupererCarreTest {

    private static final String CARRE = "130711";

    private final RapatriementCarre rapatriement = mock(RapatriementCarre.class);
    private final StringWriter sortie = new StringWriter();
    private final StringWriter erreur = new StringWriter();

    private int executer(Optional<RapatriementCarre> service, String... args) {
        CommandLine ligne = new CommandLine(new RecupererCarre(() -> service));
        ligne.setCaseInsensitiveEnumValuesAllowed(true);
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreur, true));
        ligne.setExecutionExceptionHandler((exception, commande, parse) -> {
            if (exception instanceof RegleMetierException refus) {
                commande.getErr().println("Refus : " + GesteAttenduCli.message(refus));
                return 2;
            }
            throw exception;
        });
        return ligne.execute(args);
    }

    @Test
    @DisplayName("#3856 : un carré récupéré écrit son identifiant SEUL sur la sortie standard")
    void un_carre_recupere_ecrit_son_identifiant() {
        Site site = new Site(7L, CARRE, "Étang", Protocole.STANDARD, null, "2026-08-16", "u-1");
        when(rapatriement.rapatrier(any())).thenReturn(new RapatriementCarre.Resultat.Rapatrie(site, 41));

        assertThat(executer(Optional.of(rapatriement), "--carre", CARRE)).isZero();

        // Le contrat de `creer-site` : l'identifiant seul, pour `SITE=$(vigiechiro recuperer-carre …)`.
        assertThat(sortie.toString().trim()).isEqualTo("7");
        // Le compte rendu lisible part ailleurs : le mélanger casserait la substitution ci-dessus.
        assertThat(erreur.toString()).contains(CARRE).contains("41");
    }

    @Test
    @DisplayName("#3856 : un carré absent de la plateforme est un REFUS, pas un succès vide")
    void un_carre_inexistant_est_un_refus() {
        when(rapatriement.rapatrier(any())).thenReturn(new RapatriementCarre.Resultat.Inexistant(CARRE));

        assertThat(executer(Optional.of(rapatriement), "--carre", CARRE)).isEqualTo(2);

        // Sans code non nul, un script enchaînerait sur un site qui n'existe pas.
        assertThat(sortie.toString()).isEmpty();
        assertThat(erreur.toString()).contains("n'existe pas");
    }

    @Test
    @DisplayName("#3856 : un carré sous un autre protocole est refusé en le nommant")
    void un_autre_protocole_est_refuse_en_le_nommant() {
        when(rapatriement.rapatrier(any()))
                .thenReturn(new RapatriementCarre.Resultat.AutreProtocole(List.of("Vigie-chiro - Routier-" + CARRE)));

        assertThat(executer(Optional.of(rapatriement), "--carre", CARRE)).isEqualTo(2);
        assertThat(erreur.toString()).contains("Point Fixe").contains("Routier");
    }

    @Test
    @DisplayName("#3856 : plateforme injoignable → refus, et RIEN n'a été créé")
    void injoignable_ne_cree_rien() {
        when(rapatriement.rapatrier(any())).thenReturn(new RapatriementCarre.Resultat.Indisponible());

        assertThat(executer(Optional.of(rapatriement), "--carre", CARRE)).isEqualTo(2);
        assertThat(erreur.toString()).contains("Rien n'a été créé");
    }

    @Test
    @DisplayName("#3856 : fonctionnalité éteinte → la commande le DIT, au lieu d'une pile")
    void fonctionnalite_eteinte_se_dit() {
        assertThat(executer(Optional.empty(), "--carre", CARRE)).isEqualTo(2);

        assertThat(erreur.toString()).contains("désactivée").contains("Réglages");
        verify(rapatriement, never()).rapatrier(any());
    }
}
