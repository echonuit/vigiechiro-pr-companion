package fr.univ_amu.iut.commun.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/// La **nuit** (au sens des protocoles nocturnes) à laquelle appartient un horodatage : sa **date du
/// soir**. Les enregistrements de chiroptères vont du soir `J` au matin `J+1` ; un horodatage d'avant
/// midi appartient donc à la nuit de la **veille**. Midi ne produit aucun fichier (acquisition
/// ~21:00→06:30) et sépare proprement deux nuits consécutives.
///
/// Concept **partagé** : la partition des imports par nuit ([...importation.model.PartitionNuits], #1696)
/// comme l'hydratation d'un passage reconstruit ([...passage.model.HydratationDepuisBruts], #1724)
/// rangent un horodatage dans sa nuit **de la même façon**. Le placer dans `commun` évite d'en dupliquer
/// la bascule et permet à `passage` de l'utiliser sans dépendre de `importation` (ce serait un cycle,
/// `ArchitectureTest`).
public final class Nuit {

    /// Heure de bascule jour/nuit : midi. Aucun fichier nocturne n'est produit autour de midi, qui
    /// sépare donc sans ambiguïté deux nuits consécutives.
    private static final LocalTime BASCULE = LocalTime.NOON;

    private Nuit() {}

    /// La date de la nuit d'un horodatage : sa date du soir (un horodatage d'avant midi appartient à la
    /// nuit de la veille).
    public static LocalDate de(LocalDateTime horodatage) {
        return horodatage.toLocalTime().isBefore(BASCULE)
                ? horodatage.toLocalDate().minusDays(1)
                : horodatage.toLocalDate();
    }
}
