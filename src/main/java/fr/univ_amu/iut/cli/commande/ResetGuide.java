package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.audit.model.BilanRecuperabilite;
import fr.univ_amu.iut.audit.model.RecuperabiliteNuit;
import fr.univ_amu.iut.audit.model.ResultatReset;
import fr.univ_amu.iut.audit.model.ServiceRecuperabilite;
import fr.univ_amu.iut.audit.model.ServiceReset;
import fr.univ_amu.iut.audit.model.SourceAudio;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Commande `reset-guide` (#1151) : **ce que vous perdriez si vous repartiez d'une base neuve**, nuit par
/// nuit, avant d'y toucher.
///
/// La procédure de reset se compose de gestes qui existent déjà et qui sont testés :
///
/// 1. `sauvegarder --complet` — base **et** audio (#1346) ;
/// 2. `exporter-observations` — les validations ;
/// 3. base neuve ;
/// 4. `recuperer-vigiechiro` puis `reconstruire-passage` — métadonnées, sites, points, observations
///    reviennent du serveur (#1050, prouvé de bout en bout) ;
/// 5. réimport de l'audio : depuis le disque (`importer`), ou `reactiver` pour un passage archivé ;
/// 6. `audit-coherence` — le workspace doit être sain.
///
/// Ce que la procédure **ne dit pas d'elle-même**, et que cette commande dit : *l'étape 5 va-t-elle
/// aboutir ?* Les métadonnées et les observations reviennent toujours. **L'audio, non.** Une nuit déposée
/// en **ZIP** (le mode par défaut) ne laisse **aucun** audio côté serveur : si le disque ne l'a plus, il
/// est perdu.
///
/// D'où cette commande, **en lecture seule** : elle établit le bilan de récupérabilité *avant* qu'on
/// écrive quoi que ce soit. Code de sortie **2** s'il existe au moins une nuit en « perdu » — un script
/// peut ainsi refuser d'enchaîner.
///
/// « Perdu » n'est pas une impasse (#1297) : la nuit deviendra un **passage archivé**, consultable et non
/// écoutable, réactivable si les fichiers réapparaissent. Mais c'est une perte, et elle doit être dite
/// **avant**.
@Command(
        name = "reset-guide",
        description = "Établit, nuit par nuit, ce que deviendrait l'audio si l'on repartait d'une base neuve "
                + "(disque / serveur / perdu). Lecture seule — sauf avec --executer, qui mène la procédure "
                + "jusqu'au bout.")
public final class ResetGuide implements Callable<Integer> {

    /// Code de sortie quand au moins une nuit perdrait son audio : distinct de l'échec (1) et du succès (0).
    private static final int CODE_PERTE = 2;

    @Option(names = "--json", description = "Émet le bilan au format JSON plutôt qu'en texte.")
    private boolean json;

    @Option(
            names = "--executer",
            description = "EXÉCUTE la procédure : sauvegarde complète, base neuve, repeuplement depuis "
                    + "Vigie-Chiro, audit final. Exige --confirmer. Sans cette option, la commande se "
                    + "contente d'établir le bilan (lecture seule).")
    private boolean executer;

    @Option(
            names = "--confirmer",
            description = "Obligatoire avec --executer : atteste que la destruction de la base est voulue.")
    private boolean confirmer;

    @Option(
            names = "--accepter-perte",
            description = "Autorise le reset alors que l'audio de certaines nuits ne reviendra pas. "
                    + "Sans cette option, la procédure refuse de démarrer dès qu'une nuit est en PERDU.")
    private boolean accepterPerte;

    @Option(
            names = "--sauvegarde",
            paramLabel = "<dossier>",
            description = "Où écrire la sauvegarde complète avant de détruire. Défaut : <workspace>/sauvegardes.")
    private Path dossierSauvegarde;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceRecuperabilite> service;
    private final Provider<ServiceReset> reset;
    private final Provider<ServiceSauvegarde> sauvegarde;

    @Inject
    public ResetGuide(
            Provider<ServiceRecuperabilite> service,
            Provider<ServiceReset> reset,
            Provider<ServiceSauvegarde> sauvegarde) {
        this.service = Objects.requireNonNull(service, "service");
        this.reset = Objects.requireNonNull(reset, "reset");
        this.sauvegarde = Objects.requireNonNull(sauvegarde, "sauvegarde");
    }

    @Override
    public Integer call() {
        return executer ? executerLeReset() : afficherLeBilan();
    }

    /// Sans `--executer` : **lecture seule**. Le bilan, et un code de sortie qui permet à un script de
    /// refuser d'enchaîner.
    private Integer afficherLeBilan() {
        PrintWriter sortie = spec.commandLine().getOut();
        BilanRecuperabilite bilan = service.get().bilan();

        if (json) {
            sortie.println(FormatJson.tableau(
                    bilan.nuits().stream().map(ResetGuide::enJson).toList()));
        } else {
            afficher(sortie, bilan);
        }
        return bilan.perteAnnoncee() ? CODE_PERTE : 0;
    }

    /// Avec `--executer` : la procédure entière. `--confirmer` est **obligatoire** — une base ne se détruit
    /// pas par inadvertance, et surtout pas par une option qu'on aurait pu laisser traîner dans un script.
    private Integer executerLeReset() {
        PrintWriter sortie = spec.commandLine().getOut();
        if (!confirmer) {
            spec.commandLine()
                    .getErr()
                    .println("--executer détruit la base : ajoutez --confirmer pour l'assumer."
                            + " (Sans --executer, la commande se contente du bilan.)");
            return CODE_PERTE;
        }
        Path destination =
                dossierSauvegarde != null ? dossierSauvegarde : sauvegarde.get().dossierParDefaut();

        ResultatReset resultat = reset.get().executer(destination, accepterPerte);
        sortie.println(resultat.enClair());
        if (resultat instanceof ResultatReset.Refuse refus && refus.bilan().perteAnnoncee()) {
            sortie.println();
            afficher(sortie, refus.bilan());
        }
        if (resultat instanceof ResultatReset.Fait) {
            sortie.println();
            sortie.println("Redémarrez l'application : ses écrans tiennent encore l'ancienne base en mémoire.");
        }
        return resultat.codeSortie();
    }

    private static void afficher(PrintWriter sortie, BilanRecuperabilite bilan) {
        if (bilan.nuits().isEmpty()) {
            sortie.println("Aucune nuit en base : il n'y a rien à perdre.");
            return;
        }
        sortie.println("Si vous repartiez d'une base neuve :");
        sortie.println();
        for (RecuperabiliteNuit nuit : bilan.nuits()) {
            sortie.println("  " + marque(nuit.source()) + " " + nuit.enClair());
        }
        sortie.println();
        sortie.println(bilan.resume());

        if (!bilan.perteAnnoncee()) {
            sortie.println("Aucune perte : chaque nuit retrouverait son audio.");
            return;
        }
        sortie.println();
        sortie.println("⚠ L'audio des nuits ci-dessus marquées PERDU ne reviendra PAS.");
        sortie.println("  Elles deviendront des passages archivés : observations et vérifications");
        sortie.println("  consultables, écoute impossible (réactivables si vous retrouvez les fichiers).");
        sortie.println("  Sauvegardez-les d'abord : ./vigiechiro sauvegarder --complet");
    }

    /// Marque de lecture rapide : c'est la colonne qu'on parcourt des yeux avant de décider.
    private static String marque(SourceAudio source) {
        return switch (source) {
            case DISQUE -> "[disque ]";
            case SERVEUR -> "[serveur]";
            case PERDU -> "[PERDU  ]";
        };
    }

    private static Map<String, Object> enJson(RecuperabiliteNuit nuit) {
        Map<String, Object> champs = new LinkedHashMap<>();
        champs.put("idPassage", nuit.idPassage());
        champs.put("libelle", nuit.libelle());
        champs.put("source", nuit.source().name());
        champs.put("sequencesPresentes", nuit.sequencesPresentes());
        champs.put("sequencesTotal", nuit.sequencesTotal());
        champs.put("motif", nuit.motif());
        return champs;
    }
}
