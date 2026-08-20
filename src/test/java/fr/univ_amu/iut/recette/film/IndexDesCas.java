package fr.univ_amu.iut.recette.film;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.extension.ExtensionContext;

/// L'index qui se lit par CAS, écrit une fois la session JUnit terminée.
///
/// Il tient le même rôle que celui du script, à un point près : la colonne « comment auditer »
/// ne se déduit plus d'une part d'images claires, mais du fait qu'une fenêtre a paru, mesuré par la
/// caméra. La frontière reste « quelque chose a paru, ou rien », mais elle est CONSTATÉE au lieu
/// d'être inférée.
public final class IndexDesCas implements ExtensionContext.Store.CloseableResource {

    public record Ligne(String cas, String test, String clip, boolean fenetreVue) {
        String commentAuditer() {
            return fenetreVue ? "en regardant" : "en lisant le test";
        }
    }

    private static final String ENTETE = """
            # Cas filmés

            Un clip par **test**, parce que c'est ce que la JVM sait borner ; cet index se lit par
            **cas**, parce que c'est ce qu'on cherche. Un cas couvert par plusieurs tests a donc
            plusieurs lignes.

            ## Comment auditer : en regardant, ou en lisant

            Tous les tests qui citent un cas n'ouvrent pas de fenêtre. Un ViewModel en cite et ne
            montre rien : son clip s'arrête à son carton, et c'est le résultat **juste**. La
            dernière colonne dit, pour chaque ligne, par quel moyen le cas s'audite.

            | Cas | Clip | Ce qu'il joue | Comment l'auditer |
            |---|---|---|---|
            """;

    private final Path fichier;
    private final List<Ligne> lignes = Collections.synchronizedList(new ArrayList<>());

    public IndexDesCas(Path fichier) {
        this.fichier = fichier;
    }

    public void ajouter(Ligne ligne) {
        lignes.add(ligne);
    }

    @Override
    public void close() throws IOException {
        if (lignes.isEmpty()) {
            return;
        }
        StringBuilder page = new StringBuilder(ENTETE);
        lignes.stream()
                .sorted((a, b) -> a.cas().compareTo(b.cas()))
                .forEach(l -> page.append(
                        String.format("| %s | `%s` | %s | %s |%n", l.cas(), l.clip(), l.test(), l.commentAuditer())));
        Files.createDirectories(fichier.getParent());
        Files.writeString(fichier, page.toString(), StandardCharsets.UTF_8);
        long aRegarder = lignes.stream().filter(Ligne::fenetreVue).count();
        System.out.printf("  index : %d ligne(s) de cas dont %d à regarder -> %s%n", lignes.size(), aRegarder, fichier);
    }

    void ecrireMaintenant() {
        try {
            close();
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }
}
