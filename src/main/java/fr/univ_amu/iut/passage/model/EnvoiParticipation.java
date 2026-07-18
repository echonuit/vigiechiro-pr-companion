package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.api.ResultatEcriture;
import java.util.Objects;
import java.util.Optional;

/// Ce qu'il est advenu d'un envoi de métadonnées vers la participation (#1885) : le résultat de
/// l'écriture, **et** le réalignement d'heures qui a éventuellement eu lieu au passage.
///
/// Le réalignement (#1878) **modifie des données de l'utilisateur** : il ne peut pas rester une
/// information interne. Le taire reviendrait à corriger sa nuit dans son dos - et à le priver du moyen
/// de contester la correction si elle est fausse. C'est la même exigence que celle posée par #1839 pour
/// les échecs, appliquée cette fois à un succès.
///
/// @param ecriture issue de l'écriture sur la plateforme
/// @param realignement heures corrigées d'après les enregistrements, ou **vide** si rien n'a bougé
public record EnvoiParticipation(ResultatEcriture ecriture, Optional<Realignement> realignement) {

    public EnvoiParticipation {
        Objects.requireNonNull(ecriture, "ecriture");
        Objects.requireNonNull(realignement, "realignement");
    }

    /// Envoi sans réalignement : les heures déclarées concordaient déjà avec les enregistrements, ou la
    /// nuit n'a aucune preuve locale (squelette).
    public static EnvoiParticipation sansRealignement(ResultatEcriture ecriture) {
        return new EnvoiParticipation(ecriture, Optional.empty());
    }

    /// Heures d'une nuit, **avant** et **après** réalignement sur ses enregistrements.
    ///
    /// Les deux sont conservées : dire seulement la nouvelle valeur n'apprendrait pas à l'utilisateur ce
    /// qui a été corrigé, ni de combien.
    ///
    /// @param debutAvant heure de début déclarée jusque-là
    /// @param finAvant heure de fin déclarée jusque-là
    /// @param debutApres heure de début attestée par les enregistrements
    /// @param finApres heure de fin attestée par les enregistrements
    public record Realignement(String debutAvant, String finAvant, String debutApres, String finApres) {}
}
