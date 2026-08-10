package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.persistence.DataAccessException;
import fr.univ_amu.iut.commun.persistence.RefusAvantEcriture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Ce qu'une exception vaut pour l'appelant (#3570).
///
/// La pièce est **pure** : elle décide sans rien exécuter. C'est ce qui permet de couvrir ici le cas
/// qu'aucun harnais ne sait fabriquer sans dépendance nouvelle - un refus né de la migration sur un
/// dossier occupé - au lieu d'introduire `sqlite3` dans les `bats` pour un seul test.
class VerdictCliTest {

    @Test
    @DisplayName("un refus avant écriture vaut 2, même s'il hérite de l'échec d'accès aux données")
    void refus_avant_ecriture_vaut_deux() {
        VerdictCli verdict = VerdictCli.de(new RefusAvantEcriture("Ce dossier de travail est déjà utilisé", null));

        assertThat(verdict.nature()).isEqualTo(VerdictCli.Nature.REFUS);
        assertThat(verdict.code())
                .as("c'est le cas que #3498 avait appris à traduire, et que la migration lancée depuis"
                        + " `main` rendait encore en 1 : rien n'a été touché, donc 2")
                .isEqualTo(Cli.CODE_REFUS);
        assertThat(verdict.phrase()).startsWith("Refus : ");
    }

    @Test
    @DisplayName("l'ordre des tests compte : RefusAvantEcriture hérite de DataAccessException")
    void l_heritage_ne_fait_pas_basculer_le_refus_en_incident() {
        VerdictCli refus = VerdictCli.de(new RefusAvantEcriture("rien n'a été touché", null));
        VerdictCli incident = VerdictCli.de(new DataAccessException("panne en cours d'écriture", null));

        assertThat(refus.code())
                .as("un remaniement qui remonterait le test de DataAccessException ferait basculer tous"
                        + " les refus en incidents, sans que rien d'autre ne rougisse")
                .isNotEqualTo(incident.code());
        assertThat(incident.nature()).isEqualTo(VerdictCli.Nature.INCIDENT);
    }

    @Test
    @DisplayName("une règle métier et un validateur sont des refus, pas des incidents")
    void regle_metier_et_validateur_sont_des_refus() {
        assertThat(VerdictCli.de(new RegleMetierException("carré déjà déclaré")).code())
                .isEqualTo(Cli.CODE_REFUS);
        assertThat(VerdictCli.de(new IllegalArgumentException("numéro de carré mal formé"))
                        .code())
                .as("les validateurs R1/R2 remontent ainsi ; l'IHM les traite déjà comme des refus")
                .isEqualTo(Cli.CODE_REFUS);
    }

    @Test
    @DisplayName("une erreur d'usage n'est ni un refus ni un incident")
    void erreur_d_usage() {
        VerdictCli verdict = VerdictCli.de(new ErreurUsage("passage 999 introuvable"));

        assertThat(verdict.nature()).isEqualTo(VerdictCli.Nature.USAGE);
        assertThat(verdict.code()).isEqualTo(Cli.CODE_ERREUR_ARGUMENTS);
        assertThat(verdict.phrase()).startsWith("Erreur d'usage : ");
    }

    @Test
    @DisplayName("un incident inattendu vaut 1, et sa phrase ne porte pas la trace")
    void incident_inattendu() {
        VerdictCli verdict = VerdictCli.de(new IllegalStateException("chose imprévue"));

        assertThat(verdict.code()).isEqualTo(Cli.CODE_ERREUR_EXECUTION);
        assertThat(verdict.phrase())
                .as("la trace vit dans <workspace>/logs/, pas sur la sortie d'un script")
                .isEqualTo("Échec : chose imprévue")
                .doesNotContain("at fr.univ_amu");
    }
}
