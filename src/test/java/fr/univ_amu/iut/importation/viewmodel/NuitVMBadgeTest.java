package fr.univ_amu.iut.importation.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.importation.model.Completude;
import fr.univ_amu.iut.importation.model.NuitDetectee;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Badge de complétude d'une nuit (#2036) : libellé et **classe de pastille**, dérivés de l'état,
/// jamais stockés.
///
/// La table écrivait auparavant sa sévérité dans le texte de la cellule (« ✓ complète », « ⚠
/// incomplète (motif) »). La cellule passe par `ColonneBadge`, qui la rend en pastille colorée - la
/// couleur vient donc de [NuitVM#classeBadge()], et il faut que ses deux branches soient justes.
///
/// Ce test compte double parce qu'**aucune capture ne montre une nuit incomplète** : le jeu d'essai
/// des aperçus n'a que des nuits complètes. La revue visuelle ne peut donc rien dire de la branche
/// « incomplète », et c'est ici qu'elle se vérifie.
class NuitVMBadgeTest {

    @Test
    @DisplayName("Une nuit complète : libellé « complète » et pastille de succès")
    void nuit_complete() {
        NuitVM nuit = new NuitVM(nuitDetectee(Completude.COMPLETE, null));

        assertThat(nuit.badge()).isEqualTo("complète");
        assertThat(nuit.classeBadge()).isEqualTo("badge-succes");
    }

    @Test
    @DisplayName("Une nuit tronquée : libellé « incomplète » et pastille d'avertissement, pas de danger")
    void nuit_incomplete() {
        NuitVM nuit = new NuitVM(nuitDetectee(Completude.TRONQUEE, "carte SD pleine"));

        assertThat(nuit.badge()).isEqualTo("incomplète");
        assertThat(nuit.classeBadge())
                .as("une nuit tronquée s'importe et se dépose normalement : c'est un avertissement, "
                        + "pas une erreur")
                .isEqualTo("badge-avertissement");
        assertThat(nuit.motifIncompletude())
                .as("le motif alimente l'infobulle de la pastille : il ne doit pas se perdre en chemin")
                .isEqualTo("carte SD pleine");
    }

    @Test
    @DisplayName("#4990 : une nuit inconnue le DIT, et sa pastille ne rassure pas")
    void nuit_inconnue() {
        NuitVM nuit = new NuitVM(nuitDetectee(Completude.INCONNUE, null));

        assertThat(nuit.badge())
                .as("« inconnue » n'est pas une nuance d'« incomplète » : la nuit peut être entière,"
                        + " et rien ne le dit")
                .isEqualTo("complétude inconnue");
        assertThat(nuit.classeBadge())
                .as("ni verte ni ambre : rien ne permet de rassurer, rien ne permet d'inquiéter."
                        + " La pastille verte est exactement ce que ce lot retire")
                .isEqualTo("badge-completude-inconnue");
        assertThat(nuit.estComplete())
                .as("`estComplete` veut dire « attestée complète », et ce n'est pas le cas ici")
                .isFalse();
        assertThat(nuit.motifIncompletude())
                .as("on ne sait rien : il n'y a aucun motif à donner, et en inventer un serait pire")
                .isNull();
    }

    @Test
    @DisplayName("#4990 : une nuit tronquée dit ce que le journal montrait avant l'arrêt, et quoi faire")
    void nuit_tronquee_rapproche_les_pieces() {
        NuitVM nuit = new NuitVM(nuitDetectee(
                Completude.TRONQUEE,
                "journal interrompu",
                List.of("03:14 Batteries internes 11 %", "03:14 Erreur d'écriture SD")));

        String explication = nuit.explication();

        assertThat(explication)
                .as("le motif de l'arrêt, tel que le badge le résume")
                .contains("journal interrompu");
        assertThat(explication)
                .as("et les pièces que l'observateur devait aller chercher sur un autre écran :"
                        + " c'est le rapprochement qui manquait, pas la détection")
                .contains("Batteries internes 11 %")
                .contains("Erreur d'écriture SD");
        assertThat(explication)
                .as("la conduite à tenir, qui est ce qui manquait le plus : un fait sans suite ne dit"
                        + " ni si la nuit est exploitable, ni s'il faut vérifier le matériel")
                .contains("restent exploitables")
                .contains("Vérifiez l'enregistreur");
        assertThat(explication)
                .as("et JAMAIS une cause : Companion n'a que le journal, il relève, il ne diagnostique pas")
                .doesNotContain("batterie était vide")
                .doesNotContain("cause");
    }

    @Test
    @DisplayName("#4990 : une nuit inconnue dit pourquoi on ne sait pas, sans inventer d'indice")
    void nuit_inconnue_dit_qu_elle_ne_sait_pas() {
        NuitVM nuit = new NuitVM(nuitDetectee(Completude.INCONNUE, null));

        assertThat(nuit.explication())
                .contains("Le journal ne couvre pas cette nuit")
                .contains("peut-être entière")
                .as("elle s'importe : l'ignorance n'est pas une raison d'écarter la nuit")
                .contains("s'importent normalement");
    }

    @Test
    @DisplayName("Une nuit complète n'a rien à expliquer, et son infobulle reste vide")
    void nuit_complete_n_explique_rien() {
        assertThat(new NuitVM(nuitDetectee(Completude.COMPLETE, null)).explication())
                .as("une infobulle sur un état normal est du bruit, et use l'attention qu'on veut"
                        + " garder pour les deux autres cas")
                .isNull();
    }

    private static NuitDetectee nuitDetectee(Completude completude, String motif) {
        return nuitDetectee(completude, motif, List.of());
    }

    private static NuitDetectee nuitDetectee(Completude completude, String motif, List<String> indices) {
        LocalDate date = LocalDate.of(2026, 7, 3);
        return new NuitDetectee(
                date, date.atTime(21, 0), date.plusDays(1).atTime(6, 0), List.of(), completude, motif, indices);
    }
}
