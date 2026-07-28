package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.passage.model.MarquageOpportuniste;
import fr.univ_amu.iut.passage.model.Passage;
import fr.univ_amu.iut.passage.model.PropositionCampagne;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// Applique aux passages issus d'un import réussi les **décisions prises dans le rattachement** : la
/// nature opportuniste (#2525) et la campagne (#2631).
///
/// Collaborateur de [ImportationViewModel] (comme `CoordinationNuits` ou `ExecutionImport`), extrait
/// pour ne pas alourdir l'orchestrateur. Les deux décisions voyagent ensemble parce qu'elles ont la
/// même forme : une valeur lue sur le formulaire, appliquée à **chaque** nuit créée, après coup. Une
/// demande d'import cible un seul carré ; ce qui vaut pour la demande vaut pour ses nuits.
///
/// Sans décision (case décochée, aucune campagne, fonctionnalité coupée), aucun effet : les passages
/// restent tels que l'import les a créés.
class MarquageNuitsImportees {

    private final MarquageOpportuniste marquage;
    private final BooleanSupplier estOpportuniste;

    /// Campagne (#2631), **optionnelle** : la fonctionnalité est désactivable.
    private final Optional<PropositionCampagne> campagnes;

    private final Supplier<Long> idCampagneRetenue;

    MarquageNuitsImportees(
            MarquageOpportuniste marquage,
            BooleanSupplier estOpportuniste,
            Optional<PropositionCampagne> campagnes,
            Supplier<Long> idCampagneRetenue) {
        this.marquage = Objects.requireNonNull(marquage, "marquage");
        this.estOpportuniste = Objects.requireNonNull(estOpportuniste, "estOpportuniste");
        this.campagnes = Objects.requireNonNull(campagnes, "campagnes");
        this.idCampagneRetenue = Objects.requireNonNull(idCampagneRetenue, "idCampagneRetenue");
    }

    /// Applique les décisions à la nuit d'un import **mono-nuit**.
    void appliquer(ResultatImport resultat) {
        appliquerA(resultat);
    }

    /// Applique les décisions à **chaque** nuit d'un import multi-nuits.
    void appliquer(ResultatImportMultiNuits resultats) {
        resultats.parNuit().forEach(this::appliquerA);
    }

    private void appliquerA(ResultatImport resultat) {
        Passage passage = resultat.passage();
        if (passage == null || passage.id() == null) {
            return;
        }
        if (estOpportuniste.getAsBoolean()) {
            marquage.definir(passage.id(), true);
        }
        Long idCampagne = idCampagneRetenue.get();
        if (idCampagne != null) {
            campagnes.ifPresent(port -> port.rattacher(passage.id(), idCampagne));
        }
    }
}
