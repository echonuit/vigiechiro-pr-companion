package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import com.google.inject.Provider;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.passage.model.ParticipationOrpheline;
import fr.univ_amu.iut.passage.model.RapportReconstruction;
import fr.univ_amu.iut.passage.model.ServiceReconstructionPassages;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `reconstruire-passage` (#1305, EPIC #1297) : rend visibles — et récupérables — les nuits **déposées sur
/// la plateforme mais jamais importées sur cette machine** (déposées depuis un autre poste, avant
/// l'application, ou après une réinstallation).
///
/// Sans argument, la commande **liste** ces participations orphelines. Avec `--participation <objectid>`,
/// elle en **reconstruit** une en **passage archivé** : lignes de séquences (sans fichier) et observations
/// rapatriées. Le passage se consulte alors comme tout passage archivé, et se réactive si l'utilisateur
/// retrouve ses fichiers (`reactiver`).
///
/// **Ce qui manque est dit** : un passage reconstruit n'a ni journal du capteur, ni relevé climatique, ni
/// séquences non identifiées, ni empreintes — la plateforme ne les connaît pas.
@Command(
        name = "reconstruire-passage",
        description = "Liste les participations VigieChiro sans équivalent local (nuits déposées depuis un "
                + "autre poste, ou avant l'application) et, avec --participation, en reconstruit une en "
                + "passage archivé : observations rapatriées, audio absent, réactivable par réimport.")
public final class ReconstruirePassage implements Callable<Integer> {

    @Option(
            names = "--participation",
            paramLabel = "<objectid>",
            description = "Reconstruit cette participation en passage archivé. Sans cette option, "
                    + "la commande se contente de lister les participations orphelines.")
    private String idParticipation;

    @Option(names = "--json", description = "Émet la liste ou le rapport au format JSON.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    // Provider, non instance directe : picocli instancie les sous-commandes AVANT la migration du schéma
    // (cf. Auditer). L'Optional est vide hors connexion : le service a besoin de la plateforme.
    private final Provider<Optional<ServiceReconstructionPassages>> service;

    @Inject
    public ReconstruirePassage(Provider<Optional<ServiceReconstructionPassages>> service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() {
        PrintWriter sortie = spec.commandLine().getOut();
        ServiceReconstructionPassages reconstruction = service.get()
                .orElseThrow(() -> new RegleMetierException("La reconstruction a besoin de la connexion"
                        + " VigieChiro : renseignez un jeton (VIGIECHIRO_TOKEN) puis recommencez."));
        return idParticipation == null
                ? lister(sortie, reconstruction.orphelines())
                : reconstruire(sortie, reconstruction.reconstruire(idParticipation));
    }

    /// Liste les participations sans équivalent local ; `0` même si la liste est vide (ce n'est pas une
    /// erreur : cela veut dire que tout est déjà là).
    private int lister(PrintWriter sortie, List<ParticipationOrpheline> orphelines) {
        if (json) {
            sortie.println(FormatJson.tableau(
                    orphelines.stream().map(ReconstruirePassage::projeter).toList()));
            return 0;
        }
        if (orphelines.isEmpty()) {
            sortie.println("Aucune participation orpheline : toutes vos nuits VigieChiro ont un passage local.");
            return 0;
        }
        sortie.println(orphelines.size() + " participation(s) sans équivalent local :");
        for (ParticipationOrpheline orpheline : orphelines) {
            sortie.println("  - " + orpheline.idParticipation() + "  carré " + orpheline.numeroCarre() + ", localité "
                    + orpheline.codePoint() + ", nuit du " + orpheline.dateDebut()
                    + (orpheline.pointLocalConnu() ? "" : "  [point inconnu localement : créez-le d'abord]"));
        }
        sortie.println("Reconstruisez-en une avec --participation <objectid>.");
        return 0;
    }

    /// Reconstruit et rend compte, **lacunes comprises** : un passage reconstruit est moins riche qu'un
    /// passage archivé par purge, et le taire laisserait croire à une équivalence.
    private int reconstruire(PrintWriter sortie, RapportReconstruction rapport) {
        if (json) {
            sortie.println(FormatJson.objet(projeter(rapport)));
            return 0;
        }
        sortie.println("Passage " + rapport.idPassage() + " reconstruit (archivé) : " + rapport.sequencesRecreees()
                + " séquence(s) recréée(s), " + rapport.observationsImportees() + " observation(s) importée(s).");
        sortie.println("Ce qui manque, faute d'exister côté plateforme :");
        rapport.lacunes().forEach(lacune -> sortie.println("  - " + lacune));
        return 0;
    }

    private static Map<String, Object> projeter(ParticipationOrpheline orpheline) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("participation", orpheline.idParticipation());
        objet.put("carre", orpheline.numeroCarre());
        objet.put("point", orpheline.codePoint());
        objet.put("dateDebut", orpheline.dateDebut());
        objet.put("pointLocalConnu", orpheline.pointLocalConnu());
        return objet;
    }

    private static Map<String, Object> projeter(RapportReconstruction rapport) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("passage", rapport.idPassage());
        objet.put("sequencesRecreees", rapport.sequencesRecreees());
        objet.put("observationsImportees", rapport.observationsImportees());
        objet.put("lacunes", rapport.lacunes());
        return objet;
    }
}
