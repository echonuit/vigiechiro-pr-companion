package fr.univ_amu.iut.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.cli.commande.CommandeRacine;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
/// ⚠️ Ce garde ne dit **pas** si le classement est juste : aucun signal statique ne le sait
/// (`supprimer-site` n'atteint aucune primitive d'écriture par un chemin qu'une analyse d'appels
/// retrouve, `lister-carres` en atteint une, légitimement, pour écrire son CSV hors du dossier de
/// travail). Il dit qu'un **choix a été fait** : la commande porte le marqueur, ou elle est écrite
/// noir sur blanc ci-dessous. C'est l'oubli silencieux qu'il rend impossible, pas l'erreur de
/// jugement - celle-là se corrige en revue, et se paie du bon côté ([LectureSeule]).
class ClassementLectureEcritureTest {

    /// Les commandes qui **écrivent** dans le dossier de travail, et prennent donc le verrou.
    ///
    /// Deux d'entre elles ne se devinent pas au nom : `metadonnees-passage` rattrape et envoie des
    /// métadonnées, et `emplacements --reinitialiser` réécrit la configuration d'amorçage.
    private static final Set<String> ECRIVAINS = Set.of(
            "ajouter-point",
            "constituer-selection",
            "corriger-observations",
            "creer-campagne",
            "creer-site",
            "deposer",
            "deposer-vigiechiro",
            "discussion",
            "emplacements",
            "exporter-activite",
            "exporter-lot",
            "exporter-observations",
            "exporter-sons",
            "exporter-vu",
            "importer",
            "importer-tadarida",
            "importer-transformes",
            "importer-vigiechiro",
            "lancer-traitement-vigiechiro",
            "marquer-douteux",
            "marquer-reference",
            "metadonnees-passage",
            "modifier-campagne",
            "modifier-point",
            "modifier-site",
            "poser-certitude",
            "publier-corrections-vigiechiro",
            "qualifier",
            "qualifier-fichier",
            "rattacher-campagne",
            "rattraper-communes",
            "reactiver",
            "reconstruire-passage",
            "recuperer-vigiechiro",
            "reinitialiser-depot",
            "reset-guide",
            "restaurer",
            "retro-empreintes",
            "sauvegarder",
            "supprimer-campagne",
            "supprimer-passage",
            "supprimer-sauvegarde",
            "supprimer-site",
            "traiter-passages",
            "valider-observations");

    @Test
    @DisplayName("chaque commande est déclarée lectrice, ou inscrite parmi les écrivaines")
    void aucune_commande_n_est_sans_classement() {
        List<String> sansClassement = new ArrayList<>();
        for (Class<?> commande : toutesLesCommandes()) {
            String nom = nomDe(commande);
            if (!LectureSeule.class.isAssignableFrom(commande) && !ECRIVAINS.contains(nom)) {
                sansClassement.add(nom + " (" + commande.getSimpleName() + ")");
            }
        }
        assertThat(sansClassement)
                .as("une commande nouvelle doit trancher : si elle ne touche pas au dossier de travail,"
                        + " lui faire porter LectureSeule ; sinon l'inscrire dans ECRIVAINS, ici même."
                        + " Le verrou la protège déjà - c'est la lecture verrouillée à tort qu'on évite")
                .isEmpty();
    }

    @Test
    @DisplayName("la liste des écrivaines ne garde pas de commande disparue ou devenue lectrice")
    void la_liste_des_ecrivaines_ne_ment_pas() {
        Set<String> nomsReels = new TreeSet<>();
        for (Class<?> commande : toutesLesCommandes()) {
            if (!LectureSeule.class.isAssignableFrom(commande)) {
                nomsReels.add(nomDe(commande));
            }
        }
        assertThat(ECRIVAINS)
                .as("une entrée qui ne correspond plus à rien ferait passer pour classée une commande"
                        + " renommée - le garde dirait vert sur une surface qu'il ne décrit plus")
                .isSubsetOf(nomsReels);
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
