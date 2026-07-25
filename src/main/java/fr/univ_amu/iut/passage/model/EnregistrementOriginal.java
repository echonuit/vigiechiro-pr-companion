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
/// @param idSession identifiant de la session contenante (FK → `recording_session.id`)
/// @param empreinteContenu taille + empreinte **SHA-256 intégrale** (intégrité bit-à-bit), regroupées
///     en un [EmpreinteContenu] (EPIC #2483). La taille est le pré-contrôle rapide avant le hash quand
///     une réactivation repart des bruts ; ses deux champs valent `null` si importé avant V23 et non
///     rétro-rempli (fichier purgé). Lecture par les accesseurs de compatibilité [#sha256()] et
///     [#tailleOctets()]
public record EnregistrementOriginal(
        Long id,
        String nomFichier,
        String cheminFichier,
        Double dureeSecondes,
        Integer frequenceEchantillonnageHz,
        Long idSession,
        EmpreinteContenu empreinteContenu) {

    /// Normalise l'absence de signature : un `empreinteContenu` nul devient [EmpreinteContenu#ABSENTE],
    /// pour que les lecteurs (dont [#sha256()] et [#tailleOctets()]) n'aient jamais à tester le nul.
    public EnregistrementOriginal {
        if (empreinteContenu == null) {
            empreinteContenu = EmpreinteContenu.ABSENTE;
        }
    }

    /// Empreinte **SHA-256 intégrale** du fichier (intégrité bit-à-bit), ou `null` si inconnue.
    /// Accesseur de compatibilité : la valeur vit désormais dans [#empreinteContenu()] (regroupée avec
    /// la taille, EPIC #2483).
    public String sha256() {
        return empreinteContenu.empreinte();
    }

    /// Taille du fichier en octets, ou `null` si inconnue. Accesseur de compatibilité : la valeur vit
    /// désormais dans [#empreinteContenu()] (regroupée avec l'empreinte, EPIC #2483).
    public Long tailleOctets() {
        return empreinteContenu.tailleOctets();
    }

    /// Constructeur de **compatibilité** (sans taille) : préserve les appels antérieurs à #1299 (la
    /// taille vaut `null`, remplie à l'import ou par le rétro-remplissage). Voir [#tailleOctets()].
    public EnregistrementOriginal(
            Long id,
            String nomFichier,
            String cheminFichier,
            Double dureeSecondes,
            Integer frequenceEchantillonnageHz,
            String sha256,
            Long idSession) {
        this(
                id,
                nomFichier,
                cheminFichier,
                dureeSecondes,
                frequenceEchantillonnageHz,
                idSession,
                new EmpreinteContenu(null, sha256));
    }
}
