package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.lot.model.DepotVigieChiro;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `lancer-traitement-vigiechiro` (#984) : déclenche le **traitement serveur** (Tadarida) de la
/// participation liée à un passage — équivalent CLI du bouton « Lancer la participation » de M-Lot.
/// À lancer **une fois la nuit déposée** ; le serveur enchaîne l'extraction des archives puis Tadarida.
///
/// **Jeton** : `--token`, sinon la variable d'environnement `VIGIECHIRO_TOKEN`, sinon la **connexion
/// enregistrée** dans l'application (préférer la variable d'environnement à `--token`, qui laisse le
/// jeton dans l'historique du shell).
///
/// Code retour `0` si le serveur accepte le lancement, `1` s'il le refuse. Lève une
/// [RegleMetierException] si aucune participation n'est liée au passage (déposer d'abord).
@Command(
        name = "lancer-traitement-vigiechiro",
        description = "Déclenche le traitement serveur (compute) de la participation d'un passage déjà déposé.")
public final class LancerTraitementVigieChiro implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage dont la participation liée doit être traitée (nuit déjà déposée).")
    private Long idPassage;

    @Option(
            names = "--token",
            paramLabel = "<jeton>",
            description = "Jeton VigieChiro ponctuel (sinon : variable VIGIECHIRO_TOKEN, sinon la connexion"
                    + " enregistrée dans l'application).")
    private String token;

    @Spec
    private CommandSpec spec;

    private final Optional<DepotVigieChiro> depot;

    @Inject
    public LancerTraitementVigieChiro(Optional<DepotVigieChiro> depot) {
        this.depot = Objects.requireNonNull(depot, "depot");
    }

    @Override
    public Integer call() {
        DepotVigieChiro moteur = depot.orElseThrow(
                () -> new RegleMetierException("Dépôt VigieChiro indisponible dans ce contexte d'exécution."));
        if (token != null && !token.isBlank()) {
            // Jeton ponctuel consulté par le client à chaque requête, sans rien persister.
            System.setProperty("vigiechiro.token", token);
        }
        PrintWriter sortie = spec.commandLine().getOut();
        boolean accepte = moteur.lancerTraitement(idPassage);
        sortie.println(
                accepte
                        ? "Traitement lancé sur VigieChiro pour le passage " + idPassage
                                + " : les résultats arriveront après le calcul serveur."
                        : "Le serveur a refusé le lancement du traitement (déjà en cours ?).");
        return accepte ? 0 : 1;
    }
}
