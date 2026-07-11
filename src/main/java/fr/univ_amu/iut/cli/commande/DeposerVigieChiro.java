package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.BilanDepot;
import fr.univ_amu.iut.lot.model.DepotUnite;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.lot.model.StatutDepotUnite;
import fr.univ_amu.iut.lot.model.SuiviDepot;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `deposer-vigiechiro` (#1043) : téléverse la nuit d'un passage sur la plateforme VigieChiro via le
/// **moteur reprenable** ([DepotVigieChiro], #982) — participation créée ou réutilisée, plan
/// `depot_unite` persisté, seuls les fichiers **manquants** sont (re)téléversés : la commande est
/// **relançable** telle quelle après une coupure. À ne pas confondre avec `deposer`, le **marquage
/// manuel** (téléversement fait sur le site web).
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la **connexion
/// enregistrée** dans l'application (préférer la variable d'environnement à `--token`, qui laisse le
/// jeton dans l'historique du shell).
///
/// Sortie : une ligne par fichier téléversé (`+`) ou en échec (`!` + raison), puis le bilan. Code
/// retour `0` seulement si le dépôt est **complet** (scriptable) ; `1` si des fichiers restent à
/// reprendre.
@Command(
        name = "deposer-vigiechiro",
        description = "Téléverse un passage sur VigieChiro (reprenable : seuls les fichiers manquants repartent).")
public final class DeposerVigieChiro implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage à téléverser (lot préparé : statut « Prêt à déposer » ou « Dépôt en cours »).")
    private Long idPassage;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton VigieChiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Spec
    private CommandSpec spec;

    private final ServiceLot serviceLot;
    private final Optional<DepotVigieChiro> depot;

    @Inject
    public DeposerVigieChiro(ServiceLot serviceLot, Optional<DepotVigieChiro> depot) {
        this.serviceLot = Objects.requireNonNull(serviceLot, "serviceLot");
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    @Override
    public Integer call() {
        DepotVigieChiro moteur = depot.orElseThrow(
                () -> new RegleMetierException("Dépôt VigieChiro indisponible dans ce contexte d'exécution."));
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel : consulté par le client à chaque requête (cf. ConnexionModule), sans rien
            // persister — la connexion enregistrée de l'application n'est pas modifiée.
            System.setProperty("vigiechiro.token", token);
        }
        List<Path> fichiers = serviceLot.sequencesADeposer(idPassage);
        if (fichiers.isEmpty()) {
            throw new RegleMetierException("Aucune séquence transformée à déposer pour ce passage"
                    + " (préparez d'abord le lot : statut « Prêt à déposer »).");
        }
        PrintWriter sortie = spec.commandLine().getOut();
        BilanDepot bilan = moteur.deposer(idPassage, fichiers, () -> false, new SuiviConsole(sortie));
        sortie.println(rendreBilan(bilan));
        return bilan.estComplet() ? 0 : 1;
    }

    /// Bilan final : participation, fichiers téléversés cette fois-ci, reste éventuel. Fonction pure
    /// (testable sans base ni réseau).
    static String rendreBilan(BilanDepot bilan) {
        if (bilan.estComplet()) {
            return "Dépôt complet : " + bilan.deposees() + " fichier(s) téléversé(s) (participation "
                    + bilan.participationId() + "). Passage marqué « Déposé ».";
        }
        return "Dépôt INCOMPLET : " + bilan.deposees() + " fichier(s) téléversé(s), "
                + bilan.echecs().size()
                + " en échec (participation " + bilan.participationId() + "). Relancez la commande pour ne"
                + " reprendre que les manquants.";
    }

    /// Ligne de plan : combien d'unités sont à téléverser, combien sont déjà en ligne (reprise). Fonction
    /// pure (testable sans base ni réseau).
    static String rendrePlan(List<DepotUnite> unites) {
        long dejaDeposees = unites.stream()
                .filter(unite -> unite.statut() == StatutDepotUnite.DEPOSE)
                .count();
        String reprise = dejaDeposees == 0 ? "" : " (" + dejaDeposees + " déjà en ligne, reprise)";
        return "Plan de dépôt : " + unites.size() + " fichier(s)" + reprise + ".";
    }

    /// Suivi console du dépôt : une ligne par unité, écrite au fil de l'eau (le moteur émet sur le fil
    /// d'appel — pas de relais nécessaire en CLI, contrairement à l'IHM).
    private record SuiviConsole(PrintWriter sortie) implements SuiviDepot {

        @Override
        public void planEtabli(List<DepotUnite> unites) {
            sortie.println(rendrePlan(unites));
        }

        @Override
        public void uniteDemarree(String identifiant) {
            // Silencieux : la ligne de fin (déposée / échec) suffit, le dépôt est séquentiel.
        }

        @Override
        public void uniteDeposee(DepotUnite unite) {
            sortie.println("  + " + unite.identifiantUnite());
        }

        @Override
        public void uniteEchouee(String identifiant, String raison) {
            sortie.println("  ! " + identifiant + " — " + raison);
        }
    }
}
