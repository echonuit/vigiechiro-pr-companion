package fr.univ_amu.iut.validation.model;

/// Format d'un fichier de résultats Tadarida (C12, colonne `detected_format`, R17).
///
/// - [#BRUT] : le fichier livré par Tadarida (`*-observations.csv`) où **tous** les champs sont
///   encadrés de guillemets et les colonnes observateur sont vides ;
/// - [#VU] : le fichier **réinjectable** (`*-observations_Vu.csv`) où les champs ne sont plus
///   systématiquement guillemetés et où l'observateur a pu renseigner ses décisions.
///
/// La détection ne se fait pas sur le nom de fichier (peu fiable) mais sur la **forme** de
/// l'entête : un entête entièrement guillemeté (`"nom du fichier";…`) trahit un fichier Brut, un
/// entête nu (`nom du fichier;…`) un fichier Vu (cf. `ParserCsvTadarida#detecterFormat`).
///
/// Le libellé (`"Brut"` / `"Vu"`) est la valeur persistée dans
/// `identification_results.detected_format` et est volontairement aligné sur l'énum
/// `fr.univ_amu.iut.commun.model` (libellé porteur de sens, parLibelle tolérant à la casse
/// historique).
public enum FormatTadarida {
  BRUT("Brut"),
  VU("Vu");

  private final String libelle;

  FormatTadarida(String libelle) {
    this.libelle = libelle;
  }

  /// Valeur persistée (colonne `detected_format`).
  public String libelle() {
    return libelle;
  }

  /// Retrouve un format depuis son libellé persisté (insensible à la casse).
  public static FormatTadarida parLibelle(String libelle) {
    if (libelle != null) {
      for (FormatTadarida format : values()) {
        if (format.libelle.equalsIgnoreCase(libelle.trim())) {
          return format;
        }
      }
    }
    throw new IllegalArgumentException("Format Tadarida inconnu : " + libelle);
  }
}
