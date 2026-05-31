package fr.univ_amu.iut.importation.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Applique la convention de nommage des enregistrements originaux (R6/R7) sur les fichiers déjà
 * copiés dans le workspace.
 *
 * <p>Le firmware écrit ses fichiers sous leur nom brut {@code PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav}
 * (R7). Avant tout dépôt, chaque fichier reçoit le préfixe {@code
 * Car<carré>-<année>-Pass<n>-<point>-} (R6), via {@link Prefixe#nommerOriginal(String)}. Les tirets
 * sont des <b>tirets du 6</b> (U+002D HYPHEN-MINUS), garantis par {@link Prefixe#TIRET}.
 *
 * <p>Le renommage opère <b>dans le workspace</b> (sur la copie), jamais sur la carte SD (R9 : la
 * source reste intacte ; c'est {@link CopieProtegee} qui a déjà déposé les fichiers ici).
 *
 * <p>L'opération est <b>idempotente</b> : un fichier qui porte déjà le préfixe attendu est laissé
 * en place. On peut donc relancer le renommage sans risque (réimport).
 */
public class Renommeur {

  /**
   * Renomme tous les WAV de {@code dossierBruts} en leur appliquant le préfixe R6 (R7 conserve le
   * suffixe d'origine). Les fichiers déjà préfixés sont inchangés.
   *
   * @param dossierBruts dossier {@code bruts/} contenant les originaux à renommer
   * @param prefixe préfixe de la session (R6)
   * @return la liste des chemins finaux des originaux, triée par nom de fichier
   */
  public List<Path> renommer(Path dossierBruts, Prefixe prefixe) {
    Objects.requireNonNull(dossierBruts, "dossierBruts");
    Objects.requireNonNull(prefixe, "prefixe");
    String prefixeFichier = prefixe.prefixeFichier();
    List<Path> resultats = new ArrayList<>();
    try (Stream<Path> flux = Files.list(dossierBruts)) {
      List<Path> originaux =
          flux.filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".wav"))
              .sorted(Comparator.comparing(p -> p.getFileName().toString()))
              .toList();
      for (Path original : originaux) {
        resultats.add(renommerUn(original, prefixe, prefixeFichier));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Renommage impossible dans " + dossierBruts, e);
    }
    resultats.sort(Comparator.comparing(p -> p.getFileName().toString()));
    return resultats;
  }

  private Path renommerUn(Path original, Prefixe prefixe, String prefixeFichier)
      throws IOException {
    String nom = original.getFileName().toString();
    if (nom.startsWith(prefixeFichier)) {
      return original; // déjà préfixé : idempotent
    }
    Path cible = original.resolveSibling(prefixe.nommerOriginal(nom));
    return Files.move(original, cible, StandardCopyOption.ATOMIC_MOVE);
  }
}
