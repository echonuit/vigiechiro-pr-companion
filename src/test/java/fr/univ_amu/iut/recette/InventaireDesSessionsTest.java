package fr.univ_amu.iut.recette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Confronte l'inventaire des sessions de recette au dossier qui les contient (#3885).
///
/// ## Ce qui s'est passé, et pourquoi un avertissement n'a pas suffi
///
/// `dev-docs/recette/index.md` porte cet avertissement :
///
/// > ⚠️ **Cet inventaire est la seule source.** Il a été recopié ailleurs [...] et les trois copies
/// > ont divergé en **quelques heures**.
///
/// Et la page portait pourtant **trois** inventaires. L'issue #3885 en signalait deux ; le troisième
/// s'est découvert en lisant :
///
/// | Où | Ce qu'il portait |
/// |---|---|
/// | le tableau, en tête | les dix sessions - **juste** |
/// | « Où ça vit », deux cents lignes plus bas | « les huit sessions existent » : **huit sur dix** |
/// | « Les scripts de session », une liste à puces | S1, S2, S3, S4, S7 : **cinq sur dix** |
///
/// Elle s'était recopiée **elle-même**, deux fois - ce que son avertissement ne pouvait pas
/// envisager, puisqu'il ne surveillait que l'extérieur. ⚠️ **Une consigne n'est pas un garde.**
///
/// ## Les deux premiers devoirs auraient laissé passer tout cela
///
/// `S9` et `S10` figuraient bien au tableau : un garde qui vérifie que l'inventaire est **complet**
/// serait resté vert pendant que deux copies dérivaient plus bas. C'est
/// [#le_tableau_est_le_seul_inventaire] qui traite le vrai défaut, et il le traite en rendant la
/// **suppression** obligatoire : on ne garde pas un doublon, on le retire.
class InventaireDesSessionsTest {

    private static final Path SESSIONS = Path.of("dev-docs", "recette", "sessions");

    private static final Path INDEX = Path.of("dev-docs", "recette", "index.md");

    /// Une ligne du tableau d'inventaire : `| S9 | [Installer...](sessions/s9-....md) ... | ... |`.
    private static final Pattern LIGNE = Pattern.compile("^\\| (S\\d+) \\|(.*)$", Pattern.MULTILINE);

    /// Un script de session, `s9-installer-mettre-a-jour.md`. Les passes ciblées (`passe-*.md`) n'en
    /// sont pas : le tableau les traite à part, et la page le dit.
    private static final Pattern SCRIPT = Pattern.compile("^s(\\d+)-.*\\.md$");

    /// Un renvoi vers un script de session, `sessions/s9-installer-mettre-a-jour.md`. Les passes
    /// ciblées, `sessions/passe-*.md`, n'y répondent pas : elles ne sont pas au tableau, donc leurs
    /// renvois ont leur place dans le corps de la page.
    private static final Pattern LIEN_SCRIPT = Pattern.compile("sessions/s\\d+-[^)\\s]*\\.md");

    @Test
    @DisplayName("Chaque script de session a sa ligne au tableau, et cette ligne y mène")
    void chaque_script_a_sa_ligne_et_son_lien() {
        Map<String, String> tableau = lignesDuTableau();
        List<String> scripts = scriptsDuDossier();

        assertThat(scripts)
                .as("Aucun script de session trouvé sous %s : ce garde ne garderait rien.", SESSIONS)
                .isNotEmpty();

        SoftAssertions verifs = new SoftAssertions();

        List<String> sansLigne = scripts.stream()
                .filter(script -> !tableau.containsKey(identifiant(script)))
                .toList();
        verifs.assertThat(sansLigne)
                .as(
                        "Ces scripts existent sous %s et aucune ligne du tableau de %s ne les"
                                + " annonce : la session est écrite et l'inventaire l'ignore.",
                        SESSIONS, INDEX)
                .isEmpty();

        List<String> sansLien = scripts.stream()
                .filter(script -> tableau.containsKey(identifiant(script)))
                .filter(script -> !tableau.get(identifiant(script)).contains("sessions/" + script))
                .toList();
        verifs.assertThat(sansLien)
                .as("Ces scripts ont bien une ligne, mais elle ne renvoie pas à eux. Un inventaire"
                        + " qui nomme sans mener oblige à deviner le chemin, et c'est ainsi"
                        + " qu'une session finit par exister sans que personne ne la joue.")
                .isEmpty();

        verifs.assertAll();
    }

