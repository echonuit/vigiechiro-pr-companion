package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ParticipationDetail;
import fr.univ_amu.iut.commun.model.Horloge;
import fr.univ_amu.iut.commun.model.ReleveParticipation;
import fr.univ_amu.iut.commun.model.dao.ReleveParticipationDao;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// Note ce que la plateforme portait pour une participation, à l'instant où nous l'avons lue
/// (#4706, EPIC #4640).
///
/// Ce relevé sert de **base** : constater un conflit demande la base, notre valeur et la leur, et
/// sans lui une modification faite ici ne se distingue pas d'une modification faite là-bas.
///
/// **Il ne dit pas ce qui est vrai, il dit ce que nous avions vu.** Il ne se montre jamais à
/// l'utilisateur comme une donnée.
public final class ReleveDeParticipation {

    private final ReleveParticipationDao releves;
    private final Horloge horloge;

    public ReleveDeParticipation(ReleveParticipationDao releves, Horloge horloge) {
        this.releves = Objects.requireNonNull(releves, "releves");
        this.horloge = Objects.requireNonNull(horloge, "horloge");
    }

    /// La **base** du passage : ce que la plateforme portait à notre dernière lecture, ou vide si nous
    /// ne l'avons jamais lue.
    ///
    /// Un vide n'est pas une anomalie : une nuit antérieure à la migration n'a pas de relevé, et la
    /// question du conflit reste alors sans réponse. C'est à l'appelant de dire ce qu'il en fait.
    public Optional<ReleveParticipation> base(Long idPassage) {
        return releves.pour(idPassage);
    }

    /// Note l'état distant lu, en écrasant le relevé précédent du même passage.
    ///
    /// Seuls les champs que le `PATCH` écrit sont retenus : comparer un champ que nous n'envoyons
    /// pas ne servirait à rien. Le dictionnaire de configuration, lui, est gardé **entier**, clés
    /// inconnues comprises, sans quoi le relevé ne pourrait pas dire qu'un champ hors de notre
    /// portée a bougé.
    public void noter(Long idPassage, String participationId, ParticipationDetail distant) {
        releves.enregistrer(new ReleveParticipation(
                idPassage,
                participationId,
                distant.dateDebut(),
                distant.dateFin(),
                distant.meteo(),
                distant.configuration() == null ? Map.of() : distant.configuration(),
                horloge.maintenant().toString()));
    }
}
