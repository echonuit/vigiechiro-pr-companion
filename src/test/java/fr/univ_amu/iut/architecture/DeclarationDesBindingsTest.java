package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le cliquet des `Bindings.create…Binding` (#3547).
///
/// ## Ce qu'il empêche, et ce qu'il n'empêche pas
///
/// Un binding créé par `Bindings.createXBinding(calcul, dependances…)` ne se recalcule que si l'une des
/// `dependances` s'invalide, et **rien ne vérifie** que `calcul` n'en lit pas d'autres : ni le
/// compilateur, ni PMD, ni un test. Le manquement est silencieux tant qu'une propriété correctement
/// déclarée change juste après celle qui manque. Trois défauts de ce genre ont été trouvés dans le
/// chantier #3536 (#3546, #3548, #3752), tous invisibles jusqu'à ce qu'on ouvre le calcul.
///
/// Vérifier statiquement « lu ⊆ déclaré » a été **écarté** : il faudrait suivre les appels depuis la
/// lambda, ce qu'ArchUnit ne sait pas faire, et une heuristique textuelle produirait ses faux positifs
/// sur les références de méthode - c'est-à-dire exactement là où le défaut se cache.
///
/// Ce cliquet ne vérifie donc **aucune déclaration**. Il garantit une seule chose, mais elle manquait :
/// qu'un nouveau site ne puisse pas entrer dans le dépôt **sans être vu**. La revue du 14/08/2026 en a
/// ouvert 63 un par un ; elle en annonçait 62, et le 63ᵉ était arrivé deux jours après la rédaction de
/// l'issue, sans que rien ne le signale.
///
/// ## Que faire quand ce test rougit
///
/// Ouvrir le site ajouté et confronter ce que son calcul lit à ce qu'il déclare, **y compris dans les
/// méthodes qu'il appelle**. Puis mettre à jour le nombre ci-dessous. Bouger le nombre sans ouvrir le
/// site retire au cliquet sa seule utilité.
class DeclarationDesBindingsTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /// Relevé du 14/08/2026, revue #3547 : 63 sites, 32 fichiers, tous ouverts et jugés.
    ///
    /// **+1 le 16/08/2026 (#3806)** : `ModaleSiteController` compose le motif du grisage d'« Enregistrer »,
    /// qui a désormais deux causes distinctes. Son calcul lit `peutEnregistrer()` et
    /// `motifEnregistrementFerme()`, lequel lit à son tour `carreValide`, `enCreation` et
    /// `carreRecuperable`. Les trois derniers sont déclarés ; `carreValide` ne l'est pas **directement**,
    /// mais `peutEnregistrer` en dépend par construction (`carreValide.and(…)`), donc toute invalidation
    /// du carré invalide le binding. Déclaration jugée **complète**.
    private static final int SITES_RELEVES = 64;

    private static final Pattern APPEL = Pattern.compile("Bindings\\.create[A-Za-z]*Binding\\s*\\(");

    @Test
    @DisplayName("#3547 : tout nouveau Bindings.create…Binding force une relecture de ses dépendances")
    void tout_nouveau_binding_est_vu() throws IOException {
        List<Path> fichiers = fichiersContenantUnAppel();

        // Non-vacuité : un détecteur qui ne trouve plus rien passerait au vert sans rien dire. Le jour où
        // le motif change, c'est CETTE ligne qui parle, pas l'égalité de deux zéros.
        assertThat(fichiers)
                .as("le détecteur ne voit plus AUCUN binding : c'est lui qui est cassé, pas le dépôt qui"
                        + " s'en serait débarrassé")
                .isNotEmpty();

        int sites = fichiers.stream()
                .mapToInt(DeclarationDesBindingsTest::appelsDans)
                .sum();

        assertThat(sites)
                .as("un `Bindings.create…Binding` a été ajouté ou retiré. Ouvrez-le, confrontez ce que son"
                        + " calcul lit à ce qu'il déclare (méthodes appelées comprises), puis ajustez"
                        + " SITES_RELEVES. Cf. ADR 3547.")
                .isEqualTo(SITES_RELEVES);
    }

    private static List<Path> fichiersContenantUnAppel() throws IOException {
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> appelsDans(chemin) > 0)
                    .sorted()
                    .toList();
        } catch (java.io.UncheckedIOException echec) {
            throw echec.getCause();
        }
    }

    private static int appelsDans(Path fichier) {
        Matcher trouve = APPEL.matcher(lire(fichier));
        int nombre = 0;
        while (trouve.find()) {
            nombre++;
        }
        return nombre;
    }

    private static String lire(Path fichier) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new java.io.UncheckedIOException(echec);
        }
    }
}
