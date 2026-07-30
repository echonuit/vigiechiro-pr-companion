package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.commun.persistence.BilanSauvegarde;
import fr.univ_amu.iut.commun.persistence.ServiceSauvegarde;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// Commande `sauvegarder` (#1346) : met la base (et, sur demande, l'**audio**) à l'abri.
///
/// C'est la **parité CLI** (passe 2 du cycle de chantier) d'une capacité qui n'avait jusqu'ici *aucun*
/// appelant : le moteur de sauvegarde complète existait depuis #1142, ni le menu ni la CLI ne l'exposaient.
///
/// **Pourquoi `--complet` compte.** La base seule ne garde que les métadonnées et les observations. Si
/// l'audio disparaît du disque, la plateforme ne le rendra **pas** : un dépôt en archives n'en laisse aucun
/// (#1297), et le passage devient *archivé* : consultable, mais muet. Le disque est l'unique source de
/// l'audio, et cette commande est la seule à le protéger. C'est aussi le prérequis du reset guidé (#1151).
///
/// Le bilan **dit ce qui manque** : une racine de session non montée (carte SD retirée, disque débranché)
/// est sautée, et la sauvegarde est alors annoncée **incomplète** : code de sortie `2`, exploitable en
/// script. Une sauvegarde qu'on croit complète et qui ne l'est pas vaut moins que pas de sauvegarde.
@Command(
        name = "sauvegarder",
        description = "Sauvegarde la base (et, avec --complet, les dossiers de session : l'audio).")
public final class Sauvegarder implements Callable<Integer> {

    /// Code de sortie d'une sauvegarde complète **amputée** : elle a abouti, mais des dossiers manquent.
    private static final int CODE_INCOMPLETE = 2;

    @Option(
            names = "--complet",
            description = "Sauvegarde AUSSI les dossiers de session (l'audio). Peut peser plusieurs Go. "
                    + "Sans cette option, seule la base est copiée.")
    private boolean complet;

    @Option(
            names = "--dossier",
            paramLabel = "<dossier>",
            description = "Dossier de destination. Par défaut : <workspace>/sauvegardes.")
    private Path dossier;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma.
    private final Provider<ServiceSauvegarde> service;

    @Inject
    public Sauvegarder(Provider<ServiceSauvegarde> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        ServiceSauvegarde sauvegarde = service.get();
        Path destination = dossier != null ? dossier : sauvegarde.dossierParDefaut();

        if (!complet) {
            Path fichier = sauvegarde.sauvegarder(destination);
            sortie.println("Base sauvegardée : " + fichier);
            sortie.println("⚠ L'audio n'est PAS dans cette sauvegarde. Utilisez --complet pour l'inclure :"
                    + " la plateforme ne rend pas l'audio d'un dépôt en archives.");
            return 0;
        }

        BilanSauvegarde bilan = sauvegarde.sauvegarderComplet(destination);
        sortie.println("Sauvegarde complète : " + bilan.dossier());
        sortie.println(bilan.enClair());
        if (bilan.incomplete()) {
            sortie.println("⚠ Sauvegarde INCOMPLÈTE : les dossiers ci-dessus n'ont pas été copiés.");
            return CODE_INCOMPLETE;
        }
        return 0;
    }
}
