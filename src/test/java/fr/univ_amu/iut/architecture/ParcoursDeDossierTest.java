package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Le cliquet du parcours d'arborescence (#3632).
///
/// ## Ce qu'il empêche
///
/// `Files.walk` **n'annonce pas** l'échec de parcours en `IOException` : il l'enveloppe dans une
/// `UncheckedIOException` levée **pendant l'itération** du flux. Comme elle n'hérite pas
/// d'`IOException`, tout `catch (IOException)` posé autour laisse passer **exactement le cas pour
/// lequel il a été écrit**, et toute méthode déclarant `throws IOException` laisse sortir une
/// exception d'un autre type.
///
/// Sept sites du produit portaient ce défaut, avec des conséquences opposées selon leur contrat : une
/// méthode dont le doc-comment promettait « ne lève jamais » levait depuis des `finally` - où une
/// exception **remplace** le résultat de l'opération - et une mesure d'affichage cassait là où son
/// commentaire promettait de compter zéro.
///
/// ## Pourquoi un cliquet plutôt qu'un helper unique
///
/// Interdire `Files.walk` au profit d'une seule méthode maison a été **écarté** : ces sept sites ont
/// sept contrats différents - l'un ne doit jamais lever, un autre doit compter zéro, quatre doivent
/// lever ce qu'ils annoncent. Les forcer dans un helper unique referait le défaut que l'ADR 3574 vient
/// de démêler pour l'effacement : **un seul nom pour des comportements opposés**.
///
/// Le cliquet n'exige donc qu'une chose : qui parcourt **rattrape ce que le parcours lève**.
class ParcoursDeDossierTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    @Test
    @DisplayName("#3632 : tout parcours d'arborescence rattrape l'échec que Files.walk lui lève")
    void tout_parcours_rattrape_l_echec_de_parcours() throws IOException {
        List<Path> parcourent = fichiersContenant("Files.walk(");

        // Non-vacuité : un détecteur qui ne trouve plus rien passe au vert sans dire pourquoi. Le jour
        // où le motif change ou le paquet bouge, c'est CETTE ligne qui parle, pas le silence d'une
        // liste vide comparée à une liste vide - la leçon de `PatronDuCliquetTest`.
        assertThat(parcourent)
                .as("le détecteur ne voit plus AUCUN parcours : c'est lui qui est cassé, pas le dépôt"
                        + " qui s'en serait débarrassé")
                .isNotEmpty();

        // ⚠️ Aucune exclusion, et c'est délibéré. `BancImport` est de l'outillage de performance exclu
        // du binaire livré (ADR 2746), donc un candidat naturel à la dispense - mais une garde sans
        // exception se relit sans se demander pourquoi celle-là, et le rattrapage y coûtait deux lignes.
        List<Path> sansRattrapage = parcourent.stream()
                .filter(chemin -> !contient(chemin, "catch (UncheckedIOException"))
                .toList();

        assertThat(sansRattrapage).as("""
                        Ces fichiers appellent `Files.walk` sans rattraper l'`UncheckedIOException`
                        qu'il lève sur un dossier illisible (#3632).

                        Elle n'hérite pas d'`IOException` : le `catch` voisin ne la voit pas, et une
                        méthode qui déclare `throws IOException` la laisse sortir d'un autre type.

                        Selon le contrat du site : la ramener au type annoncé
                        (`throw parcours.getCause()`), la rapporter sans lever, ou compter zéro.""").isEmpty();
    }

    private static List<Path> fichiersContenant(String motif) throws IOException {
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(chemin -> chemin.toString().endsWith(".java"))
                    .filter(chemin -> contient(chemin, motif))
                    .sorted()
                    .toList();
        }
    }

    private static boolean contient(Path fichier, String motif) {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8).contains(motif);
        } catch (IOException illisible) {
            return false;
        }
    }
}
