package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Éprouve [PerimetreDesSessions] sur des situations que le dossier ne contient pas encore (#3884).
///
/// Le garde qui l'utilise balaie `dev-docs/recette/sessions/` : on ne peut pas lui présenter une
/// session admise qui se met à rendre des cas, ni une admission qui désigne un fichier disparu. Ces
/// deux verdicts sont pourtant ceux qui empêchent la liste des admises de dériver, donc ceux qu'il
/// faut voir basculer.
class PerimetreDesSessionsTest {

    @Test
    @DisplayName("Une session qui rend des cas est lue, avec son compte")
    void une_session_fructueuse_est_lue() {
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s1.md", 37), Set.of());

        assertThat(p.lues()).containsExactly(Map.entry("s1.md", 37));
        assertThat(p.muettes()).isEmpty();
    }

    @Test
    @DisplayName("Un seul cas suffit à rendre une session lue")
    void un_seul_cas_suffit() {
        // La borne du tri. Avec `cas >= 0` ou `cas > 1`, une session bascule du mauvais côté.
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s2.md", 1, "s3.md", 0), Set.of("s3.md"));

        assertThat(p.lues()).containsOnlyKeys("s2.md");
        assertThat(p.muettes()).containsExactly("s3.md");
    }

    @Test
    @DisplayName("Une session muette que rien n'admet est un silence non déclaré")
    void une_muette_non_admise_rougit() {
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s1.md", 37, "s6.md", 0), Set.of());

        assertThat(p.silencesNonDeclares()).containsExactly("s6.md");
        assertThat(p.admissionsPerimees()).isEmpty();
    }

    @Test
    @DisplayName("Une session muette et admise ne rougit pas")
    void une_muette_admise_se_tait_legitimement() {
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s1.md", 37, "s6.md", 0), Set.of("s6.md"));

        assertThat(p.muettes()).containsExactly("s6.md");
        assertThat(p.silencesNonDeclares()).isEmpty();
        assertThat(p.admissionsPerimees()).isEmpty();
    }

    @Test
    @DisplayName("Une session admise muette qui rend des cas signale une admission périmée")
    void une_admise_qui_parle_rougit() {
        // Le sens qui rend le point 2 de #3884 mesurable : convertir une session DOIT retirer sa
        // ligne, et le garde refuse qu'on l'oublie.
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s6.md", 12), Set.of("s6.md"));

        assertThat(p.lues()).containsOnlyKeys("s6.md");
        assertThat(p.admissionsPerimees()).containsExactly("s6.md");
        assertThat(p.silencesNonDeclares()).isEmpty();
    }

    @Test
    @DisplayName("Une admission qui ne désigne plus aucun fichier a péri aussi")
    void une_admise_disparue_rougit() {
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s1.md", 37), Set.of("s-supprimee.md"));

        assertThat(p.admissionsPerimees()).containsExactly("s-supprimee.md");
    }

    @Test
    @DisplayName("Sur un dossier vide, le garde ne se déclare pas satisfait en silence")
    void un_dossier_vide_ne_rassure_pas() {
        // ⚠️ Le cas qui ment dans le sens rassurant : aucun silence non déclaré, aucune admission
        // périmée, et pourtant rien n'a été lu. C'est `assiette()` et `casLus()` qui doivent le
        // dire, et c'est pourquoi ils sont assertés ici plutôt que supposés.
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of(), Set.of());

        assertThat(p.lues()).isEmpty();
        assertThat(p.muettes()).isEmpty();
        assertThat(p.silencesNonDeclares()).isEmpty();
        assertThat(p.admissionsPerimees()).isEmpty();
        assertThat(p.casLus()).isZero();
        assertThat(p.assiette()).isEqualTo("0 session(s) lue(s) sur 0");
    }

    @Test
    @DisplayName("Le compte des cas somme les sessions lues, et elles seules")
    void les_cas_lus_se_somment() {
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s1.md", 37, "s9.md", 14, "s6.md", 0), Set.of());

        assertThat(p.casLus()).isEqualTo(51);
    }

    @Test
    @DisplayName("L'assiette nomme les deux nombres, jamais le seul flatteur")
    void l_assiette_dit_les_deux_nombres() {
        // Un rapport qui n'annoncerait que « 2 sessions lues » se lirait comme s'il y en avait deux.
        PerimetreDesSessions p =
                PerimetreDesSessions.analyser(Map.of("s1.md", 37, "s9.md", 14, "s6.md", 0, "s7.md", 0), Set.of());

        assertThat(p.assiette()).isEqualTo("2 session(s) lue(s) sur 4");
    }

    @Test
    @DisplayName("Les listes rendues ne se modifient pas depuis l'extérieur")
    void les_listes_sont_figees() {
        PerimetreDesSessions p = PerimetreDesSessions.analyser(Map.of("s1.md", 37, "s6.md", 0), Set.of());

        assertThat(p.muettes()).isUnmodifiable();
        assertThat(p.lues()).isUnmodifiable();
        assertThat(p.silencesNonDeclares()).isUnmodifiable();
        assertThat(p.admissionsPerimees()).isUnmodifiable();
    }
}
