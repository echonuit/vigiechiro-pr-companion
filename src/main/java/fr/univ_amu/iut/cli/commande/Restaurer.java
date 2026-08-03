package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.persistence.BilanRestauration;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/// Commande `restaurer` (#1346) : remet en place une sauvegarde produite par `sauvegarder`.
///
/// **Destructif** : l'état local est écrasé. `--confirmer` est donc obligatoire, en script, rien ne doit
/// pouvoir remplacer une base par inadvertance. L'état courant de la base est mis de côté
/// (`vigiechiro.db.avant-restauration`) ; l'**audio**, lui, ne l'est pas.
///
/// Avec `--complet`, l'argument est le **dossier** d'une sauvegarde complète (base + dossiers de session) ;
/// sinon, c'est le **fichier** `.db` d'une sauvegarde de base.
@Command(
        name = "restaurer",
        description = "Restaure une sauvegarde (base seule, ou base + audio avec --complet). Destructif.")
public final class Restaurer implements Callable<Integer> {

    /// Code de sortie d'un refus faute de `--confirmer` : la commande **n'a rien fait**, et un script
    /// doit pouvoir le distinguer d'un échec en cours de route (`1`), qui laisserait l'état incertain.
    private static final int CODE_REFUS = 2;

    @Parameters(
            index = "0",
            paramLabel = "<source>",
            description = "Fichier .db de la sauvegarde, ou dossier de sauvegarde complète avec --complet.")
    private Path source;

    @Option(
            names = "--complet",
            description = "La source est un DOSSIER de sauvegarde complète : restaure la base ET l'audio.")
    private boolean complet;

    @Option(
            names = "--confirmer",
            description = "Obligatoire : confirme l'écrasement de l'état local (opération destructive).")
    private boolean confirmer;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceSauvegarde> service;

    @Inject
    public Restaurer(Provider<ServiceSauvegarde> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        if (!confirmer) {
            // Le refus part sur stderr, et sort en 2 : la commande n'a RIEN fait. En rendant 1 - le code
            // de l'échec d'exécution - elle laissait un script incapable de distinguer « j'ai refusé,
            // l'état local est intact » de « j'ai échoué en route », sur une commande destructive (#2294).
            spec.commandLine()
                    .getErr()
                    .println("Restauration refusée : elle écrase l'état local. Relancez avec --confirmer.");
            return CODE_REFUS;
        }
        ServiceSauvegarde sauvegarde = service.get();
        if (complet) {
            BilanRestauration bilan = sauvegarde.restaurerComplet(source);
            sortie.println("Base et dossiers de son restaurés depuis : " + source);
            // Une restauration complète déplace des gigaoctets et corrige la base : dire où ils ont
            // atterri, et ce qui manquait, fait partie du compte rendu (#2727).
            sortie.println(bilan.enClair());
        } else {
            sauvegarde.restaurer(source);
            sortie.println("Base restaurée depuis : " + source);
            sortie.println("(L'audio n'est pas concerné : seule une sauvegarde --complet le contient.)");
        }
        return 0;
    }
}
