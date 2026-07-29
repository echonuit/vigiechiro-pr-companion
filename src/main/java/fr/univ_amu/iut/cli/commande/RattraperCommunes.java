package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.sites.model.ServiceCommunes;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/// Commande `rattraper-communes` : comble la commune des points d'écoute **en attente** (GPS présent,
/// commune absente) via l'API Géo (#2791). C'est le pendant CLI des déclencheurs de l'IHM (modale,
/// synchro, carte) - et le geste explicite pour les points créés par `ajouter-point`, qui ne résout
/// **pas** à la création (hors ligne, un appel réseau silencieux coûterait jusqu'à 10 s par point à
/// un script).
///
/// Best-effort et rejouable sans risque : un point non résolu (hors ligne, position en mer) reste
/// simplement en attente pour un prochain rattrapage ; une commune déjà résolue n'est jamais retouchée.
@Command(
        name = "rattraper-communes",
        description = "Comble la commune des points d'écoute en attente (GPS présent, commune absente) via "
                + "l'API Géo (#2791). Best-effort et rejouable sans risque : hors ligne, les points restent "
                + "en attente ; une commune déjà résolue n'est jamais retouchée.")
public final class RattraperCommunes implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma
    // (cf. Auditer) ; on résout donc paresseusement, à l'exécution de la commande.
    private final Provider<ServiceCommunes> communes;

    @Inject
    public RattraperCommunes(Provider<ServiceCommunes> communes) {
        this.communes = Objects.requireNonNull(communes, "communes");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        ServiceCommunes.BilanCommunes bilan = communes.get().rattraper();
        if (bilan.enAttente() == 0) {
            sortie.println("Aucun point en attente : rien à rattraper.");
        } else {
            sortie.println(bilan.enAttente() + " point(s) en attente : " + bilan.resolues()
                    + " commune(s) résolue(s), " + bilan.restantes()
                    + " restée(s) en attente (hors ligne ou position hors référentiel).");
        }
        return 0;
    }
}
