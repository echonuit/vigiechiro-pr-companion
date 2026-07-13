package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import fr.univ_amu.iut.lot.model.ServiceLot;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `reinitialiser-depot` (#984) : délègue l'effacement du plan de dépôt à [ServiceLot], code retour 0.
/// Service mocké (l'effacement + le retour « Prêt à déposer » sont couverts par `ServiceLotTest`).
class ReinitialiserDepotTest {

    private final ServiceLot service = mock(ServiceLot.class);

    @Test
    @DisplayName("réinitialise le dépôt du passage, confirme et renvoie 0")
    void reinitialise_code_zero() {
        StringWriter sortie = new StringWriter();
        CommandLine ligne = new CommandLine(new ReinitialiserDepot(service));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));

        int code = ligne.execute("--passage", "42");

        assertThat(code).isZero();
        verify(service).reinitialiserDepot(42L);
        assertThat(sortie.toString()).contains("réinitialisé");
    }
}
