package fr.univ_amu.iut.cli;

import com.google.inject.Injector;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.VerrouWorkspace;
import java.util.Objects;
import picocli.CommandLine;
import picocli.CommandLine.ParseResult;

final class StrategieExecutionCli {

    private final Injector injecteur;

    StrategieExecutionCli(Injector injecteur) {
        this.injecteur = Objects.requireNonNull(injecteur, "injecteur");
    }

    int executer(ParseResult resultat) {
        if (!resultat.hasSubcommand()) {
            return new CommandLine.RunLast().execute(resultat);
        }
        // Les erreurs nées dans une stratégie échappent au gestionnaire d'exécution de picocli. La
        // migration et le verrou passent donc explicitement par le même rendu que les commandes.
        try {
            injecteur.getInstance(MigrationSchema.class).migrer();
        } catch (RuntimeException echec) {
            return rendre(echec, resultat);
        }
        if (litSeulement(resultat)) {
            return new CommandLine.RunLast().execute(resultat);
        }
        VerrouWorkspace verrou;
        try {
            verrou = VerrouWorkspace.pourOperationExclusive(Workspace.resolu(), nomDe(resultat));
        } catch (RuntimeException echec) {
            return rendre(echec, resultat);
        }
        // Les exceptions de la commande repartent vers picocli. Le dernier catch ne reçoit donc que
        // les incidents propres au relâchement du verrou.
        try (verrou) {
            return new CommandLine.RunLast().execute(resultat);
        } catch (CommandLine.ExecutionException echecCommande) {
            throw echecCommande;
        } catch (RuntimeException echec) {
            return rendre(echec, resultat);
        }
    }

    private static int rendre(RuntimeException echec, ParseResult resultat) {
        return Cli.gererErreurExecution(echec, resultat.commandSpec().commandLine(), resultat);
    }

    private static boolean litSeulement(ParseResult resultat) {
        return feuille(resultat).commandSpec().userObject() instanceof LectureSeule;
    }

    private static String nomDe(ParseResult resultat) {
        return "« " + feuille(resultat).commandSpec().name() + " »";
    }

    private static ParseResult feuille(ParseResult resultat) {
        ParseResult courant = resultat;
        while (courant.hasSubcommand()) {
            courant = courant.subcommand();
        }
        return courant;
    }
}
