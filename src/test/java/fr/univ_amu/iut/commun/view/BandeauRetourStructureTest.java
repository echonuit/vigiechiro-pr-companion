package fr.univ_amu.iut.commun.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/// Garde-fou **structurel** des bandeaux de retour (#1980) : tout écran qui en déclare un le déclare
/// **complet**, avec les trois identifiants attendus par [BandeauRetour#installer].
///
/// Il complète, sans le remplacer, le contrat de comportement de `BandeauRetourTest` : celui-ci prouve
/// ce que fait le composant, celui-ci prouve que chaque écran lui donne de quoi travailler. Un `fx:id`
/// manquant fait échouer le chargement FXML à l'exécution - donc en production ou dans le test de vue
/// de l'écran concerné, s'il en a un. Ici, l'écart se voit **sans ouvrir l'écran**, et surtout il se
/// voit pour le **treizième** écran, celui que personne n'aura pensé à tester.
///
/// Ce garde-fou n'a été possible qu'après l'unification des identifiants : le libellé du bandeau
/// s'appelait `lblMessage`, `messageErreur`, `lblErreur`, `lblCompteRendu` ou `lblExport` selon
/// l'écran, et trois de ces noms **mentaient** sur ce que le bandeau porte depuis l'EPIC #1870 - un
/// canal nommé « erreur » y affiche aussi des succès et des guidages.
class BandeauRetourStructureTest {

    private static final Path SOURCES = Path.of("src/main/java/fr/univ_amu/iut");

    /// Les trois identifiants que `BandeauRetour.installer` exige, dans l'ordre de ses paramètres.
    private static final List<String> IDENTIFIANTS = List.of("bandeauRetour", "lblRetour", "btnFermerRetour");

    /// Toutes les vues qui déclarent un bandeau, découvertes plutôt qu'énumérées : une liste écrite à la
    /// main serait périmée dès le prochain écran migré, et c'est précisément lui qu'on veut couvrir.
    static Stream<Path> vuesAvecBandeau() throws IOException {
        try (Stream<Path> fichiers = Files.walk(SOURCES)) {
            return fichiers
                    .filter(p -> p.toString().endsWith(".fxml"))
                    .filter(BandeauRetourStructureTest::declareUnBandeau)
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    private static boolean declareUnBandeau(Path fxml) {
        return lire(fxml).contains("styleClass=\"bandeau-retour\"");
    }

    private static String lire(Path fxml) {
        try {
            return Files.readString(fxml, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new IllegalStateException("Lecture impossible : " + fxml, echec);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vuesAvecBandeau")
    @DisplayName("Un écran qui déclare un bandeau déclare ses trois identifiants")
    void un_bandeau_declare_ses_trois_identifiants(Path fxml) {
        String contenu = lire(fxml);

        assertThat(IDENTIFIANTS)
                .allSatisfy(identifiant -> assertThat(contenu)
                        .as(
                                "%s : identifiant « %s » attendu par BandeauRetour.installer",
                                fxml.getFileName(), identifiant)
                        .contains("fx:id=\"" + identifiant + "\""));
    }

    @Test
    @DisplayName("Aucun écran ne garde un ancien nom de libellé de bandeau")
    void aucun_ancien_nom_ne_subsiste() throws IOException {
        // Les noms d'avant l'unification. Trois d'entre eux annonçaient une erreur sur un canal qui porte
        // aussi des succès et des guidages : les laisser réapparaître, c'est rouvrir la confusion que
        // l'EPIC #1870 a fermée (ADR 0028).
        List<String> ancienNoms = List.of("messageErreur", "lblErreur", "lblCompteRendu", "lblExport");

        try (Stream<Path> vues = vuesAvecBandeau()) {
            assertThat(vues).allSatisfy(fxml -> {
                String contenu = lire(fxml);
                assertThat(ancienNoms)
                        .allSatisfy(ancien -> assertThat(contenu)
                                .as(
                                        "%s porte un bandeau : son libellé doit s'appeler lblRetour, pas %s",
                                        fxml.getFileName(), ancien)
                                .doesNotContain("fx:id=\"" + ancien + "\""));
            });
        }
    }

    @Test
    @DisplayName("Le garde-fou couvre bien tous les écrans porteurs, pas un sous-ensemble")
    void le_garde_fou_couvre_tous_les_ecrans() throws IOException {
        // Sans ce contrôle, une découverte cassée (mauvais chemin, extension mal filtrée) rendrait la
        // suite verte en ne vérifiant rien du tout.
        try (Stream<Path> vues = vuesAvecBandeau()) {
            assertThat(vues)
                    .as("l'EPIC #1870 en a migré douze ; ce nombre ne peut que croître")
                    .hasSizeGreaterThanOrEqualTo(12);
        }
    }
}
