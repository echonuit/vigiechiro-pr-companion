package fr.univ_amu.iut.passage.model;

import java.util.Locale;
import java.util.Set;

/// Enregistreur de terrain : Passive Recorder Teensy déposé sur un point d'écoute (C4, table
/// `recorder`).
///
/// Son identité (le n° de série) est **lue depuis le journal du capteur** (`LogPR<n>.txt` du
/// firmware Teensy) au moment de l'import. C'est pourquoi sa clé est **naturelle**
/// (`serial_number`, en `TEXT`) et que
/// [fr.univ_amu.iut.passage.model.dao.EnregistreurDao#insert(Enregistreur)] fait un **upsert** :
/// si le même enregistreur est rencontré sur un nouveau passage, on rafraîchit ses métadonnées
/// plutôt que de planter sur un doublon de clé.
///
/// @param numeroSerie n° de série, clé naturelle (ex. `1925492`)
/// @param versionModele modèle / version du firmware (optionnel, ex. `V1.01, T4.1`)
/// @param commentaire commentaire libre (optionnel : anomalies, remises en état)
public record Enregistreur(String numeroSerie, String versionModele, String commentaire) {

    /// Numéro de repli quand on ignore quel appareil a produit la nuit : le schéma exige un enregistreur
    /// (`recorder_id NOT NULL`), et inventer un vrai numéro serait un mensonge. Posé par la reconstruction
    /// et la synchro ([CreationPassageArchive]).
    public static final String INCONNU = "INCONNU";

    /// Même aveu, côté **import en mode dégradé** (#107) : la carte SD n'avait pas de journal `LogPR<n>.txt`
    /// et les noms de fichiers ne portaient aucun numéro (`JournalDeRepli`).
    public static final String INCONNU_IMPORT = "PR-INCONNU";

    private static final Set<String> SENTINELLES = Set.of(INCONNU, INCONNU_IMPORT);

    /// `true` si `numeroSerie` ne désigne **aucun appareil réel** : absent, vide, ou l'une des sentinelles
    /// d'aveu d'ignorance. Un tel numéro ne doit être ni **publié** vers la plateforme (ce serait fabriquer
    /// une donnée, #1828), ni **adopté** par-dessus un numéro réel déjà connu localement.
    public static boolean estInconnu(String numeroSerie) {
        return numeroSerie == null
                || numeroSerie.isBlank()
                || SENTINELLES.contains(numeroSerie.trim().toUpperCase(Locale.ROOT));
    }
}
