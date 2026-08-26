package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.commande.CommandeRacine;
import fr.univ_amu.iut.commun.model.Besoin;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;

/// Ce qu'un refus de la ligne de commande a le droit de conseiller (#3963).
///
/// ## Le défaut que ce fichier ferme
///
/// `GesteAttenduCli` conseillait « Connectez-vous avec « vigiechiro connexion --token <jeton> » ».
/// Cette commande **n'a jamais existé** : la frapper répond « Commande inconnue ». Le conseil menait
/// donc droit dans une erreur, sur trois commandes réseau qui lèvent ce besoin.
///
/// **Pourquoi aucun garde ne l'a vu.** Le test `bats` qui exerce ce chemin affirmait
/// `[[ "${output}" == *"jeton"* ]]` : il vérifiait un **mot**, pas un geste. La phrase fausse contenait
/// le mot, donc le garde était vert.
///
/// C'est l'ADR 3854 - un refus ne conseille que ce qu'il a vérifié applicable - côté ligne de commande.
class GesteAttenduCliTest {

    /// Toute mention de la forme « vigiechiro xxx » dans un message.
    private static final Pattern COMMANDE_CITEE = Pattern.compile("vigiechiro ([a-z][a-z0-9-]+)");

    @Test
    @DisplayName("#3963 : aucun message ne cite une commande que le binaire ne connaît pas")
    void aucune_commande_inventee() {
        Set<String> connues = sousCommandes();
        assertThat(connues)
                .as("le dispositif est cassé : aucune sous-commande lue, la vérification serait vide")
                .isNotEmpty();

        for (String message : tousLesMessages()) {
            Matcher citation = COMMANDE_CITEE.matcher(message);
            while (citation.find()) {
                assertThat(connues)
                        .as(
                                "« %s » est conseillée par « %s », et le binaire ne la connaît pas",
                                citation.group(1), message)
                        .contains(citation.group(1));
            }
        }
    }

    @Test
    @DisplayName("#3963 : le besoin de connexion nomme les trois voies réelles d'apport du jeton")
    void le_geste_de_connexion_est_celui_qui_existe() {
        String message = GesteAttenduCli.message(new RegleMetierException("Non connecté.", new Besoin.Connexion()));

        assertThat(message).contains("--token").contains("VIGIECHIRO_TOKEN").contains("dans l'application");
        assertThat(message)
                .as("la commande « connexion » n'existe pas : la conseiller mène à « Commande inconnue »")
                .doesNotContain("vigiechiro connexion");
    }

    @Test
    @DisplayName("#3963 : l'application se désigne « menu principal », jamais par un pictogramme")
    void l_application_se_designe_comme_partout_ailleurs() {
        // Même raison que l'ADR 3470 côté message d'incident : deux désignations pour une seule entrée
        // obligent le lecteur à deviner qu'il s'agit du même endroit.
        assertThat(tousLesMessages()).allSatisfy(message -> assertThat(message).doesNotContain("☰"));
    }

    /// Les messages que cette classe sait produire, un par famille de besoin.
    private static List<String> tousLesMessages() {
        return Stream.of(new Besoin.Connexion(), new Besoin.Fonctionnalite("Recherche globale"))
                .map(besoin -> GesteAttenduCli.message(new RegleMetierException("Geste impossible.", besoin)))
                .toList();
    }

    /// Les noms de sous-commandes **déclarés** sur la racine, lus à l'annotation.
    ///
    /// Par réflexion et non par `new CommandLine(...)` : construire la ligne réelle demanderait
    /// l'injecteur complet, quand la seule chose qui nous intéresse ici est la liste des noms.
    private static Set<String> sousCommandes() {
        Set<String> noms = new LinkedHashSet<>();
        collecter(CommandeRacine.class, noms);
        return noms;
    }

    private static void collecter(Class<?> commande, Set<String> noms) {
        Command declaration = commande.getAnnotation(Command.class);
        if (declaration == null) {
            return;
        }
        if (!declaration.name().isBlank()) {
            noms.add(declaration.name());
        }
        Arrays.stream(declaration.subcommands()).forEach(fille -> collecter(fille, noms));
    }
}
