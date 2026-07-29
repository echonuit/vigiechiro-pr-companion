package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.analyse.model.ExportSyntheseCsv;
import fr.univ_amu.iut.analyse.model.LigneSynthese;
import fr.univ_amu.iut.analyse.model.ServiceSynthese;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.ContexteActivite;
import fr.univ_amu.iut.commun.model.ReferentielActivite;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `synthetiser-passage` (#2351, lot 1 du chantier #2348) : la **facette CLI de M-Synthese**.
///
/// Même service, mêmes règles de repli, même avertissement que l'écran — c'est le même
/// [ServiceSynthese] qui calcule. Une synthèse qui différerait d'une surface à l'autre serait pire
/// qu'absente : on ne saurait plus laquelle croire.
///
/// ## Le contexte voyage avec la donnée
///
/// En CSV, l'en-tête de contexte, l'avertissement et la citation sont écrits en tête de fichier. En
/// JSON, ils forment un objet `contexte` à côté des lignes. Dans les deux cas, **ils sont là** : un
/// fichier ouvert trois mois plus tard, par quelqu'un qui n'a jamais vu l'écran, doit pouvoir savoir
/// d'où sortent ces classes et ce qu'elles valent.
@Command(
        name = "synthetiser-passage",
        description = "Synthétise une nuit : contacts par espèce et classe d'activité au regard du référentiel.")
public final class SynthetiserPassage implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Identifiant du passage à synthétiser.")
    private long passage;

    @Option(
            names = "--carre",
            paramLabel = "<numero>",
            description = "Numéro de carré, dont les deux premiers chiffres donnent le département, "
                    + "donc la région du référentiel. Sans lui : comparaison nationale.")
    private String carre;

    @Option(
            names = "--milieu",
            paramLabel = "<milieu>",
            description = "Milieu du point d'écoute (Foret, Agricole, Urbain, Riviere…). Il ne se devine "
                    + "pas depuis les données : sans ce choix, la comparaison reste nationale.")
    private String milieu;

    @Option(
            names = "--validees-seulement",
            description = "Ne retient que les identifications validées ou corrigées. Recalcule TOUT le "
                    + "tableau, classe d'activité comprise.")
    private boolean valideesSeulement;

    @Option(
            names = "--format",
            paramLabel = "<format>",
            defaultValue = "csv",
            description = "Format de sortie : csv ou json. Défaut : ${DEFAULT-VALUE}.")
    private String format;

    @Option(
            names = "--sortie",
            paramLabel = "<fichier>",
            description = "Écrit le CSV dans ce fichier plutôt que sur la sortie standard.")
    private Path sortie;

    @Spec
    private CommandSpec spec;

    private final ServiceSynthese service;

    @Inject
    public SynthetiserPassage(ServiceSynthese service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() throws IOException {
        if (!"csv".equalsIgnoreCase(format) && !"json".equalsIgnoreCase(format)) {
            spec.commandLine().getErr().println("Format non pris en charge : " + format + ". Choisir csv ou json.");
            return ExitCode.USAGE;
        }
        List<LigneSynthese> lignes = service.pour(passage, valideesSeulement, carre, milieu);
        ContexteActivite contexte = service.contexte(passage, carre, milieu);
        PrintWriter sortieStandard = spec.commandLine().getOut();

        if ("json".equalsIgnoreCase(format)) {
            sortieStandard.println(FormatJson.objet(champsJson(lignes, contexte)));
            return 0;
        }
        if (sortie != null) {
            Path ecrit = ExportSyntheseCsv.ecrire(lignes, contexte, sortie);
            sortieStandard.println("Synthèse exportée : " + lignes.size() + " espèce(s) → " + ecrit.toAbsolutePath());
            return 0;
        }
        sortieStandard.print(ExportSyntheseCsv.contenu(lignes, contexte));
        return 0;
    }

    /// Le JSON porte le contexte dans un **objet à part**, à côté des lignes — pas dilué dans chacune.
    /// L'avertissement et la citation y figurent au même titre qu'en CSV : le format change, l'obligation
    /// de citer ne change pas.
    private java.util.Map<String, Object> champsJson(List<LigneSynthese> lignes, ContexteActivite contexte) {
        java.util.Map<String, Object> contexteJson = new java.util.LinkedHashMap<>();
        contexteJson.put("referentiel", contexte.libelle());
        contexteJson.put("avertissement", ReferentielActivite.AVERTISSEMENT);
        contexteJson.put("source", ReferentielActivite.CITATION);

        java.util.Map<String, Object> racine = new java.util.LinkedHashMap<>();
        racine.put("passage", passage);
        racine.put("valideesSeulement", valideesSeulement);
        racine.put("contexte", contexteJson);
        racine.put(
                "especes", lignes.stream().map(SynthetiserPassage::champsEspece).toList());
        return racine;
    }

    private static java.util.Map<String, Object> champsEspece(LigneSynthese ligne) {
        java.util.Map<String, Object> champs = new java.util.LinkedHashMap<>();
        champs.put("code", ligne.codeTaxon());
        champs.put("nom", ligne.nomEspece());
        champs.put("groupe", ligne.groupe());
        champs.put("contacts", ligne.contacts());
        champs.put("fichiers", ligne.fichiers());
        champs.put("activite", ligne.libelleClasse());
        champs.put("indicatif", ligne.indicatif());
        champs.put("couvertParLeReferentiel", ligne.couvertParLeReferentiel());
        ligne.seuils().ifPresent(seuils -> {
            champs.put("q25", seuils.q25());
            champs.put("q75", seuils.q75());
            champs.put("q98", seuils.q98());
            champs.put("declinaison", seuils.declinaison());
            champs.put("saison", seuils.saison());
            champs.put("occurrences", seuils.occurrences());
            champs.put("fiabilite", seuils.confiance().name());
        });
        return champs;
    }
}
