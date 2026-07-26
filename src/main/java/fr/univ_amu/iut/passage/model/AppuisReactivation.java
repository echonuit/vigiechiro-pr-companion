package fr.univ_amu.iut.passage.model;

import fr.univ_amu.iut.commun.model.ImportObservations;
import java.util.Objects;
import java.util.Optional;

/// Ce que la réactivation **emprunte à d'autres features**, regroupé en objet-paramètre (patron
/// `AppuisPassage` / `AppuisAudio`).
///
/// Ces cinq collaborateurs partagent exactement un trait, et c'est ce trait qui en fait un objet plutôt
/// qu'un sac : ils sont **tous optionnels, pour la même raison** (ADR 0003). Une feature est
/// désactivable (#1057) et la passerelle VigieChiro n'existe qu'en connexion ; un module ne peut donc
/// pas les exiger en dur sans que l'injecteur cesse de se construire. Absents, la réactivation
/// fonctionne toujours - elle en fait moins, et elle le **dit**.
///
/// Ce qui se dégrade quand l'un manque :
///
/// - `crisAttendus` : la cascade (#1309) perd son dernier échelon, l'acoustique ; la vérification reste
///   structurelle ;
/// - `regeneration` et `inventaireBruts` : la voie « bruts » ne peut plus recalculer les tranches depuis
///   les enregistrements d'origine ;
/// - `importObservations` : la phase d'ancrage (#1571) n'a rien pour rapatrier les `donnees` ;
/// - `hydratationSquelette` : une nuit rapatriée de Vigie-Chiro (#2555) ne peut pas récupérer ses
///   observations, donc pas se réactiver - c'est le seul cas où l'absence est un **refus**, et il est
///   annoncé en amont par le grisage du bouton (#789).
///
/// @param crisAttendus cris attendus d'une séquence, pour l'échelon acoustique de la cascade (`validation`)
/// @param regeneration régénération d'une tranche depuis son enregistrement d'origine (`importation`)
/// @param inventaireBruts inventaire des bruts d'un dossier, log compris (`importation`)
/// @param importObservations import des observations d'une nuit (`validation`, #1264)
/// @param hydratationSquelette rapatriement des observations d'une nuit récupérée (#2555)
public record AppuisReactivation(
        Optional<CrisAttendus> crisAttendus,
        Optional<RegenerationSequences> regeneration,
        Optional<InventaireBrutsSource> inventaireBruts,
        Optional<ImportObservations> importObservations,
        Optional<HydratationSquelette> hydratationSquelette) {

    public AppuisReactivation {
        Objects.requireNonNull(crisAttendus, "crisAttendus");
        Objects.requireNonNull(regeneration, "regeneration");
        Objects.requireNonNull(inventaireBruts, "inventaireBruts");
        Objects.requireNonNull(importObservations, "importObservations");
        Objects.requireNonNull(hydratationSquelette, "hydratationSquelette");
    }

    /// Aucun appui : l'injecteur partiel d'un test, ou une application où tout ce qui est optionnel est
    /// éteint. La réactivation s'y limite au rebranchement direct de séquences retrouvées telles quelles.
    public static AppuisReactivation aucun() {
        return new AppuisReactivation(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
