package fr.univ_amu.iut.cli.commande;

import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.EcrivainCsv;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

/// Où et sous quelle forme un inventaire est rendu (#3269) : `--format` dit **quoi**, `--sortie` dit
/// **où**, et les deux sont indépendants.
///
/// Le CSV du fichier et celui de la sortie standard passent par la **même** table et le **même**
/// [EcrivainCsv] : ce sont les mêmes octets. Deux CSV qui différeraient selon leur destination seraient
/// un piège à script, d'autant plus vicieux qu'on ne compare presque jamais les deux.
///
/// `--sortie` vaut aussi pour le JSON. La commande dont ce mixin s'inspire ne l'honorait qu'en CSV et
/// **ignorait en silence** un `--sortie` posé avec `--format json` : l'utilisateur croyait avoir écrit un
/// fichier qui n'existait pas.
public final class SortieInventaire {

    @Option(
            names = "--format",
            paramLabel = "<format>",
            defaultValue = "csv",
            description = "Format de sortie : csv ou json. Défaut : ${DEFAULT-VALUE}.")
    private String format;

    @Option(
            names = "--sortie",
            paramLabel = "<fichier>",
            description = "Écrit dans ce fichier plutôt que sur la sortie standard.")
    private Path sortie;

    /// Vrai si `--format` désigne un format connu ; sinon le dit sur la sortie d'erreur.
    ///
    /// Le contrôle précède toute lecture de la base : refuser un format après avoir travaillé ferait
    /// payer une requête pour rien, et laisserait croire que l'échec vient des données.
    public boolean formatReconnu(CommandSpec spec) {
        if ("csv".equalsIgnoreCase(format) || "json".equalsIgnoreCase(format)) {
            return true;
        }
        spec.commandLine().getErr().println("Format non pris en charge : " + format + ". Choisir csv ou json.");
        return false;
    }

    /// Rend l'inventaire, et rend le code de sortie de la commande.
    ///
    /// @param resume ce qui est affiché à la place des données quand elles partent dans un fichier :
    ///     sans lui, `--sortie` serait parfaitement muet et on ne saurait pas où regarder
    public int rendre(List<List<String>> table, Map<String, Object> json, CommandSpec spec, String resume)
            throws IOException {
        PrintWriter standard = spec.commandLine().getOut();
        if ("json".equalsIgnoreCase(format)) {
            String texte = FormatJson.objet(json);
            if (sortie == null) {
                standard.println(texte);
                return ExitCode.OK;
            }
            creerDossierParent();
            Files.writeString(sortie, texte + "\n", StandardCharsets.UTF_8);
        } else if (sortie == null) {
            standard.print(new EcrivainCsv().versChaine(table));
            return ExitCode.OK;
        } else {
            new EcrivainCsv().ecrire(sortie, table);
        }
        standard.println(resume + " → " + sortie.toAbsolutePath());
        return ExitCode.OK;
    }

    private void creerDossierParent() throws IOException {
        Path parent = sortie.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
