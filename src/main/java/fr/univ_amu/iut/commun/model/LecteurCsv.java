package fr.univ_amu.iut.commun.model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lecteur CSV minimal, sans dépendance externe, partagé par toutes les features ({@code
 * commun.model}). Lit un fichier ou une chaîne UTF-8 et renvoie une <b>liste de lignes</b>, chaque
 * ligne étant une <b>liste de champs</b> (dans l'ordre du fichier, donc <b>déterministe</b>).
 *
 * <p>Le séparateur est configurable (défaut {@code ';'}). Il sert :
 *
 * <ul>
 *   <li>au CSV Tadarida « Brut » (séparateur {@code ;}, tous les champs entre guillemets) ;
 *   <li>au CSV Tadarida « Vu » (séparateur {@code ;}, champs sans guillemets) ;
 *   <li>au journal {@code THLog} (séparateur tabulation : {@code new LecteurCsv('\t')}) ;
 *   <li>à la relecture des exports produits par {@link EcrivainCsv}.
 * </ul>
 *
 * <p>Gestion des guillemets conforme à l'usage RFC&nbsp;4180 : un champ peut être encadré de
 * guillemets {@code "}, un guillemet littéral à l'intérieur s'écrit doublé ({@code ""}), et un
 * champ entre guillemets peut contenir le séparateur ou un saut de ligne. Les fins de ligne {@code
 * \n} et {@code \r\n} sont toutes deux reconnues.
 *
 * <p>La première ligne est souvent une <b>entête</b> : {@link #lire} la renvoie comme première
 * ligne, {@link #lireSansEntete} la retire.
 */
public final class LecteurCsv {

  /** Séparateur de champs par défaut (CSV Tadarida, exports). */
  public static final char SEPARATEUR_PAR_DEFAUT = ';';

  private final char separateur;

  /** Lecteur au séparateur par défaut {@code ';'}. */
  public LecteurCsv() {
    this(SEPARATEUR_PAR_DEFAUT);
  }

  /**
   * Lecteur au séparateur indiqué (ex. {@code ';'} pour Tadarida, {@code '\t'} pour le THLog).
   *
   * @param separateur caractère séparant les champs d'une même ligne
   */
  public LecteurCsv(char separateur) {
    this.separateur = separateur;
  }

  /** Séparateur de champs utilisé par ce lecteur. */
  public char separateur() {
    return separateur;
  }

  /**
   * Lit le fichier {@code fichier} en UTF-8 et renvoie toutes ses lignes (entête comprise).
   *
   * @throws UncheckedIOException si le fichier est illisible
   */
  public List<List<String>> lire(Path fichier) {
    Objects.requireNonNull(fichier, "fichier");
    try {
      return lire(Files.readString(fichier, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Lecture CSV impossible : " + fichier, e);
    }
  }

  /** Variante de {@link #lire(Path)} qui retire la première ligne (l'entête). */
  public List<List<String>> lireSansEntete(Path fichier) {
    return sansEntete(lire(fichier));
  }

  /**
   * Analyse le contenu CSV déjà chargé en mémoire et renvoie toutes ses lignes (entête comprise).
   */
  public List<List<String>> lire(String contenu) {
    Objects.requireNonNull(contenu, "contenu");
    List<List<String>> lignes = new ArrayList<>();
    List<String> champs = new ArrayList<>();
    StringBuilder champ = new StringBuilder();
    boolean dansGuillemets = false;
    int n = contenu.length();
    int i = 0;
    while (i < n) {
      char c = contenu.charAt(i);
      if (dansGuillemets) {
        if (c == '"') {
          if (i + 1 < n && contenu.charAt(i + 1) == '"') {
            champ.append('"'); // guillemet littéral doublé
            i += 2;
          } else {
            dansGuillemets = false;
            i++;
          }
        } else {
          champ.append(c);
          i++;
        }
      } else if (c == '"') {
        dansGuillemets = true;
        i++;
      } else if (c == separateur) {
        champs.add(champ.toString());
        champ.setLength(0);
        i++;
      } else if (c == '\n' || c == '\r') {
        champs.add(champ.toString());
        champ.setLength(0);
        lignes.add(champs);
        champs = new ArrayList<>();
        i += (c == '\r' && i + 1 < n && contenu.charAt(i + 1) == '\n') ? 2 : 1;
      } else {
        champ.append(c);
        i++;
      }
    }
    // Dernière ligne sans saut final : on la pousse seulement si elle a du contenu.
    if (champ.length() > 0 || !champs.isEmpty()) {
      champs.add(champ.toString());
      lignes.add(champs);
    }
    return lignes;
  }

  /** Variante de {@link #lire(String)} qui retire la première ligne (l'entête). */
  public List<List<String>> lireSansEntete(String contenu) {
    return sansEntete(lire(contenu));
  }

  private static List<List<String>> sansEntete(List<List<String>> lignes) {
    return lignes.isEmpty() ? lignes : new ArrayList<>(lignes.subList(1, lignes.size()));
  }
}