    @Test
    @DisplayName("Chaque ligne du tableau désigne un script qui existe")
    void chaque_ligne_designe_un_script_existant() {
        Map<String, String> tableau = lignesDuTableau();
        List<String> identifiants = scriptsDuDossier().stream()
                .map(InventaireDesSessionsTest::identifiant)
                .toList();

        assertThat(tableau)
                .as(
                        "Aucune ligne `| Sx |` lue dans %s : le tableau a changé de forme, et ce garde ne"
                                + " garde plus rien.",
                        INDEX)
                .isNotEmpty();

        List<String> orphelines = tableau.keySet().stream()
                .filter(session -> !identifiants.contains(session))
                .toList();
        assertThat(orphelines)
                .as(
                        "Ces lignes annoncent une session dont le script n'existe pas sous %s. Une ligne"
                                + " qui survit à son fichier promet un document qu'on ne trouvera pas.",
                        SESSIONS)
                .isEmpty();
    }

    @Test
    @DisplayName("Aucune session n'est énumérée ailleurs que dans le tableau")
    void le_tableau_est_le_seul_inventaire() {
        // ⚠️ Le devoir qui manquait. Les deux précédents vérifient que le tableau est COMPLET ; ils
        // seraient restés verts pendant que la page portait, plus bas, deux autres listes de
        // sessions - dont une qui s'arrêtait à huit sur dix et une autre à cinq sur dix. Une copie
        // incomplète ne se répare pas, elle se supprime : ce test rend la suppression obligatoire.
        List<String> horsTableau = lignesCitantUnScript();

        assertThat(horsTableau)
                .as(
                        "Ces lignes de %s renvoient à un script de session hors du tableau d'inventaire."
                                + " Chacune est une seconde liste qui dérivera de la première : c'est ainsi que"
                                + " « les huit sessions existent » a survécu à l'arrivée de la dixième."
                                + " Renvoyez au tableau plutôt que de répéter la liste. Les passes ciblées"
                                + " (`sessions/passe-*.md`) restent libres : le tableau ne les porte pas.",
                        INDEX)
                .isEmpty();
    }

    /// Les lignes qui renvoient à un script `sNN-*.md` sans être une ligne du tableau.
    private static List<String> lignesCitantUnScript() {
        return lire(INDEX)
                .lines()
                .filter(ligne -> LIEN_SCRIPT.matcher(ligne).find())
                .filter(ligne -> !ligne.startsWith("| S"))
                .map(String::strip)
                .toList();
    }

    /// `s9-installer-mettre-a-jour.md` donne `S9`.
    private static String identifiant(String script) {
        Matcher m = SCRIPT.matcher(script);
        if (!m.matches()) {
            throw new IllegalArgumentException("nom de script inattendu : " + script);
        }
        return "S" + m.group(1);
    }

    /// Les lignes du tableau, par identifiant de session, associées au reste de la ligne.
    private static Map<String, String> lignesDuTableau() {
        Map<String, String> lignes = new TreeMap<>();
        Matcher m = LIGNE.matcher(lire(INDEX));
        while (m.find()) {
            lignes.put(m.group(1), m.group(2));
        }
        return lignes;
    }

    private static List<String> scriptsDuDossier() {
        try (Stream<Path> fichiers = Files.list(SESSIONS)) {
            return fichiers.map(fichier -> fichier.getFileName().toString())
                    .filter(nom -> SCRIPT.matcher(nom).matches())
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + SESSIONS, echec);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new UncheckedIOException("lecture de " + fichier, echec);
        }
    }
}
