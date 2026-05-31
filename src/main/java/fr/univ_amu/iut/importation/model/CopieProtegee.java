package fr.univ_amu.iut.importation.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Copie protégée d'un fichier depuis la carte SD vers l'espace de travail (R9).
 *
 * <p><b>Contrainte autoritaire R9</b> : l'application copie systématiquement les fichiers de la SD
 * vers son workspace et <b>n'écrit jamais sur les originaux</b> de la carte. Cette classe
 * matérialise la règle : elle <b>ouvre la source uniquement en lecture</b> ({@link Files#copy} lit
 * l'octet source, n'y écrit pas) et écrit exclusivement dans la destination.
 *
 * <p>Garantie vérifiée : après copie, l'empreinte SHA-256 de la destination est recalculée et
 * comparée à celle de la source. Une divergence (copie tronquée, disque plein) lève une {@link
 * IllegalStateException}. Le test de la règle R9 vérifie en plus que le hash <b>de la source</b>
 * est identique avant et après l'opération (la source n'a pas bougé).
 */
public class CopieProtegee {

  /**
   * Copie {@code source} vers le fichier {@code destination} (les dossiers parents sont créés). La
   * source n'est jamais modifiée. Écrase une destination existante (réimport idempotent).
   *
   * @return le chemin de la destination écrite
   * @throws IllegalStateException si la copie n'est pas fidèle (empreintes différentes)
   */
  public Path copier(Path source, Path destination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(destination, "destination");
    try {
      String empreinteSource = Empreintes.sha256Hex(source);
      Path parent = destination.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
      String empreinteCopie = Empreintes.sha256Hex(destination);
      if (!empreinteSource.equals(empreinteCopie)) {
        throw new IllegalStateException(
            "Copie non fidèle de " + source + " : empreinte SHA-256 divergente après copie.");
      }
      return destination;
    } catch (IOException e) {
      throw new UncheckedIOException("Échec de la copie protégée de " + source, e);
    }
  }

  /**
   * Copie {@code source} dans le dossier {@code dossierDestination}, en conservant le nom de
   * fichier d'origine (le dossier est créé au besoin).
   *
   * @return le chemin du fichier copié
   */
  public Path copierVers(Path source, Path dossierDestination) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(dossierDestination, "dossierDestination");
    return copier(source, dossierDestination.resolve(source.getFileName().toString()));
  }
}
