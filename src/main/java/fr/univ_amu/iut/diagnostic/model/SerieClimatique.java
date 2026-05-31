package fr.univ_amu.iut.diagnostic.model;

import java.util.List;
import java.util.Objects;

/// Série climatique d'une nuit, prête pour un graphe (P6-CA1), avec gestion explicite de
/// l'absence de relevé (R20).
///
/// **R20 (relevé optionnel)** : la sonde T°/hygrométrie peut être absente ou défaillante.
/// L'onglet diagnostic doit **signaler** cette absence plutôt que la masquer. Le drapeau
/// [#present()] distingue donc deux cas :
///
/// - [#absente()] : aucun relevé climatique rattaché à la session (`present == false` ⇒ l'IHM
///   affiche « relevé climatique absent ») ;
/// - [#presente(List)] : un relevé existe (`present == true`) ; la liste de mesures peut malgré
///   tout être vide si le fichier a été perdu (journal tronqué, R19).
public final class SerieClimatique {

  private final boolean present;
  private final List<MesureClimatique> mesures;

  private SerieClimatique(boolean present, List<MesureClimatique> mesures) {
    this.present = present;
    this.mesures = List.copyOf(Objects.requireNonNull(mesures, "mesures"));
  }

  /// Aucun relevé climatique pour la session (R20, absence à signaler).
  public static SerieClimatique absente() {
    return new SerieClimatique(false, List.of());
  }

  /// Un relevé existe ; `mesures` porte la série lue (éventuellement vide).
  public static SerieClimatique presente(List<MesureClimatique> mesures) {
    return new SerieClimatique(true, mesures);
  }

  /// `true` si un relevé climatique est rattaché à la session (R20).
  public boolean present() {
    return present;
  }

  /// La série de mesures (immuable, dans l'ordre chronologique du fichier).
  public List<MesureClimatique> mesures() {
    return mesures;
  }

  /// Nombre de mesures de la série.
  public int nombreMesures() {
    return mesures.size();
  }
}
