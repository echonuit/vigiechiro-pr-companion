package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.passage.model.BackfillEmpreintes;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// Commande `retro-empreintes` : rétro-remplissage des preuves d'identité (#1299) sur les lignes
/// importées avant la migration V23, à partir des fichiers encore présents sur disque. Sans elle,
/// les passages existants n'auraient jamais d'empreinte, donc pas de réactivation par empreinte
/// (#1302) une fois archivés.
///
/// Déclenchement **explicite** plutôt qu'au démarrage : l'opération lit 64 Kio par séquence, ce qui
/// se compte en secondes (voire dizaines de secondes sur disque mécanique) sur un gros workspace.
/// Idempotente et reprenable : relançable sans risque, elle ne retouche jamais une ligne déjà
/// renseignée.
@Command(
        name = "retro-empreintes",
        description = "Pose taille et empreinte de contenu (#1299) sur les séquences et originaux importés "
                + "avant la V23, à partir des fichiers encore présents sur disque. Idempotent, reprenable, "
                + "relançable sans risque. Les fichiers déjà partis restent sans empreinte.")
public final class RetroEmpreintes implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma
    // (cf. Auditer) ; on résout donc paresseusement, à l'exécution de la commande.
    private final Provider<BackfillEmpreintes> backfill;

    @Inject
    public RetroEmpreintes(Provider<BackfillEmpreintes> backfill) {
        this.backfill = Objects.requireNonNull(backfill, "backfill");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        BackfillEmpreintes.Bilan bilan = backfill.get().remplirTout();
        sortie.println("Séquences : " + bilan.sequencesRemplies() + " renseignée(s), " + bilan.sequencesIgnorees()
                + " sans fichier (restée(s) sans empreinte).");
        sortie.println("Originaux : " + bilan.originauxRemplis() + " renseigné(s), " + bilan.originauxIgnores()
                + " sans fichier.");
        return 0;
    }
}
