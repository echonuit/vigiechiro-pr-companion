package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// La police embarquée couvre-t-elle **tout ce que l'application affiche** ? (#3389)
///
/// ## Le défaut que ce cliquet empêche de revenir
///
/// [Typographie] embarque Noto Sans pour que le produit cesse de dépendre de la machine. Mais un
/// caractère **absent** de la fonte part en **repli** vers une police du système - et deux machines ne
/// replient pas sur la même. Le produit redevient dépendant de son hôte, par la petite porte, et
/// **rien ne le signale** : le glyphe s'affiche, simplement pas le même.
///
/// Huit caractères étaient dans ce cas au moment d'écrire ce garde : `→ ⚠ ☰ ≥ ≤ ▸ ← −`, dans une
/// quarantaine de messages - transitions de statut « X → Y », « Réglages ▸ Import », « Alt+← »,
/// « Archives de dépôt Tadarida (≤ 700 Mo) ». Ils ont été remplacés par des équivalents couverts ; les
/// pictogrammes l'ont été au titre de l'[ADR 0035] - un pictogramme d'IHM est une icône, pas un
/// caractère.
///
/// ## Ce qu'il ne regarde pas, et pourquoi
///
/// **Les sorties terminal.** Une commande écrit dans une console dont on ne maîtrise pas la police :
/// la fonte embarquée n'y joue aucun rôle. `TexteCompteRendu` le documente déjà pour ses marqueurs de
/// sévérité. Le périmètre exclut donc `cli/`, `perf/`, `commun/api/` et les journaux des outils de
/// capture, qui écrivent tous sur la sortie standard.
///
/// **Deux fichiers de règle.** `RetourOperation.GLYPHES_DE_SEVERITE` et les marqueurs de
/// `TexteCompteRendu` **sont** la donnée d'un garde ou d'un format : ces caractères y sont cités, pas
/// affichés.
class PoliceCouvreLIhmTest {

    /// Ce qui écrit sur un terminal, où la police embarquée n'a pas cours.
    private static final List<String> HORS_PERIMETRE =
            List.of("/cli/", "/perf/", "/commun/api/", "/outils/", "TexteCompteRendu", "RetourOperation");

    /// Les littéraux d'une source, commentaires ôtés : c'est du **texte affiché** qu'il s'agit.
    private static final Pattern COMMENTAIRE = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern LITTERAL = Pattern.compile("\"([^\"\\\\\\n]*)\"");

    @Test
    @DisplayName("#3389 : aucun caractère affiché par l'application n'échappe à la police embarquée")
    void la_police_couvre_tout_ce_qui_s_affiche() throws IOException, FontFormatException {
        java.awt.Font police;
        try (InputStream flux = Typographie.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            police = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, flux);
        }

        Set<String> fautifs = new TreeSet<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path source :
                    sources.filter(PoliceCouvreLIhmTest::estUneSourceDIhm).toList()) {
                releverLesNonCouverts(source, police, fautifs);
            }
        }

        assertThat(fautifs)
                .as("ces caractères ne sont pas dans Noto Sans embarquée : ils partiront en repli vers une "
                        + "police du système, donc deux utilisateurs ne verront pas le même glyphe - et rien "
                        + "ne le signalera. Choisir un équivalent couvert, ou une icône (ADR 0035). #3389")
                .isEmpty();
    }

    @Test
    @DisplayName("#3389 : le garde saurait voir un caractère non couvert - il en reconnaît un connu")
    void le_garde_detecte_bien_ce_qu_il_cherche() throws IOException, FontFormatException {
        // Sans cette vérification, le cas ci-dessus resterait vert si la police se mettait à tout
        // accepter (fichier remplacé, lecture qui échoue en silence) : il certifierait une couverture
        // qu'il ne sait plus constater.
        java.awt.Font police;
        try (InputStream flux = Typographie.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            police = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, flux);
        }

        assertThat(police.canDisplay('é'))
                .as("un caractère banal du français doit passer")
                .isTrue();
        assertThat(police.canDisplay('≤'))
                .as("« ≤ » est absent de cette fonte : c'est le cas qui a ouvert #3389")
                .isFalse();
    }

    private static boolean estUneSourceDIhm(Path source) {
        String chemin = source.toString().replace('\\', '/');
        if (!chemin.endsWith(".java") && !chemin.endsWith(".fxml")) {
            return false;
        }
        return HORS_PERIMETRE.stream().noneMatch(chemin::contains);
    }

    private static void releverLesNonCouverts(Path source, java.awt.Font police, Set<String> fautifs)
            throws IOException {
        String texte = Files.readString(source);
        if (source.toString().endsWith(".java")) {
            texte = COMMENTAIRE.matcher(texte).replaceAll("");
        }
        Matcher litteral = LITTERAL.matcher(texte);
        while (litteral.find()) {
            for (char caractere : litteral.group(1).toCharArray()) {
                if (caractere > 0x7F && !police.canDisplay(caractere)) {
                    fautifs.add(
                            String.format("U+%04X « %c » dans %s", (int) caractere, caractere, source.getFileName()));
                }
            }
        }
    }
}
