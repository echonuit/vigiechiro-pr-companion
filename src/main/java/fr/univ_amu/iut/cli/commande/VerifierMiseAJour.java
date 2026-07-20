package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.VersionApplication;
import fr.univ_amu.iut.maj.model.VerificateurMiseAJour;
import fr.univ_amu.iut.maj.model.VersionDisponible;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// `verifier-maj` (#2109) : dit si une version plus récente est publiée.
///
/// Pendant CLI de l'annonce affichée au démarrage de l'IHM (ADR 0014, parité CLI ↔ IHM). Son intérêt
/// propre est le **script** : un poste de terrain ou un parc de machines peut vouloir vérifier
/// l'obsolescence sans ouvrir l'application, d'où un **code de sortie** exploitable.
///
/// Trois codes, parce que « pas de mise à jour » et « je n'ai pas pu savoir » ne se pilotent pas
/// pareil dans un script :
///
/// - `0` : à jour, ou rien à annoncer ;
/// - `10` : une version plus récente existe (choisi hors de la plage habituelle `0/1/2`, pour ne pas
///   se confondre avec un échec d'exécution ou une mauvaise invocation) ;
/// - `1` : la vérification n'a pas abouti - hors ligne, service injoignable, ou version locale
///   inconnue parce que l'application ne tourne pas depuis un artefact publié.
@Command(name = "verifier-maj", description = "Indique si une version plus récente de l'application est publiée.")
public final class VerifierMiseAJour implements java.util.concurrent.Callable<Integer> {

    /// Code distinct du succès : « à jour » et « mise à jour disponible » sont deux réponses
    /// normales, pas un succès et un échec.
    static final int CODE_MISE_A_JOUR_DISPONIBLE = 10;

    @Spec
    private CommandSpec spec;

    private final VerificateurMiseAJour verificateur;
    private final VersionApplication version;

    @Inject
    public VerifierMiseAJour(VerificateurMiseAJour verificateur, VersionApplication version) {
        this.verificateur = Objects.requireNonNull(verificateur, "verificateur");
        this.version = Objects.requireNonNull(version, "version");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();

        if (version.versionEmpaquetee().isEmpty()) {
            // Pas une erreur d'usage : c'est le cas normal hors artefact publié. On le dit au lieu
            // de laisser croire que l'application est à jour.
            spec.commandLine()
                    .getErr()
                    .println("Version locale inconnue (application lancée hors d'un artefact publié) :"
                            + " comparaison impossible.");
            return 1;
        }

        Optional<VersionDisponible> proposee = verificateur.versionAProposer();
        if (proposee.isEmpty()) {
            sortie.println("Version " + version.libelle() + " : aucune version plus récente n'est annoncée.");
            return 0;
        }

        VersionDisponible disponible = proposee.orElseThrow();
        sortie.println("Version " + version.libelle() + " installée ; " + disponible.numero() + " est disponible.");
        sortie.println(disponible.adresse());
        return CODE_MISE_A_JOUR_DISPONIBLE;
    }
}
