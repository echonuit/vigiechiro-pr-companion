package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.Prefixe;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/// Re-renomme physiquement le dossier d'une session et les fichiers préfixés qu'il contient, d'un
/// ancien préfixe Vigie-Chiro vers un nouveau (R6).
///
/// Sert la modification rétroactive du rattachement d'un passage (E2.S8) : changer l'année ou le
/// n° de passage change le préfixe `Car<carré>-<année>-Pass<n>-<point>`, donc le nom du dossier de
/// session et de chaque fichier préfixé (originaux dans `bruts/`, séquences dans `transformes/`).
/// Les fichiers non préfixés (journal, relevé climatique à la racine) ne sont que déplacés avec le
/// dossier, sans changement de nom.
///
/// Opération **atomique best-effort** : le dossier est déplacé, puis chaque fichier préfixé est
/// renommé ; à la moindre erreur, les renommages déjà faits sont annulés et le dossier est remis en
/// place. La base, elle, n'est mise à jour qu'ensuite par [ServicePassage] : en cas d'échec disque,
/// rien n'a bougé et la cohérence base/disque est préservée.
public class ReprefixeurSession {

  /// Déplace `racineSession` vers le dossier du nouveau préfixe (même parent) et re-préfixe les
  /// fichiers qu'il contient. Renvoie la nouvelle racine.
  ///
  /// @throws UncheckedIOException en cas d'erreur d'E/S (après tentative de rollback)
  /// @throws IllegalStateException si le dossier cible existe déjà (quadruplet non libre)
  public Path reprefixer(Path racineSession, Prefixe ancien, Prefixe nouveau) {
    Objects.requireNonNull(racineSession, "racineSession");
    Objects.requireNonNull(ancien, "ancien");
    Objects.requireNonNull(nouveau, "nouveau");
    Path nouvelleRacine = racineSession.resolveSibling(nouveau.nomDossierSession());
    if (Files.exists(nouvelleRacine)) {
      throw new IllegalStateException(
          "Le dossier de session cible existe déjà : " + nouvelleRacine);
    }
    try {
      Files.move(racineSession, nouvelleRacine);
    } catch (IOException echec) {
      throw new UncheckedIOException("Déplacement du dossier de session impossible", echec);
    }

    String ancienPrefixe = ancien.prefixeFichier();
    String nouveauPrefixe = nouveau.prefixeFichier();
    Deque<Path[]> faits = new ArrayDeque<>();
    try {
      for (Path fichier : fichiersPrefixes(nouvelleRacine, ancienPrefixe)) {
        Path cible =
            fichier.resolveSibling(
                nouveauPrefixe
                    + fichier.getFileName().toString().substring(ancienPrefixe.length()));
        Files.move(fichier, cible);
        faits.push(new Path[] {fichier, cible});
      }
      return nouvelleRacine;
    } catch (IOException | RuntimeException echec) {
      annuler(faits);
      remettreEnPlace(nouvelleRacine, racineSession);
      throw new UncheckedIOException(
          "Re-préfixage impossible (rollback effectué)", enErreurES(echec));
    }
  }

  /// Nouveau chemin d'un fichier de la session après re-préfixage : relocalisé sous la nouvelle
  /// racine, avec le préfixe de son nom remplacé s'il est présent. Permet à [ServicePassage] de
  /// mettre à jour les chemins persistés exactement comme le disque.
  public static Path cheminApres(
      Path stocke, Path ancienneRacine, Path nouvelleRacine, Prefixe ancien, Prefixe nouveau) {
    Path relatif = ancienneRacine.relativize(stocke);
    Path cible = nouvelleRacine.resolve(relatif);
    String nom = cible.getFileName().toString();
    String ancienPrefixe = ancien.prefixeFichier();
    if (nom.startsWith(ancienPrefixe)) {
      String nouveauNom = nouveau.prefixeFichier() + nom.substring(ancienPrefixe.length());
      return cible.resolveSibling(nouveauNom);
    }
    return cible;
  }

  private static List<Path> fichiersPrefixes(Path racine, String ancienPrefixe) throws IOException {
    try (Stream<Path> flux = Files.walk(racine)) {
      return flux.filter(Files::isRegularFile)
          .filter(chemin -> chemin.getFileName().toString().startsWith(ancienPrefixe))
          .collect(ArrayList::new, List::add, List::addAll);
    }
  }

  private static void annuler(Deque<Path[]> faits) {
    while (!faits.isEmpty()) {
      Path[] paire = faits.pop();
      try {
        Files.move(paire[1], paire[0]);
      } catch (IOException ignore) {
        // best-effort : on poursuit l'annulation des autres renommages
      }
    }
  }

  private static void remettreEnPlace(Path nouvelleRacine, Path racineSession) {
    try {
      Files.move(nouvelleRacine, racineSession);
    } catch (IOException ignore) {
      // best-effort : le dossier reste sous le nouveau nom, mais ses fichiers ont été restaurés
    }
  }

  private static IOException enErreurES(Exception echec) {
    return echec instanceof IOException io ? io : new IOException(echec);
  }
}
