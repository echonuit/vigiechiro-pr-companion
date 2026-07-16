package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `importer-vigiechiro` (#1181) : délégation à [ImportVigieChiro] (mocké - l'orchestration réseau +
/// import est couverte par `ImportVigieChiroTest`), rattachement préalable optionnel, rendu partagé
/// avec l'import CSV, jeton ponctuel `--token`.
class ImporterVigieChiroTest {

    private final ImportVigieChiro moteur = mock(ImportVigieChiro.class);
    private final ResultatsIdentificationDao resultatsDao = mock(ResultatsIdentificationDao.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Optional<ImportVigieChiro> service, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new ImporterVigieChiro(service, resultatsDao));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    private static BilanImport bilan() {
        return new BilanImport(
                new ResultatsIdentification(9L, "vigiechiro", "\"API\"", "2026-07-12T10:00:00", 42L), 4806, 2, 1);
    }

    @Test
    @DisplayName("import via le lien existant : bilan rendu comme l'import CSV, code 0")
    void import_par_le_lien_code_zero() {
        when(moteur.importer(eq(42L), eq(false), any())).thenReturn(bilan());
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(moteur), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString())
                .contains("jeu #9")
                .contains("Observations importées")
                .contains("4806");
        verify(moteur, never())
                .rattacher(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("--participation : rattache d'abord le passage, puis importe")
    void rattachement_prealable() {
        when(moteur.importer(eq(42L), eq(true), any())).thenReturn(bilan());

        int code = ligne(Optional.of(moteur), new StringWriter())
                .execute("--passage", "42", "--remplacer", "--participation", "6a4961f587bc8dba39481180");

        assertThat(code).isZero();
        verify(moteur).rattacher(42L, "6a4961f587bc8dba39481180");
        verify(moteur).importer(eq(42L), eq(true), any());
    }

    @Test
    @DisplayName("service absent (feature import-vigiechiro désactivée) : refus explicite, code non nul")
    void service_absent_code_non_nul() {
        int code = ligne(Optional.empty(), new StringWriter()).execute("--passage", "42");

        assertThat(code).isNotZero();
    }

    @Test
    @DisplayName(
            "passage déjà pourvu d'un jeu, sans --remplacer : refus AVANT tout appel réseau (parité avec importer-tadarida)")
    void deja_pourvu_sans_remplacer_refuse_avant_reseau() {
        when(resultatsDao.findByPassage(42L))
                .thenReturn(Optional.of(
                        new ResultatsIdentification(9L, "vigiechiro", "\"API\"", "2026-07-12T10:00:00", 42L)));

        int code = ligne(Optional.of(moteur), new StringWriter()).execute("--passage", "42");

        assertThat(code).isNotZero();
        verify(moteur, never()).importer(anyLong(), anyBoolean(), any());
    }

    @Test
    @DisplayName("--token : jeton ponctuel posé pour la durée de la commande (propriété système)")
    void token_ponctuel_pose() {
        when(moteur.importer(eq(42L), eq(false), any())).thenReturn(bilan());

        ligne(Optional.of(moteur), new StringWriter()).execute("--passage", "42", "--token", "jeton-x");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-x");
    }

    @Test
    @DisplayName("import long : l'avancement par page est relayé sur la sortie d'erreur (#1622)")
    void progression_relayee_sur_stderr() {
        when(moteur.importer(eq(42L), eq(false), any())).thenAnswer(invocation -> {
            SuiviPagination suivi = invocation.getArgument(2);
            suivi.surPage(1, 3); // le service signale une page rapatriée
            return bilan();
        });
        StringWriter erreur = new StringWriter();
        CommandLine ligne = new CommandLine(new ImporterVigieChiro(Optional.of(moteur), resultatsDao));
        ligne.setOut(new PrintWriter(new StringWriter(), true));
        ligne.setErr(new PrintWriter(erreur, true));

        ligne.execute("--passage", "42");

        assertThat(erreur.toString()).contains("page 1/3");
    }
}
