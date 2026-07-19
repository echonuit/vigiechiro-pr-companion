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
/// @param cheminFichier d'où vient le fichier : le sous-dossier `bruts/` (R22) quand l'import l'a
///     copié, **le chemin sur la carte SD** quand il ne l'a pas fait. Dans ce second cas c'est une
///     **provenance, pas un localisateur** : le montage aura disparu. Aucun parcours de récupération
///     ne s'en sert — la réactivation apparie par [#nomFichier], jamais par ce chemin — et l'audit
///     l'ignore dès lors que la session déclare « non stocké localement » (#2062). Ne pas écrire de
///     code qui suppose ce fichier ouvrable.
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
