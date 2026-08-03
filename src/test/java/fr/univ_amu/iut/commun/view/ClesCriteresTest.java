package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Contrat des **clés de critères** (#3096) : une clé désigne **un seul concept**, et se déclare à un
/// seul endroit.
///
/// Une clé n'est pas un détail local. C'est le contrat de sérialisation des vues mémorisées
/// (`vue_sauvegardee`) et la base du transport d'un écran à l'autre (#476). Réécrite en littéral dans
/// chaque catalogue, elle diverge en silence : deux écrans finissent par nommer le même concept
/// différemment, ou pire, par nommer deux concepts pareil.
///
/// Le garde travaille sur les **sources** plutôt que par réflexion : ce qu'il faut interdire est
/// l'écriture d'un littéral, et un littéral ne se voit plus une fois compilé.
class ClesCriteresTest {

    /// Les catalogues de critères de l'application, un par écran à barre de filtres.
    private static final Path SOURCES = Path.of("src", "main", "java", "fr", "univ_amu", "iut");

    private static List<Path> catalogues() {
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            return fichiers.filter(chemin -> chemin.getFileName().toString().matches("Criteres\\w+\\.java"))
                    .sorted()
                    .toList();
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier);
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }

    /// Les clés publiées par le porteur commun, par nom de constante.
    private static Map<String, String> clesPartagees() {
        Map<String, String> cles = new LinkedHashMap<>();
        for (Field champ : ClesCriteres.class.getDeclaredFields()) {
            if (Modifier.isPublic(champ.getModifiers()) && champ.getType() == String.class) {
                try {
                    cles.put(champ.getName(), (String) champ.get(null));
                } catch (IllegalAccessException echec) {
                    throw new IllegalStateException(echec);
                }
            }
        }
        return cles;
    }

    @Test
    @DisplayName("#3096 : le porteur commun ne publie pas deux noms pour la même clé")
    void aucune_cle_publiee_deux_fois() {
        // Deux constantes de même valeur rendraient la collision indétectable : chaque écran croirait
        // nommer un concept distinct.
        Map<String, String> cles = clesPartagees();

        assertThat(cles.values()).as("clés publiées : %s", cles).doesNotHaveDuplicates();
        assertThat(cles)
                .as("le porteur doit publier au moins les clés réellement partagées")
                .isNotEmpty();
    }

    @Test
    @DisplayName("#3096 : aucun catalogue ne réécrit une clé partagée en littéral")
    void aucun_catalogue_ne_reecrit_une_cle_partagee() {
        // C'est le cœur du contrat. Tant qu'un catalogue peut écrire « "lieu" » à la main, rien
        // n'empêche un cinquième écran de nommer le même concept « lieux » ou « place », ni deux écrans
        // de nommer deux concepts « statut ». La garantie reposerait sur la vigilance.
        Map<String, String> cles = clesPartagees();
        SoftAssertions verifs = new SoftAssertions();

        for (Path catalogue : catalogues()) {
            String source = lire(catalogue);
            List<String> fautives = new ArrayList<>();
            for (Map.Entry<String, String> cle : cles.entrySet()) {
                if (source.contains('"' + cle.getValue() + '"')) {
                    fautives.add('"' + cle.getValue() + "\" (utiliser ClesCriteres." + cle.getKey() + ")");
                }
            }
            verifs.assertThat(fautives)
                    .as(
                            "%s réécrit des clés partagées en littéral : une clé est le contrat de"
                                    + " sérialisation des vues mémorisées, elle se déclare une fois",
                            catalogue)
                    .isEmpty();
        }
        verifs.assertAll();
    }
}
