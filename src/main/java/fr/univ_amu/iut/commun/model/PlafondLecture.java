package fr.univ_amu.iut.commun.model;

import fr.univ_amu.iut.commun.viewmodel.Formats;

/// Taille au-delà de laquelle on **refuse de lire** une entrée externe (#3222), et message de refus
/// associé.
///
/// ## Pourquoi un plafond, alors que les tailles réelles sont modestes
///
/// Le lot #2722 a borné la décompression d'une archive en ressources (#2732) : plus rien ne s'écrit
/// sans qu'on ait accepté combien. L'audit d'harmonisation a trouvé la même forme ailleurs, en
/// **mémoire** cette fois : le corps d'une réponse réseau et le journal du capteur étaient avalés en
/// entier avant qu'on sache s'ils étaient exploitables. Ni l'un ni l'autre n'est exploitable par un
/// tiers, mais tous deux viennent du dehors, et une entrée externe se borne.
///
/// ## Les défauts viennent d'une mesure, pas d'une intuition
///
/// Mesures prises sur la carte réelle `Car640380-2026-Pass2-Z1`, une nuit complète (4 031
/// observations, la nuit de référence des bancs) :
///
/// - `LogPR1925492.txt`, le journal du capteur : **1 862 octets**, 22 lignes. Il consigne des
/// événements de session (démarrage, mode, batterie, veille), **pas** un enregistrement par
/// fichier : sa taille ne suit donc pas le nombre de séquences. Une saison de 250 nuits sur la
/// même carte pèse ~465 Ko, dix ans d'exploitation continue ~4,7 Mo. Le « journal de saison »
/// redouté est petit.
/// - Le CSV d'observations rendu par la plateforme : **446 Kio**, 4 032 lignes. C'est le plus gros
/// corps qu'un appel légitime rapporte, et c'est le cas dimensionnant, pas un cas favorable.
///
/// D'où [#DEFAUT_JOURNAL] (17 000 fois une nuit réelle, 70 fois une saison de dix ans) et
/// [#DEFAUT_CORPS] (147 fois le plus gros corps mesuré). Un plafond posé sans cette mesure refuserait
/// du légitime, comme le plafond de taux de compression de #2732 l'a fait.
///
/// ## Surchargeable, mais pas un réglage
///
/// Chaque plafond se surcharge par **propriété système**, comme les bornes de
/// [fr.univ_amu.iut.importation.model.BornesExtraction]. Il n'y a délibérément pas de réglage dans
/// l'écran Réglages : un naturaliste n'a pas à choisir une taille de corps de réponse. C'est le
/// **message de refus** qui doit nommer la limite atteinte et la surcharge, sans quoi la seule issue
/// serait de renoncer au fichier.
///
/// @param cle la borne au registre des réglages, qui porte son nom court et la propriété système
/// @param octets plafond effectif, en octets
/// @param refus l'annonce du refus, **accordée** (« Fichier de la carte refusé », « Réponse du
///     serveur refusée ») : le message la reprend telle quelle
public record PlafondLecture(CleDeReglage cle, long octets, String refus) {

    /// Propriété système du plafond des fichiers texte lus depuis la carte SD.
    public static final String PROPRIETE_JOURNAL = CleDeReglage.IMPORT_JOURNAL_MAX_OCTETS.propriete();

    /// Propriété système du plafond des corps de réponse HTTP.
    public static final String PROPRIETE_CORPS = CleDeReglage.RESEAU_CORPS_MAX_OCTETS.propriete();

    /// 32 Mio : 17 000 fois le journal d'une nuit réelle, 70 fois une saison de dix ans.
    public static final long DEFAUT_JOURNAL = 32L * 1024 * 1024;

    /// 64 Mio : 147 fois le plus gros corps mesuré (le CSV d'observations d'une nuit, 446 Kio).
    public static final long DEFAUT_CORPS = 64L * 1024 * 1024;

    /// Plafond des fichiers texte que l'utilisateur fournit avec sa carte : journal du capteur, relevé
    /// climatique.
    public static PlafondLecture journalCapteur() {
        return new PlafondLecture(
                CleDeReglage.IMPORT_JOURNAL_MAX_OCTETS,
                octetsDe(PROPRIETE_JOURNAL, DEFAUT_JOURNAL),
                "Fichier de la carte refusé");
    }

    /// Plafond du corps d'une réponse de la plateforme.
    public static PlafondLecture corpsReseau() {
        return new PlafondLecture(
                CleDeReglage.RESEAU_CORPS_MAX_OCTETS,
                octetsDe(PROPRIETE_CORPS, DEFAUT_CORPS),
                "Réponse du serveur refusée");
    }

    private static long octetsDe(String propriete, long defaut) {
        String surcharge = System.getProperty(propriete);
        return surcharge == null || surcharge.isBlank() ? defaut : Long.parseLong(surcharge.trim());
    }

    /// Vrai si `octetsObserves` franchit le plafond. La limite elle-même est **admise** : c'est une
    /// taille acceptée, pas une taille interdite.
    public boolean depasse(long octetsObserves) {
        return octetsObserves > octets;
    }

    /// Refuse `origine` si sa taille franchit le plafond.
    ///
    /// @param octetsObserves taille annoncée ou déjà lue
    /// @param origine ce qu'on lisait, tel que l'utilisateur le reconnaît (nom de fichier, chemin
    ///     d'appel)
    /// @throws EntreeTropVolumineuse si le plafond est franchi ; le message nomme la limite, le chiffre
    ///     observé **et** la surcharge, pour que le refus soit actionnable
    public void exiger(long octetsObserves, String origine) {
        if (depasse(octetsObserves)) {
            throw new EntreeTropVolumineuse(motif(octetsObserves, origine));
        }
    }

    /// Le message de refus, séparé de [#exiger] pour que le transport puisse le porter dans une
    /// réponse refusée plutôt que dans une exception.
    public String motif(long octetsObserves, String origine) {
        return refus + " : « " + origine + " » fait " + Formats.octetsLisibles(octetsObserves)
                + ", au-delà des " + Formats.octetsLisibles(octets) + " admis. "
                + cle().commentRelever();
    }
}
