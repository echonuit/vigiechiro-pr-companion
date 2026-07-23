package fr.univ_amu.iut.cli.commande;

import com.google.inject.Inject;
import fr.univ_amu.iut.cli.FormatJson;
import fr.univ_amu.iut.commun.model.ServiceEmplacements;
import fr.univ_amu.iut.commun.model.ServiceEmplacements.Accessibilite;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/// `emplacements` (#1038, [ADR 1038]) : **voir ou changer où vivent** le dossier de travail et la base,
/// équivalent CLI de l'onglet « Emplacements » des réglages (parité, passe 2 du cycle de chantier).
///
/// Sans option, **affiche** les emplacements effectifs au prochain démarrage et leurs défauts (option
/// `--json` pour l'enchaînement de scripts). Avec `--definir-travail` / `--definir-base`, **écrit** le
/// choix après avoir **sondé** l'accessibilité de chaque dossier désigné (un dossier inutilisable est
/// refusé ici, pas au prochain démarrage). Avec `--reinitialiser`, **efface** le choix.
///
/// Comme l'écran, la commande ne déplace **rien** : elle change le pointeur lu au prochain démarrage,
/// pas les données. C'est dit dans le compte rendu.
@Command(
        name = "emplacements",
        description = "Voir ou changer où vivent le dossier de travail et la base (effet au prochain démarrage).")
public final class Emplacements implements Callable<Integer> {

    private static final int CODE_SUCCES = 0;
    private static final int CODE_ERREUR = 1;
    private static final String LIGNE = "  %-22s : %s";

    @Option(
            names = "--definir-travail",
            paramLabel = "<dossier>",
            description = "Range le dossier de travail à cet emplacement (sondé avant d'être écrit).")
    private Path dossierTravail;

    @Option(
            names = "--definir-base",
            paramLabel = "<dossier>",
            description = "Range la base (vigiechiro.db) dans ce dossier (sondé avant d'être écrit).")
    private Path dossierBase;

    @Option(names = "--reinitialiser", description = "Rétablit les emplacements par défaut.")
    private boolean reinitialiser;

    @Option(
            names = "--json",
            description = "Émet les emplacements au format JSON (enchaînement de scripts) plutôt qu'en texte.")
    private boolean json;

    @Spec
    private CommandSpec spec;

    private final ServiceEmplacements service;

    @Inject
    public Emplacements(ServiceEmplacements service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Integer call() throws IOException {
        PrintWriter sortie = spec.commandLine().getOut();
        PrintWriter erreur = spec.commandLine().getErr();

        if (reinitialiser) {
            if (dossierTravail != null || dossierBase != null) {
                erreur.println("--reinitialiser ne se combine pas avec --definir-travail / --definir-base.");
                return CODE_ERREUR;
            }
            service.reinitialiser();
            sortie.println("Emplacements rétablis par défaut. Effet au prochain démarrage.");
            return CODE_SUCCES;
        }

        if (dossierTravail != null || dossierBase != null) {
            return definir(sortie, erreur);
        }

        afficher(sortie);
        return CODE_SUCCES;
    }

    private int definir(PrintWriter sortie, PrintWriter erreur) throws IOException {
        ServiceEmplacements.Emplacements courant = service.emplacementsCourants();
        // Un emplacement non fourni garde sa valeur effective actuelle.
        Path travail = dossierTravail != null ? dossierTravail : courant.espaceDeTravail();
        Path base = dossierBase != null ? dossierBase : courant.base().getParent();

        if (dossierTravail != null && !accessible(erreur, "dossier de travail", dossierTravail)) {
            return CODE_ERREUR;
        }
        if (dossierBase != null && !accessible(erreur, "dossier de la base", dossierBase)) {
            return CODE_ERREUR;
        }

        service.enregistrer(travail, base);
        sortie.println("Emplacements enregistrés. Effet au prochain démarrage.");
        sortie.println("  Dossier de travail : " + travail.toAbsolutePath());
        sortie.println("  Base               : " + base.resolve("vigiechiro.db").toAbsolutePath());
        sortie.println("Note : le pointeur change, pas les données. Une base pointée vers un dossier vide"
                + " démarre neuve ; copiez votre .db pour l'emporter.");
        return CODE_SUCCES;
    }

    private boolean accessible(PrintWriter erreur, String quoi, Path dossier) {
        Accessibilite verdict = service.sonder(dossier);
        if (verdict == Accessibilite.ACCESSIBLE) {
            return true;
        }
        erreur.println("Le " + quoi + " est inutilisable (" + motif(verdict) + ") : " + dossier);
        return false;
    }

    private static String motif(Accessibilite verdict) {
        return switch (verdict) {
            case PAS_UN_DOSSIER -> "un fichier, pas un dossier";
            case INEXISTANT_NON_CREABLE -> "dossier introuvable et non créable";
            case NON_INSCRIPTIBLE -> "dossier non inscriptible";
            case ACCESSIBLE -> "";
        };
    }

    private void afficher(PrintWriter sortie) {
        ServiceEmplacements.Emplacements courant = service.emplacementsCourants();
        if (json) {
            sortie.println(FormatJson.objet(projeter(courant)));
            return;
        }
        sortie.println("Emplacements (effet au prochain démarrage)");
        sortie.println(String.format(LIGNE, "Dossier de travail", courant.espaceDeTravail()));
        sortie.println(String.format(LIGNE, "Base de données", courant.base()));
        sortie.println(String.format(LIGNE, "Personnalisés", courant.personnalise() ? "oui" : "non (défauts)"));
        sortie.println(String.format(LIGNE, "Défaut travail", courant.espaceDeTravailParDefaut()));
        sortie.println(String.format(LIGNE, "Défaut base", courant.baseParDefaut()));
    }

    private static Map<String, Object> projeter(ServiceEmplacements.Emplacements courant) {
        Map<String, Object> objet = new LinkedHashMap<>();
        objet.put("espaceDeTravail", courant.espaceDeTravail().toString());
        objet.put("base", courant.base().toString());
        objet.put("personnalise", courant.personnalise());
        objet.put("espaceDeTravailParDefaut", courant.espaceDeTravailParDefaut().toString());
        objet.put("baseParDefaut", courant.baseParDefaut().toString());
        return objet;
    }
}
