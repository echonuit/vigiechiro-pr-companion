package fr.univ_amu.iut.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Un libellé de navigation **calculé** porte son relibellage (#3702).
///
/// ## Ce qu'il empêche
///
/// Quinze écrans passent au `Navigateur` un libellé **constant** : il ne peut pas se périmer. Deux le
/// **dérivent de la donnée** - « Détails du passage N° X », « Carré N » - et les deux ont produit le
/// même défaut, à onze jours d'écart : renommer la nuit ou le carré laissait le fil d'Ariane et le
/// bouton ← Retour sur l'ancienne valeur (#3455, #3672).
///
/// L'arbitrage 3 du chantier #3536 refuse d'en faire une mécanique générale - deux sur dix-neuf ne la
/// justifient pas - et prévoit qu'un **troisième cas rouvre la décision**. Encore faut-il le remarquer :
/// #3672 n'a été trouvée ni par l'usage ni par un test, mais par un balayage. La fiche site ne déclarant
/// pas `EmplacementNavigation`, son fil et son bouton annonçaient **la même** valeur fausse ; rien ne se
/// contredisait à l'écran, donc rien n'alertait.
///
/// ## Ce qu'il ne prouve pas
///
/// Ni que le libellé est **juste**, ni que le relibellage est appelé **au bon moment**. Il vérifie que
/// les deux pièces existent : `libelleFil()` sur le contrôleur, `actualiserFil` sur la navigation. C'est
/// une promesse faible, et l'ADR 3540 demande de l'écrire plutôt que d'emprunter la solidité du voisin.
class LibelleDeNavigationTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /// Les deux entrées du `Navigateur` qui posent un libellé d'étape.
    private static final Pattern EMPILEMENT = Pattern.compile("\\.(empiler|ouvrirRacine)\\s*\\(");

    /// Le `Navigateur` les **déclare** ; il ne les appelle pas sur lui-même.
    private static final String SOCLE = "commun/view/Navigateur.java";

    @Test
    @DisplayName("#3702 : un libellé de navigation calculé porte libelleFil() et actualiserFil")
    void un_libelle_calcule_porte_son_relibellage() throws IOException {
        List<Empilement> tous = empilements();

        // Non-vacuité : un détecteur qui ne voit plus aucun empilement passerait au vert en silence.
        assertThat(tous)
                .as("le détecteur ne voit plus AUCUN empilement : c'est lui qui est cassé")
                .isNotEmpty();

        List<String> manquants = new ArrayList<>();
        for (Empilement empilement : tous) {
            if (empilement.libelleEstConstant()) {
                continue;
            }
            String source = lire(empilement.fichier());
            if (!source.contains("actualiserFil")) {
                manquants.add(empilement.fichier().getFileName() + " (pas d'`actualiserFil`)");
            }
            if (!contientLibelleFil(empilement.fichier())) {
                manquants.add(
                        empilement.fichier().getFileName() + " (aucun contrôleur voisin n'expose" + " `libelleFil()`)");
            }
        }

        assertThat(manquants)
                .as("un libellé de navigation **calculé** se périme quand la donnée change. L'écran doit"
                        + " exposer `String libelleFil()` et sa classe `Navigation*` un `actualiserFil` qui"
                        + " le repose. Cf. « Un libellé dérivé de la donnée se relibelle » dans la page"
                        + " Navigation, et l'arbitrage 3 de #3536 - un troisième cas rouvre la décision.")
                .isEmpty();
    }

    /// Un appel à `empiler` / `ouvrirRacine` et le libellé qu'il pose (troisième argument).
    private record Empilement(Path fichier, String libelle) {

        boolean libelleEstConstant() {
            return libelle.startsWith("\"");
        }
    }

    private static List<Empilement> empilements() throws IOException {
        List<Empilement> trouves = new ArrayList<>();
        for (Path chemin : fichiers()) {
            if (chemin.toString().replace('\\', '/').endsWith(SOCLE)) {
                continue;
            }
            String source = lire(chemin);
            Matcher appel = EMPILEMENT.matcher(source);
            while (appel.find()) {
                List<String> arguments = arguments(source, appel.end() - 1);
                if (arguments.size() >= 3) {
                    trouves.add(new Empilement(chemin, arguments.get(2)));
                }
            }
        }
        return trouves;
    }

    /// Découpe les arguments de **premier niveau**, parenthèses et chaînes comprises.
    ///
    /// ⚠️ Une expression régulière ne suffit pas, et l'ouverture de #3702 s'y est prise les doigts : un
    /// motif qui compte les virgules à plat rend le **deuxième** argument pour le troisième, et désigne
    /// onze sites innocents au lieu de deux. Un garde qui rougit sur dix-sept écrans corrects se fait
    /// désarmer dans la semaine.
    private static List<String> arguments(String texte, int debut) {
        List<String> arguments = new ArrayList<>();
        StringBuilder courant = new StringBuilder();
        int profondeur = 0;
        boolean dansChaine = false;
        boolean echappe = false;
        for (int i = debut; i < texte.length(); i++) {
            char lettre = texte.charAt(i);
            if (dansChaine) {
                courant.append(lettre);
                if (echappe) {
                    echappe = false;
                } else if (lettre == '\\') {
                    echappe = true;
                } else if (lettre == '"') {
                    dansChaine = false;
                }
                continue;
            }
            switch (lettre) {
                case '"' -> {
                    dansChaine = true;
                    courant.append(lettre);
                }
                case '(', '[', '{' -> {
                    profondeur++;
                    if (profondeur > 1) {
                        courant.append(lettre);
                    }
                }
                case ')', ']', '}' -> {
                    profondeur--;
                    if (profondeur == 0) {
                        arguments.add(courant.toString().strip());
                        return arguments;
                    }
                    courant.append(lettre);
                }
                case ',' -> {
                    if (profondeur == 1) {
                        arguments.add(courant.toString().strip());
                        courant.setLength(0);
                    } else {
                        courant.append(lettre);
                    }
                }
                default -> courant.append(lettre);
            }
        }
        return arguments;
    }

    /// Vrai si un contrôleur de la **même feature** expose `libelleFil()`. La navigation et son écran
    /// vivent dans le même paquet `view` : c'est là qu'on cherche, et nulle part ailleurs.
    private static boolean contientLibelleFil(Path navigation) {
        Path paquet = navigation.getParent();
        try (Stream<Path> voisins = Files.list(paquet)) {
            return voisins.filter(chemin -> chemin.toString().endsWith(".java"))
                    .anyMatch(chemin -> lire(chemin).contains("String libelleFil()"));
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }

    private static List<Path> fichiers() throws IOException {
        try (Stream<Path> arbre = Files.walk(SOURCES)) {
            return arbre.filter(chemin -> chemin.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        } catch (UncheckedIOException echec) {
            throw echec.getCause();
        }
    }

    private static String lire(Path chemin) {
        try {
            return Files.readString(chemin, StandardCharsets.UTF_8);
        } catch (IOException echec) {
            throw new UncheckedIOException(echec);
        }
    }
}
