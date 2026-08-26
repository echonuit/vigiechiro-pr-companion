package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ResultatEcriture;
import java.util.Objects;
import java.util.Optional;

/// Ce qu'il est advenu d'un envoi de métadonnées vers la participation (#1885) : ce que l'écriture a
/// donné, **et** le réalignement d'heures qui a éventuellement eu lieu au passage.
///
/// Le réalignement (#1878) **modifie des données de l'utilisateur** : il ne peut pas rester une
/// information interne. Le taire reviendrait à corriger sa nuit dans son dos, et à le priver du moyen
/// de contester la correction si elle est fausse. Il est donc porté par **tous** les cas, y compris
/// ceux où rien n'est parti : le réalignement a eu lieu quand même.
///
/// Le type est **scellé** parce qu'un envoi a trois issues et non deux (#4552) : la plateforme a
/// accepté, la plateforme a refusé, ou nous avons renoncé. Les confondre fait dire à l'application
/// que Vigie-Chiro a refusé là où elle n'a rien vu passer.
public sealed interface EnvoiParticipation {

    /// Heures corrigées d'après les enregistrements, ou **vide** si rien n'a bougé.
    Optional<Realignement> realignement();

    /// L'écriture est **partie** : [ResultatEcriture] dit ce que la plateforme en a fait.
    ///
    /// @param ecriture issue de l'écriture sur la plateforme
    /// @param realignement heures corrigées d'après les enregistrements, ou vide
    record Ecrit(ResultatEcriture ecriture, Optional<Realignement> realignement) implements EnvoiParticipation {
        public Ecrit {
            Objects.requireNonNull(ecriture, "ecriture");
            Objects.requireNonNull(realignement, "realignement");
        }
    }

    /// **Rien n'a été écrit** : la participation a changé sur la plateforme entre notre lecture et
    /// notre envoi, et écrire par-dessus aurait effacé le travail d'un autre poste (#4552).
    ///
    /// Ce n'est **pas** un refus de la plateforme : elle n'a rien vu passer. C'est nous qui renonçons,
    /// et le dire autrement ferait accuser Vigie-Chiro d'un geste qui est le nôtre.
    ///
    /// @param realignement heures corrigées d'après les enregistrements, ou vide
    record ModifieEntreTemps(Optional<Realignement> realignement) implements EnvoiParticipation {
        public ModifieEntreTemps {
            Objects.requireNonNull(realignement, "realignement");
        }
    }

    /// Envoi parti **sans réalignement** : les heures déclarées concordaient déjà avec les
    /// enregistrements, ou la nuit n'a aucune preuve locale (squelette).
    static EnvoiParticipation sansRealignement(ResultatEcriture ecriture) {
        return new Ecrit(ecriture, Optional.empty());
    }

    /// Envoi parti, avec le réalignement éventuellement survenu au passage.
    static EnvoiParticipation ecrit(ResultatEcriture ecriture, Optional<Realignement> realignement) {
        return new Ecrit(ecriture, realignement);
    }

    /// Heures d'une nuit, **avant** et **après** réalignement sur ses enregistrements.
    ///
    /// Les deux sont conservées : dire seulement la nouvelle valeur n'apprendrait pas à l'utilisateur
    /// ce qui a été corrigé, ni de combien.
    ///
    /// @param debutAvant heure de début déclarée jusque-là
    /// @param finAvant heure de fin déclarée jusque-là
    /// @param debutApres heure de début attestée par les enregistrements
    /// @param finApres heure de fin attestée par les enregistrements
    record Realignement(String debutAvant, String finAvant, String debutApres, String finApres) {}
}
