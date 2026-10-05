package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.Horodatage;
import fr.univ_amu.iut.commun.viewmodel.Formats;
import fr.univ_amu.iut.lot.model.EtatLot;
import fr.univ_amu.iut.lot.model.Lot;
import fr.univ_amu.iut.lot.model.ServiceLot;
import fr.univ_amu.iut.passage.model.Passage;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `deposer` (#617) : clôture le workflow d'un passage, **prépare le lot** ([ServiceLot#preparerLot],
/// transition Vérifié → Prêt à déposer, avec les gardes métier : R14 « Inexploitable », cohérence bloquante) puis
/// le **marque déposé** ([ServiceLot#marquerDepose], Prêt à déposer → Déposé). Réutilise `ServiceLot` sans
/// logique nouvelle. Tout refus métier (statut incompatible, passage « Inexploitable », session introuvable)
/// sort en échec d'exécution (code 1), avant tout changement d'état si la préparation échoue.
@Command(
        name = "deposer",
        description = "Clôture le dépôt d'un passage : prépare le dépôt s'il ne l'est pas, puis le marque déposé.")
public final class Deposer implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage à déposer (vérifié, ou dont le dépôt est déjà préparé ou entamé).")
    private Long idPassage;

    @Spec
    private CommandSpec spec;

    private final ServiceLot service;

    @Inject
    public Deposer(ServiceLot service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        // Préparer seulement ce qui ne l'est pas (#5867), comme `exporter-lot` depuis #5599 : la
        // préparation n'admet que « Vérifié », et la commande refusait donc un dépôt entamé, que le
        // moteur autorise pourtant à passer « Déposé ». C'est le dernier geste du repli manuel.
        EtatLot etat = service.consulterLot(idPassage);
        int sequences = etat.nombreSequences();
        Long volume = etat.volumeSequencesOctets();
        if (!ServiceLot.archivesSeGenerent(etat.statut())) {
            Lot lot = service.preparerLot(idPassage);
            sequences = lot.sequences().size();
            volume = lot.volumeSequencesOctets();
        }
        Passage depose = service.marquerDepose(idPassage);
        spec.commandLine().getOut().println(rendreDepot(idPassage, sequences, volume, depose.deposeLe()));
        return 0;
    }

    /// Compte rendu du dépôt. Fonction pure (testable sans base) : le volume peut être `null` (non calculé).
    static String rendreDepot(long idPassage, int nombreSequences, Long volumeOctets, String deposeLe) {
        String volume = volumeOctets == null ? "volume inconnu" : Formats.octetsLisibles(volumeOctets);
        // La date se lit comme dans `statut-passage` (#5761) : la base la porte avec son heure, en ISO.
        return "Passage #" + idPassage + " déposé le " + Horodatage.dateSeule(deposeLe) + ".\n" + "  Dépôt : "
                + nombreSequences
                + " séquence(s), " + volume + ".";
    }
}
