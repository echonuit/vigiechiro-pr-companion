package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.model.ErreurUsage;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `importer-tadarida` (#616) : importe un CSV de résultats Tadarida dans un passage et affiche le **bilan**
/// (observations importées, lignes ignorées, taxons hors référentiel). Ferme la boucle de validation en
/// ligne de commande, en pendant d'`exporter-vu`. Réutilise [ServiceValidation] sans logique nouvelle ;
/// `--remplacer` bascule sur le réimport atomique. Les refus métier (passage sans nuit importée, aucune
/// séquence du CSV en base) sortent en échec d'exécution (code 1).
@Command(
        name = "importer-tadarida",
        description = "Importe un CSV de résultats Tadarida dans un passage et affiche le bilan.")
public final class ImporterTadarida implements Callable<Integer> {

    @Option(
            names = "--passage",
            required = true,
            paramLabel = "<id>",
            description = "Passage cible (sa nuit doit déjà être importée).")
    private Long idPassage;

    @Option(
            names = "--csv",
            required = true,
            paramLabel = "<fichier>",
            description = "Fichier CSV Tadarida (observations ou _Vu).")
    private Path csv;

    @Option(
            names = "--remplacer",
            description = "Remplace le jeu de résultats déjà importé pour ce passage (réimport atomique).")
    private boolean remplacer;

    @Spec
    private CommandSpec spec;

    private final ServiceValidation service;
    private final ResultatsIdentificationDao resultatsDao;

    @Inject
    public ImporterTadarida(ServiceValidation service, ResultatsIdentificationDao resultatsDao) {
        this.service = Objects.requireNonNull(service, "service");
        this.resultatsDao = Objects.requireNonNull(resultatsDao, "resultatsDao");
    }

    @Override
    public Integer call() {
        if (!Files.isRegularFile(csv)) {
            throw new ErreurUsage("Fichier CSV introuvable : " + csv + ".");
        }
        GardeJeuExistant.refuserSiDejaImporte(resultatsDao, idPassage, remplacer);
        BilanImport bilan = remplacer ? service.reimporter(idPassage, csv) : service.importer(idPassage, csv);
        spec.commandLine().getOut().println(rendreBilan(bilan, remplacer));
        return 0;
    }

    /// Rendu texte du bilan d'import. Fonction pure (testable sans base) : les compteurs de validations
    /// (préservées / perdues) ne sont affichés qu'en réimport, où ils ont un sens.
    static String rendreBilan(BilanImport bilan, boolean remplacer) {
        StringBuilder texte = new StringBuilder("Import Tadarida réussi (jeu #")
                .append(bilan.idResultats())
                .append(") :")
                .append('\n');
        ligne(texte, "Observations importées", Integer.toString(bilan.importees()));
        ligne(texte, "Lignes ignorées", bilan.ignorees() + " (séquence audio absente ou ligne sans taxon)");
        ligne(texte, "Taxons hors référentiel", bilan.taxonsHorsReferentiel() + " (auto-enregistrés en souches)");
        if (remplacer) {
            ligne(texte, "Validations préservées", Integer.toString(bilan.validationsPreservees()));
            ligne(texte, "Validations perdues", Integer.toString(bilan.validationsPerdues()));
        }
        return texte.toString().stripTrailing();
    }

    /// Ajoute `  <libellé aligné> : <valeur>` (libellé cadré à 24 caractères) suivi d'un retour ligne.
    private static void ligne(StringBuilder texte, String libelle, String valeur) {
        texte.append(String.format("  %-24s : %s", libelle, valeur)).append('\n');
    }
}
