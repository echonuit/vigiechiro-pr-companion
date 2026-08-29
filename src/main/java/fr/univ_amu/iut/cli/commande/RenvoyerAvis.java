package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `renvoyer-avis` (#4729) : le relecteur renvoie son jugement, parité en ligne de commande du geste
/// « Renvoyer mon avis… » (article A19).
///
/// Le paquet du retour porte **un avis, pas une nuit** : l'expéditeur a déjà les séquences. Il est
/// signé de qui est connecté ici, faute de quoi l'avis reviendrait anonyme (ADR 4517).
@Command(name = "renvoyer-avis", description = "Écrit l'avis du relecteur : un manifeste signé, sans séquence.")
public final class RenvoyerAvis implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = "--passage", required = true, paramLabel = "<v>", description = "Nuit relue.")
    private Long idPassage;

    @Option(names = "--vers", required = true, paramLabel = "<v>", description = "Archive d'avis à écrire.")
    private Path destination;

    private final ServiceEmport service;

    private final StockageConnexion connexion;

    @Inject
    public RenvoyerAvis(ServiceEmport service, StockageConnexion connexion) {
        this.service = Objects.requireNonNull(service, "service");
        this.connexion = Objects.requireNonNull(connexion, "connexion");
    }

    @Override
    public Integer call() throws IOException {
        String pseudo = connexion
                .profil()
                .map(fr.univ_amu.iut.commun.api.ProfilVigieChiro::pseudo)
                .orElseThrow(
                        () -> new ErreurUsage("Aucune identité : reconnectez-vous, sinon l'avis reviendrait anonyme."));
        ServiceEmport.BilanAvisRenvoye bilan = service.renvoyerAvis(idPassage, destination, pseudo);
        spec.commandLine()
                .getOut()
                .println(bilan.verdicts() + " verdict(s) signés « " + bilan.pseudoJugeur() + " » : " + destination
                        + " (" + bilan.octets() + " octets).");
        return 0;
    }
}
