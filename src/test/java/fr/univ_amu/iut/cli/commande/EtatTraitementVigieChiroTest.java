package fr.univ_amu.iut.cli.commande;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.model.ImportObservations;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.SuiviTraitement;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// `etat-traitement-vigiechiro` (#1265) : l'interface que lit un **script**, ce sont les codes de retour.
/// Ils doivent donc dire exactement une chose chacun : « c'est prêt », « patiente », « ça a échoué »,
/// « ça n'a jamais tourné », « je n'ai pas pu demander ». Suivi mocké, aucun réseau.
class EtatTraitementVigieChiroTest {

    private static final String COMPTE_RENDU = "Observations importées depuis Vigie-Chiro : 1284 observation(s).";

    private final SuiviTraitement suivi = mock(SuiviTraitement.class);
    private final ImportObservations importation = mock(ImportObservations.class);

    @AfterEach
    void nettoyerJetonPonctuel() {
        System.clearProperty("vigiechiro.token");
    }

    private CommandLine ligne(Optional<SuiviTraitement> moteur, StringWriter sortie) {
        return ligne(moteur, sortie, new StringWriter());
    }

    private CommandLine ligne(Optional<SuiviTraitement> moteur, StringWriter sortie, StringWriter erreur) {
        CommandLine ligne = new CommandLine(new EtatTraitementVigieChiro(moteur, Optional.of(importation)));
        ligne.setOut(new PrintWriter(sortie, true));
        ligne.setErr(new PrintWriter(erreur, true));
        return ligne;
    }

    /// La commande ne modifie rien tant qu'on ne le lui demande pas : c'est ce sur quoi un script
    /// compte, et ce qui la dispense du verrou du dossier de travail (#5784).
    @Test
    @DisplayName("#5784 : sans --importer, une analyse terminée n'importe rien et renvoie à l'option")
    void sans_l_option_rien_n_est_importe() {
        when(suivi.relever(42L)).thenReturn(traitement(EtatTraitement.FINI));
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie).execute("--passage", "42");

        assertThat(code).isZero();
        verifyNoInteractions(importation);
        assertThat(sortie.toString()).contains("--importer");
    }

    @Test
    @DisplayName("#5784 : --importer sur une analyse terminée importe les observations et le dit, code 0")
    void avec_l_option_l_import_part() {
        when(suivi.relever(42L)).thenReturn(traitement(EtatTraitement.FINI));
        when(importation.importer(42L, false)).thenReturn(COMPTE_RENDU);
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie).execute("--passage", "42", "--importer");

        assertThat(code).isZero();
        verify(importation).importer(42L, false);
        assertThat(sortie.toString()).contains("TERMINÉE", COMPTE_RENDU);
    }

    @Test
    @DisplayName("#5784 : --importer sur une nuit déjà importée ne réimporte pas, le dit, et rend 0")
    void avec_l_option_une_nuit_deja_importee_n_est_pas_reimportee() {
        when(suivi.relever(42L)).thenReturn(traitement(EtatTraitement.FINI));
        when(importation.aDejaSesObservations(42L)).thenReturn(true);
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie).execute("--passage", "42", "--importer");

        assertThat(code).isZero();
        verify(importation, never()).importer(42L, false);
        assertThat(sortie.toString()).contains("déjà importées");
    }

    @Test
    @DisplayName("#5784 : un import demandé qui échoue rend 2 et dit son motif, l'état restant affiché")
    void avec_l_option_un_import_qui_echoue_rend_deux() {
        when(suivi.relever(42L)).thenReturn(traitement(EtatTraitement.FINI));
        when(importation.importer(42L, false)).thenThrow(new RegleMetierException("aucune donnée renvoyée"));
        StringWriter sortie = new StringWriter();
        StringWriter erreur = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie, erreur).execute("--passage", "42", "--importer");

        assertThat(code).isEqualTo(2);
        assertThat(sortie.toString()).contains("TERMINÉE");
        assertThat(erreur.toString()).contains("aucune donnée renvoyée");
    }

    @Test
    @DisplayName("#5784 : --importer sur une analyse en cours n'importe rien et rend 3, comme sans l'option")
    void avec_l_option_une_analyse_en_cours_rend_trois() {
        when(suivi.relever(42L)).thenReturn(traitement(EtatTraitement.EN_COURS));

        int code = ligne(Optional.of(suivi), new StringWriter()).execute("--passage", "42", "--importer");

        assertThat(code).isEqualTo(3);
        verifyNoInteractions(importation);
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
    @DisplayName("analyse en échec côté serveur → code 1, et la trace du serveur est restituée telle quelle")
    void en_echec_code_un() {
        Traitement echec =
                new Traitement(EtatTraitement.ERREUR, null, null, "2026-07-13T10:00:00+00:00", "Traceback: boum", 1);
        when(suivi.relever(42L)).thenReturn(echec);
        StringWriter sortie = new StringWriter();

        int code = ligne(Optional.of(suivi), sortie).execute("--passage", "42");

        assertThat(code).isEqualTo(1);
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
    @DisplayName("suivi indisponible (contexte sans connexion) → code 2 : on n'a pas pu demander (pas un état serveur)")
    void suivi_indisponible_code_deux() {
        int code = ligne(Optional.empty(), new StringWriter()).execute("--passage", "42");

        assertThat(code)
                .as("indisponible = 2 (refus/pas pu demander, #2294), distinct de EN_ECHEC (1)")
                .isEqualTo(2);
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
