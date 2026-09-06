package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.view.ContenuDesignation.Entree;
import fr.univ_amu.iut.commun.view.ContenuDesignation.Mode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/// Ce que le dialogue de désignation **montre** d'un dossier : les entrées visibles, ordonnées,
/// filtrées selon ce qu'on cherche.
///
/// Séparée de [ContenuDesignation] parce que ce sont deux métiers : l'une compose des contrôles et
/// réagit aux gestes, l'autre lit le disque et décide de ce qui se voit. Le portail PMD l'a dit avant
/// nous, en refusant la classe unique comme `GodClass` à la clôture de #5282 - le cliquet de
/// production est à zéro, et il n'a pas bougé.
final class EntreesDuDossier {

    private final Mode mode;
    private final FiltreFichier filtre;

    EntreesDuDossier(Mode mode, FiltreFichier filtre) {
        this.mode = mode;
        this.filtre = filtre;
    }

    /// Les entrées d'un dossier, **dossiers d'abord** puis par ordre alphabétique.
    ///
    /// Les fichiers cachés n'y sont jamais : le dialogue du système les propose, celui-ci non, et
    /// c'est plus facile à ajouter qu'à retirer.
    ///
    /// Un dossier devenu illisible **entre** le contrôle et la lecture ne produit pas un refus : la
    /// liste vide est alors exacte, et l'appelant reçoit la raison par [#raisonDeLectureIncomplete].
    List<Entree> de(Path dossier) {
        List<Entree> entrees = new ArrayList<>();
        try (Stream<Path> flux = Files.list(dossier)) {
            flux.filter(chemin -> !chemin.getFileName().toString().startsWith("."))
                    .filter(this::retenu)
                    .sorted(Comparator.comparing((Path chemin) -> !Files.isDirectory(chemin))
                            .thenComparing(chemin -> chemin.getFileName().toString()))
                    .forEach(chemin ->
                            entrees.add(new Entree(chemin.getFileName().toString(), Files.isDirectory(chemin))));
        } catch (IOException lectureImpossible) {
            return null;
        }
        return entrees;
    }

    /// Ce que l'appelant affiche quand [#de] rend `null`.
    static String raisonDeLectureIncomplete() {
        return "Ce dossier n'a pas pu être lu entièrement.";
    }

    /// Un dossier passe toujours. Un fichier ne passe pas du tout en mode dossier, et sinon seulement
    /// si le filtre le retient.
    private boolean retenu(Path chemin) {
        if (Files.isDirectory(chemin)) {
            return true;
        }
        if (mode == Mode.DOSSIER) {
            return false;
        }
        String motif = filtre == null ? null : filtre.motif();
        if (motif == null || !motif.startsWith("*.")) {
            return true;
        }
        return chemin.getFileName()
                .toString()
                .toLowerCase()
                .endsWith(motif.substring(1).toLowerCase());
    }
}
