package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage;
import fr.univ_amu.iut.passage.model.ServiceArchivagePassage.BilanArchivage;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `archiver` (#1304, EPIC #1297) : **archive un passage** en ligne de commande, équivalent de l'action
/// « Archiver ce passage » de M-Passage (#1300). Purge l'audio local (séquences et bruts) pour libérer
/// de l'espace ; les observations, les validations, le journal et le relevé **restent consultables**.
///
/// **Destructif, donc jamais implicite** : sans `--confirmer`, la commande se contente d'**annoncer**
/// ce qu'elle libérerait (et combien de séquences verraient leur empreinte capturée in extremis) puis
/// sort en `2` (invocation incomplète), sans rien supprimer. C'est le pendant scriptable de la
/// confirmation de l'IHM.
///
/// Mêmes règles que l'IHM : seul un passage **déposé** s'archive (le service refuse les autres, code `1`).
@Command(
        name = "archiver",
        description = "Archive un passage : purge son audio local (séquences et bruts) pour libérer de l'espace. "
                + "Les observations et validations restent consultables ; l'écoute redevient possible en "
                + "réimportant les fichiers d'origine (voir « reactiver »). Destructif : --confirmer requis.")
public final class Archiver implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant technique du passage à archiver (doit être déposé).")
    private Long idPassage;

    @Option(
            names = "--confirmer",
            description = "Exécute réellement la purge. Sans cette option, la commande annonce seulement "
                    + "l'espace récupérable et sort en 2, sans rien supprimer.")
    private boolean confirmer;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma
    // (cf. Auditer). On résout donc paresseusement, à l'exécution de la commande.
    private final Provider<ServiceArchivagePassage> service;

    @Inject
    public Archiver(Provider<ServiceArchivagePassage> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        ServiceArchivagePassage archivage = service.get();
        long recuperable = archivage.volumeRecuperable(idPassage);
        int sansEmpreinte = archivage.sequencesSansEmpreinte(idPassage);

        if (!confirmer) {
            sortie.println("Archivage du passage " + idPassage + " : environ " + Formats.octetsLisibles(recuperable)
                    + " seraient libérés.");
            sortie.println("L'audio (séquences et bruts) serait supprimé du disque ; les observations, les "
                    + "validations, le journal et le relevé resteraient consultables.");
            if (sansEmpreinte > 0) {
                sortie.println("L'empreinte de " + sansEmpreinte + " séquence(s) serait capturée avant la "
                        + "suppression, pour reconnaître à coup sûr les fichiers réimportés.");
            }
            sortie.println("Pour réécouter un jour, il faudra réimporter les mêmes fichiers : la plateforme "
                    + "VigieChiro ne rend pas l'audio d'un dépôt ZIP.");
            sortie.println("Relancez avec --confirmer pour archiver réellement.");
            return CODE_NON_CONFIRME;
        }

        // Lève RegleMetierException (passage introuvable, jamais importé, non déposé) → code 1.
        BilanArchivage bilan = archivage.archiver(idPassage);
        sortie.println("Passage " + idPassage + " archivé : " + Formats.octetsLisibles(bilan.octetsLiberes())
                + " libéré(s), " + bilan.empreintesCapturees() + " empreinte(s) capturée(s) avant la purge.");
        sortie.println("Les observations et validations restent consultables ; « reactiver » remettra l'audio "
                + "en place à partir des fichiers d'origine.");
        return 0;
    }

    /// Code de sortie d'une invocation **sans** `--confirmer` : rien n'a été fait, l'invocation est
    /// incomplète (même code que les erreurs d'usage du CLI).
    private static final int CODE_NON_CONFIRME = 2;
}
