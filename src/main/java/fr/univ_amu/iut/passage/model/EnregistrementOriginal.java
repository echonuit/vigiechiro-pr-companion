package fr.univ_amu.iut.passage.model;

/// Enregistrement original : fichier audio brut sortant de l'enregistreur après copie protégée et
/// renommage (C7, table `original_recording`). Ultrason mono 16 bits à 384 kHz, **inaudible** sans
/// transformation, conservé intact comme référence ultime.
///
/// Rattaché à une session (`session_id`, `ON DELETE CASCADE`) ; un original est ensuite découpé en
/// plusieurs [SequenceDEcoute].
///
/// @param id clé technique, `null` avant insertion
/// @param nomFichier nom de fichier (préfixe R6 + suffixe enregistreur R7)
/// @param cheminFichier chemin sur disque, sous-dossier `bruts/` (R22)
/// @param dureeSecondes durée en secondes (optionnel, typiquement 2-30 s)
/// @param frequenceEchantillonnageHz fréquence d'échantillonnage en Hz (optionnel, ex. 384000)
/// @param sha256 empreinte SHA-256 hexadécimale (optionnel, intégrité bit-à-bit)
/// @param idSession identifiant de la session contenante (FK → `recording_session.id`)
/// @param tailleOctets taille du fichier en octets (#1299), pré-contrôle rapide avant le [#sha256]
///     intégral quand une réactivation repart des bruts ; `null` si importé avant V23 et non
///     rétro-rempli (fichier purgé)
public record EnregistrementOriginal(
        Long id,
        String nomFichier,
        String cheminFichier,
        Double dureeSecondes,
        Integer frequenceEchantillonnageHz,
        String sha256,
        Long idSession,
        Long tailleOctets) {

    /// Constructeur de **compatibilité** (sans taille) : préserve les appels antérieurs à #1299 (la
    /// taille vaut `null`, remplie à l'import ou par le rétro-remplissage). Voir [#tailleOctets].
    public EnregistrementOriginal(
            Long id,
            String nomFichier,
            String cheminFichier,
            Double dureeSecondes,
            Integer frequenceEchantillonnageHz,
            String sha256,
            Long idSession) {
        this(id, nomFichier, cheminFichier, dureeSecondes, frequenceEchantillonnageHz, sha256, idSession, null);
    }
}
