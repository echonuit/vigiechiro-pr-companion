package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OuvreurDeLienSystemeTest {

    @Test
    @DisplayName("ouvrir sans HostServices branché ne lève jamais d'exception (mode CLI/tests)")
    void ouvrir_sans_hostservices_est_silencieux() {
        assertThatCode(() -> new OuvreurDeLienSysteme().ouvrir("https://www.openstreetmap.org/"))
                .doesNotThrowAnyException();
    }
}
