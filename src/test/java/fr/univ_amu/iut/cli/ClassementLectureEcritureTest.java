package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.commande.CommandeRacine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/// Toute commande CLI est **classée** : lectrice ou écrivaine (#3498).
///
/// Le verrou du dossier de travail est pris par défaut ; seules les commandes portant [LectureSeule]
/// s'en dispensent. Une commande nouvelle arrive donc protégée - mais son autrice n'aura peut-être
/// jamais su que la question se posait, et une commande de lecture verrouillée est une gêne qu'on
/// découvre en production.
///
/// Ce garde ne dit **pas** si le classement est juste : aucun signal statique ne le sait
/// (`supprimer-site` n'atteint aucune primitive d'écriture par un chemin qu'une analyse d'appels
/// retrouve, `lister-carres` en atteint une, légitimement, pour écrire son CSV hors du dossier de
/// travail). Il dit qu'un **choix a été fait** : la commande porte le marqueur, ou elle est écrite
/// noir sur blanc ci-dessous. C'est l'oubli silencieux qu'il rend impossible, pas l'erreur de
/// jugement - celle-là se corrige en revue, et se paie du bon côté ([LectureSeule]).
///
/// Le cliquet est un **compteur**, et non la liste des écrivaines qu'il portait d'abord (#3575).
/// Cette liste était **intégralement dérivable** : les deux assertions d'alors se refermaient l'une sur
/// l'autre - toute commande non marquée devait y figurer, et elle ne pouvait contenir que des commandes
/// non marquées - si bien qu'elle valait exactement l'ensemble que le code déclare déjà. Ce qui force
/// la décision n'est pas la liste, c'est le **message**, qui nomme la commande fautive.
class ClassementLectureEcritureTest {

    /// Combien de commandes prennent le verrou, c'est-à-dire n'ont **pas** été déclarées lectrices.
    ///
    /// Un cliquet, pas un inventaire : il ne dit rien de plus que le code, il oblige seulement à
    /// trancher quand la surface bouge. Le chiffre se met à jour **en même temps** que la décision.
    /// 45 à 49 par les quatre gestes de l'emport (#4729) : les quatre gardent le verrou, y compris
    /// `emporter-nuit` et `renvoyer-avis` qui n'écrivent que hors du dossier de travail. Un paquet
    /// bâti pendant qu'un autre processus écrit porterait une sélection à demi lue.
    private static final int ECRIVAINES_ATTENDUES = 49;

    @Test
    @DisplayName("le nombre de commandes qui prennent le verrou est celui qu'on a décidé")
    void aucune_commande_n_est_sans_classement() {
        List<String> ecrivaines = new ArrayList<>();
        for (Class<?> commande : toutesLesCommandes()) {
            if (!LectureSeule.class.isAssignableFrom(commande)) {
                ecrivaines.add(nomDe(commande) + " (" + commande.getSimpleName() + ")");
            }
        }

        // Les noms sont portés par la collection, qu'AssertJ affiche en cas d'échec : les répéter dans
        // le message doublerait une liste de quarante-cinq entrées et rendrait le reproche illisible.
        assertThat(ecrivaines)
                .as("une commande nouvelle doit trancher : si elle ne touche pas au dossier de travail,"
                        + " lui faire porter LectureSeule ; sinon monter ce compteur, ici même, en sachant"
                        + " ce qu'on fait. Le verrou la protège déjà - c'est la lecture verrouillée à tort"
                        + " qu'on évite")
                .hasSize(ECRIVAINES_ATTENDUES);
    }

    /// Toutes les commandes câblées, groupes imbriqués compris : `api` porte deux filles, et c'est la
    /// **feuille** que la CLI interroge pour décider du verrou.
    private static Set<Class<?>> toutesLesCommandes() {
        Set<Class<?>> trouvees = new LinkedHashSet<>();
        empiler(CommandeRacine.class, trouvees);
        return trouvees;
    }

    private static void empiler(Class<?> commande, Set<Class<?>> trouvees) {
        for (Class<?> fille : commande.getAnnotation(CommandLine.Command.class).subcommands()) {
            if (trouvees.add(fille)) {
                empiler(fille, trouvees);
            }
        }
    }

    private static String nomDe(Class<?> commande) {
        return commande.getAnnotation(CommandLine.Command.class).name();
    }
}
