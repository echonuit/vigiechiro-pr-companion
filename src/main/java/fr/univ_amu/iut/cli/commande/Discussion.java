package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.validation.model.MessageObservation;
import fr.univ_amu.iut.validation.model.PublicationMessage;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Commande `discussion` (#1418) : le **fil d'échange avec le validateur** du MNHN sur une observation :
/// le lire, et y répondre.
///
/// Parité CLI de ce que « Sons & validation » offre à l'écran depuis #1417 / #1418. Sans elle, deux
/// capacités métier n'existeraient que d'un seul côté.
///
/// ⚠️ **Répondre est définitif.** Le serveur ajoute par `$push` et n'offre **aucune** route de suppression
/// ni de modification : un message posté ne se retire pas, et il est lu par un expert du MNHN. D'où
/// `--confirmer`, **obligatoire** : on n'écrit pas l'irréversible par une option qu'on aurait pu laisser
/// traîner dans un script.
///
/// Sans `--message`, la commande **lit** : lecture seule, sans réseau (le fil vient de la base, rafraîchi
/// à chaque import).
@Command(
        name = "discussion",
        description = "Lit le fil de discussion d'une observation avec le validateur, et permet d'y répondre "
                + "(--message, écriture DÉFINITIVE : exige --confirmer).")
public final class Discussion implements Callable<Integer> {

    /// Code de sortie d'un envoi refusé : distinct du succès (0) et de l'échec d'exécution (1).
    private static final int CODE_REFUS = 2;

    private static final DateTimeFormatter QUAND = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH);

    @Option(
            names = "--observation",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant de l'observation dont lire (ou alimenter) le fil.")
    private long observation;

    @Option(
            names = "--message",
            paramLabel = "<texte>",
            description = "Texte à poster. ÉCRITURE DÉFINITIVE : le message ne pourra pas être supprimé "
                    + "ni modifié. Exige --confirmer. Sans cette option, la commande se contente de lire.")
    private String message;

    @Option(
            names = "--confirmer",
            description = "Obligatoire avec --message : atteste que l'envoi irréversible est voulu.")
    private boolean confirmer;

    @Spec
    private CommandSpec spec;

    // Providers, non instances : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceValidation> validation;
    private final Provider<Optional<PublicationMessage>> publication;

    @Inject
    public Discussion(Provider<ServiceValidation> validation, Provider<Optional<PublicationMessage>> publication) {
        this.validation = Objects.requireNonNull(validation, "validation");
        this.publication = Objects.requireNonNull(publication, "publication");
    }

    @Override
    public Integer call() {
        return message == null ? lire() : poster();
    }

    /// Sans `--message` : le fil, en lecture seule.
    private Integer lire() {
        PrintWriter sortie = spec.commandLine().getOut();
        List<MessageObservation> fil = validation.get().filDeLObservation(observation);
        if (fil.isEmpty()) {
            sortie.println("Aucun message : personne n'a encore ouvert de discussion sur cette détection.");
            return 0;
        }
        for (MessageObservation ligne : fil) {
            sortie.println(entete(ligne) + " : " + ligne.texte());
        }
        return 0;
    }

    /// Avec `--message` : l'envoi. `--confirmer` est **obligatoire**, le message ne se retire pas.
    private Integer poster() {
        PrintWriter sortie = spec.commandLine().getOut();
        if (!confirmer) {
            spec.commandLine()
                    .getErr()
                    .println("Ce message sera visible par le validateur du MNHN et ne pourra PAS être supprimé"
                            + " ni modifié. Ajoutez --confirmer pour l'assumer.");
            return CODE_REFUS;
        }
        Optional<PublicationMessage> envoi = publication.get();
        if (envoi.isEmpty()) {
            spec.commandLine()
                    .getErr()
                    .println("L'envoi de messages au validateur est désactivé (fonctionnalité"
                            + " « discuter-validateur »).");
            return CODE_REFUS;
        }

        ReponseApi<String> reponse = envoi.get().poster(observation, message);
        Optional<String> echec = reponse.echec();
        if (echec.isPresent()) {
            spec.commandLine().getErr().println("Message non envoyé : " + echec.get() + "\nRien n'a été publié.");
            return CODE_REFUS;
        }
        sortie.println("Message envoyé au validateur. Il est définitif : il ne peut plus être retiré.");
        return 0;
    }

    /// « Vous · 11/07/2026 21:04 », l'auteur est un identifiant plateforme, jamais un nom : on ne peut que
    /// dire s'il est le nôtre. En CLI, on affiche l'identifiant brut plutôt que d'inventer une identité.
    private static String entete(MessageObservation ligne) {
        String auteur = ligne.auteur() == null ? "auteur inconnu" : ligne.auteur();
        return ligne.date() == null
                ? auteur
                : auteur + " · " + QUAND.format(ligne.date().atZone(ZoneId.systemDefault()));
    }
}
