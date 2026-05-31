package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Écrivain CSV minimal, sans dépendance externe, partagé par toutes les features ({@code
 * commun.model}). Sérialise une <b>liste de lignes</b> (chaque ligne étant une liste de champs) en
 * texte CSV UTF-8 <b>déterministe</b> : ordre des champs préservé, séparateur fixe, fin de ligne
 * {@code \n}.
 *
 * <p>Deux styles de guillemets, qui couvrent les deux variantes Tadarida :
 *
 * <ul>
 *   <li>{@link #avecGuillemets()} : <b>tous</b> les champs sont encadrés de guillemets (format
 *       Tadarida « Brut ») ;
 *   <li>{@link #minimal()} (défaut) : un champ n'est encadré que s'il le faut (il contient le
 *       séparateur, un guillemet ou un saut de ligne) — format Tadarida « Vu » et exports.
 * </ul>
 *
 * <p>Le séparateur est configurable (défaut {@code ';'}, ex. {@code '\t'} pour un THLog). Un champ
 * {@code null} est écrit comme une chaîne vide. Un guillemet littéral est échappé en le doublant
 * ({@code "} → {@code ""}), symétriquement à {@link LecteurCsv}.
 */
public final class EcrivainCsv {

  private final char separateur;
  private final boolean toujoursGuillemeter;

  /** Écrivain au séparateur par défaut {@code ';'}, guillemets posés seulement si nécessaire. */
  public EcrivainCsv() {
    this(LecteurCsv.SEPARATEUR_PAR_DEFAUT, false);
  }

  /**
   * @param separateur caractère séparant les champs d'une même ligne
   * @param toujoursGuillemeter {@code true} pour encadrer tous les champs (format « Brut »)
   */
  public EcrivainCsv(char separateur, boolean toujoursGuillemeter) {
    this.separateur = separateur;
    this.toujoursGuillemeter = toujoursGuillemeter;
  }

  /** Écrivain « Brut » : séparateur {@code ';'}, tous les champs entre guillemets. */
  public static EcrivainCsv avecGuillemets() {
    return new EcrivainCsv(LecteurCsv.SEPARATEUR_PAR_DEFAUT, true);
  }

  /** Écrivain « Vu » / exports : séparateur {@code ';'}, guillemets seulement si nécessaire. */
  public static EcrivainCsv minimal() {
    return new EcrivainCsv(LecteurCsv.SEPARATEUR_PAR_DEFAUT, false);
  }

  /** Séparateur de champs utilisé par cet écrivain. */
  public char separateur() {
    return separateur;
  }

  /** Sérialise {@code lignes} en texte CSV (chaque ligne terminée par {@code \n}). */
  public String versChaine(List<? extends List<String>> lignes) {
    Objects.requireNonNull(lignes, "lignes");
    StringBuilder sb = new StringBuilder();
    for (List<String> ligne : lignes) {
      for (int j = 0; j < ligne.size(); j++) {
        if (j > 0) {
          sb.append(separateur);
        }
        sb.append(formater(ligne.get(j)));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  /**
   * Écrit {@code lignes} dans {@code fichier} en UTF-8 (crée les dossiers parents au besoin).
   *
   * @throws UncheckedIOException si l'écriture échoue
   */
  public void ecrire(Path fichier, List<? extends List<String>> lignes) {
    Objects.requireNonNull(fichier, "fichier");
    try {
      Path parent = fichier.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(fichier, versChaine(lignes), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Écriture CSV impossible : " + fichier, e);
    }
  }

  private String formater(String champ) {
    String valeur = champ == null ? "" : champ;
    boolean besoin =
        toujoursGuillemeter
            || valeur.indexOf(separateur) >= 0
            || valeur.indexOf('"') >= 0
            || valeur.indexOf('\n') >= 0
            || valeur.indexOf('\r') >= 0;
    if (!besoin) {
      return valeur;
    }
    return '"' + valeur.replace("\"", "\"\"") + '"';
  }
}
