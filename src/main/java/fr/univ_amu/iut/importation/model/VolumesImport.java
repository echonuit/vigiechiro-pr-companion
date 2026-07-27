package fr.univ_amu.iut.importation.model;

/// Ce qu'un import a **lu** et ce qu'il a **écrit**, en octets (#2358).
///
/// C'est la seule donnée que le compte rendu chiffré réclamait et que personne ne produisait. Les deux
/// volumes écrits existaient déjà - la [fr.univ_amu.iut.passage.model.SessionDEnregistrement] les
/// persiste - mais le volume **lu sur la carte** n'était mesuré nulle part, alors que c'est lui qui donne
/// l'échelle : sans lui, « 6,8 Go écrits » ne se compare à rien.
///
/// Regroupés en un type valeur plutôt qu'ajoutés un à un à [ResultatImport] : trois `long` voisins
/// s'échangent en silence (doctrine de l'EPIC #2483, Parameter-Object d'abord), et les trois forment un
/// vrai concept - ils se rendent ensemble, à la même échelle, dans le même bloc de barres.
///
/// @param octetsLus volume des originaux de la nuit **lus sur la source**, que la copie des bruts soit
///     demandée ou non : c'est le volume de la nuit, pas celui de ce qu'on en garde
/// @param octetsBruts volume des originaux **conservés dans le workspace** ; `0` en mode sans copie
///     (ADR 0036 : la conservation des bruts est une option), où rien n'est stocké localement
/// @param octetsSequences volume des séquences d'écoute produites (R10)
public record VolumesImport(long octetsLus, long octetsBruts, long octetsSequences) {

    /// Un import qui n'a rien lu ni rien écrit : les appelants et tests qui ne mesurent pas de volume.
    public static final VolumesImport AUCUN = new VolumesImport(0L, 0L, 0L);

    public VolumesImport {
        exigerPositif(octetsLus, "octetsLus");
        exigerPositif(octetsBruts, "octetsBruts");
        exigerPositif(octetsSequences, "octetsSequences");
    }

    /// Total écrit sur le disque : les bruts conservés **plus** les séquences produites. C'est ce que
    /// l'import a coûté en place, et la barre à comparer au volume lu.
    public long octetsEcrits() {
        return octetsBruts + octetsSequences;
    }

    /// Somme de deux mesures, pour agréger les nuits d'un import multi-nuits ([ResultatImportMultiNuits]) :
    /// chaque nuit lit sa part de la carte et écrit sa propre session, les volumes s'additionnent.
    public VolumesImport plus(VolumesImport autre) {
        return new VolumesImport(
                octetsLus + autre.octetsLus, octetsBruts + autre.octetsBruts, octetsSequences + autre.octetsSequences);
    }

    /// `true` si rien n'a été mesuré. Le compte rendu s'en sert pour **taire** le bloc des volumes plutôt
    /// que d'afficher deux barres à zéro, qui se liraient comme un import qui n'aurait rien produit.
    public boolean estVide() {
        return octetsLus == 0 && octetsEcrits() == 0;
    }

    private static void exigerPositif(long octets, String nom) {
        if (octets < 0) {
            throw new IllegalArgumentException(nom + " ne peut pas être négatif : " + octets);
        }
    }
}
