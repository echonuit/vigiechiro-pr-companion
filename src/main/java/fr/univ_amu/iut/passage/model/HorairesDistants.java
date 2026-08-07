package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.FuseauDuPoint;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/// Les bornes d'une nuit **telles que la plateforme les rend**, ramenées à l'heure murale du site
/// auquel elles appartiennent (#3442).
///
/// ## Pourquoi une classe, et pas trois lignes dans le service
///
/// Deux raisons, et la seconde est la vraie.
///
/// La première est mécanique : porter le [FuseauDuPoint] et ses quatre conversions dans
/// [ServiceReconstructionPassages] a fait franchir à cette classe le seuil `GodClass` du portail
/// qualité. Le dépôt refuse `@SuppressWarnings` ; on extrait.
///
/// La seconde est que ces conversions **forment un concept** : *une borne distante se lit dans le
/// fuseau de son point*. Éparpillées, elles se réécrivent - et l'une d'elles finit par oublier le
/// fuseau. C'est exactement ce qui est arrivé sur #3434, où la moitié « lecture » du correctif de
/// fuseau avait été omise, et où seule l'intégration continue l'a vu.
///
/// Ici, il n'existe plus qu'un endroit où une borne distante devient une heure locale.
final class HorairesDistants {

    private final FuseauDuPoint fuseaux;

    HorairesDistants(FuseauDuPoint fuseaux) {
        this.fuseaux = Objects.requireNonNull(fuseaux, "fuseaux");
    }

    /// La borne `borne` (ISO renvoyé par la plateforme) à l'heure murale du site du point `idPoint`, ou
    /// vide si la borne est absente ou illisible.
    Optional<LocalDateTime> lire(Long idPoint, String borne) {
        return ParticipationOrpheline.horodatage(borne, fuseaux.pour(idPoint));
    }

    /// Idem, quand le point n'est pas encore résolu : rien à lire, puisqu'on ne saurait pas dans quel
    /// fuseau. L'appelant traite ce vide comme il traite une borne absente - il refuse déjà de
    /// reconstruire sans point.
    Optional<LocalDateTime> lire(Optional<Long> idPoint, String borne) {
        return idPoint.flatMap(point -> lire(point, borne));
    }
}
