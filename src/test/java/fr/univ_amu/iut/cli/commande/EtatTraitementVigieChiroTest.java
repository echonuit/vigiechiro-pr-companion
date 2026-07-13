package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `etat-traitement-vigiechiro` (#1265) : l'interface que lit un **script**, ce sont les codes de retour.
/// Ils doivent donc dire exactement une chose chacun — « c'est prêt », « patiente », « ça a échoué »,
/// « ça n'a jamais tourné », « je n'ai pas pu demander ». Suivi mocké, aucun réseau.
class EtatTraitementVigieChiroTest {

    private final SuiviTraitement suivi = mock(SuiviTraitement.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Optional<SuiviTraitement> moteur, StringWriter sortie) {
        CommandLine ligne = new CommandLine(new EtatTraitementVigieChiro(moteur));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(new StringWriter(), true));
        return ligne;
    }

    @Test
    @DisplayName("analyse terminée → code 0 : le script peut enchaîner sur l'import")
    void terminee_code_zero() {
        when(suivi.relever(42L)).thenReturn(traitement(EtatTraitement.FINI));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        assertThat(sortie.toString()).contains("TERMINÉE", "importer-vigiechiro");
    }

    @Test
    @DisplayName("planifiée, en cours ou nouvel essai → code 3 : il n'y a qu'à patienter (boucle du script)")
    void en_attente_code_trois() {
        for (EtatTraitement etat :
                new EtatTraitement[] {EtatTraitement.PLANIFIE, EtatTraitement.EN_COURS, EtatTraitement.RETRY}) {
            when(suivi.relever(42L)).thenReturn(traitement(etat));

            int code = ligne(Optional.of(suivi), new StringWriter()).execute("--passage", "42");

            assertThat(code).as("état %s", etat).isEqualTo(3);
        }
    }

    @Test
    @DisplayName("analyse en échec → code 2, et la trace du serveur est restituée telle quelle")
    void en_echec_code_deux() {
        Traitement echec =
                new Traitement(EtatTraitement.ERREUR, null, null, "2026-07-13T10:00:00+00:00", "Traceback: boum", 1);
        when(suivi.relever(42L)).thenReturn(echec);
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(2);
        assertThat(sortie.toString()).contains("EN ÉCHEC", "Traceback: boum");
    }

    @Test
    @DisplayName("aucun traitement connu → code 4 : la nuit n'a jamais été analysée (à lancer)")
    void jamais_lance_code_quatre() {
        when(suivi.relever(42L)).thenReturn(Traitement.absent());
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(4);
        assertThat(sortie.toString()).contains("aucun traitement connu", "lancer-traitement-vigiechiro");
    }

    @Test
    @DisplayName("suivi indisponible (contexte sans connexion) → échec d'exécution, pas un état")
    void suivi_indisponible_echoue() {
        int code = ligne(Optional.empty(), new StringWriter()).execute("--passage", "42");

        assertThat(code).isNotZero();
    }

    @Test
    @DisplayName("--token pose le jeton ponctuel (propriété système), sans rien persister")
    void option_token_pose_le_jeton_ponctuel() {
        when(suivi.relever(42L)).thenReturn(traitement(EtatTraitement.FINI));

        ligne(Optional.of(suivi), new StringWriter()).execute("--passage", "42", "--token", "jeton-essai");

        assertThat(System.getProperty("vigiechiro.token")).isEqualTo("jeton-essai");
    }

    private static Traitement traitement(EtatTraitement etat) {
        return new Traitement(etat, "2026-07-13T08:00:00+00:00", "2026-07-13T08:10:00+00:00", null, null, null);
    }
}
