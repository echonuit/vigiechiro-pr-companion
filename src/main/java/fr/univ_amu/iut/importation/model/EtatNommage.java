package fr.univ_amu.iut.importation.model;

/**
 * État de nommage des enregistrements originaux trouvés sur une carte SD.
 *
 * <p>L'enregistreur écrit ses fichiers sous leur nom brut {@code
 * PaRecPR<sn>_<AAAAMMJJ>_<HHMMSS>.wav} (R7). Avant tout dépôt, ils doivent recevoir le préfixe
 * {@code Car<carré>-<année>-Pass<n>-<point>-} (R6). L'inspection (lecture seule) détecte si le
 * dossier est encore <b>brut</b> ou déjà <b>préfixé</b>, ce qui dit au {@link Renommeur} s'il a du
 * travail.
 */
public enum EtatNommage {
  /** Aucun original n'a encore reçu le préfixe R6 (noms bruts de l'enregistreur). */
  BRUT,
  /** Tous les originaux portent déjà le préfixe R6 (dossier déjà renommé). */
  PREFIXE,
  /** Le dossier ne contient aucun enregistrement original (rien à nommer). */
  VIDE
}
