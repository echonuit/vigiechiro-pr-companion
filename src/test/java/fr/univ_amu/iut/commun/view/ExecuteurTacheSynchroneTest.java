package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Exécution **synchrone** ([ExecuteurTacheSynchrone], #1014, défaut de test) : le travail et le
/// callback correspondant s'enchaînent sur le fil appelant ; exactement un des deux callbacks est
/// appelé, jamais les deux.
class ExecuteurTacheSynchroneTest {

    private final ExecuteurTache executeur = new ExecuteurTacheSynchrone();

    @Test
    @DisplayName("succès : le résultat du travail est remis à `succes`, `echec` n'est pas appelé")
    void succes_remis_au_callback() {
        List<String> succes = new ArrayList<>();
        List<Throwable> echecs = new ArrayList<>();

        executeur.executer(() -> "42", succes::add, echecs::add);

        assertThat(succes).containsExactly("42");
        assertThat(echecs).isEmpty();
    }

    @Test
    @DisplayName("échec : l'exception du travail est remise à `echec`, `succes` n'est pas appelé")
    void echec_remis_au_callback() {
        List<Object> succes = new ArrayList<>();
        List<Throwable> echecs = new ArrayList<>();
        RuntimeException panne = new IllegalStateException("base indisponible");

        executeur.executer(
                () -> {
                    throw panne;
                },
                succes::add,
                echecs::add);

        assertThat(succes).isEmpty();
        assertThat(echecs).containsExactly(panne);
    }
}
