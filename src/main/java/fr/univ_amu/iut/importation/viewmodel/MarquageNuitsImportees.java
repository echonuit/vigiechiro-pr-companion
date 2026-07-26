package fr.univ_amu.iut.importation.viewmodel;

import fr.univ_amu.iut.importation.model.ResultatImport;
import fr.univ_amu.iut.importation.model.ResultatImportMultiNuits;
import fr.univ_amu.iut.passage.model.MarquageOpportuniste;
import fr.univ_amu.iut.passage.model.Passage;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/// Applique la nature **opportuniste** (#2525) aux passages issus d'un import réussi, quand la case du
/// rattachement est cochée. Collaborateur de [ImportationViewModel] (comme `CoordinationNuits` ou
/// `ExecutionImport`), extrait pour ne pas alourdir l'orchestrateur.
///
/// Une demande d'import cible un seul carré : la case s'applique donc à **chaque** passage créé
/// (mono-nuit ou multi-nuits). Décochée (cas courant), aucun effet : les passages restent normaux.
class MarquageNuitsImportees {

    private final MarquageOpportuniste marquage;
    private final BooleanSupplier estOpportuniste;

    MarquageNuitsImportees(MarquageOpportuniste marquage, BooleanSupplier estOpportuniste) {
        this.marquage = Objects.requireNonNull(marquage, "marquage");
        this.estOpportuniste = Objects.requireNonNull(estOpportuniste, "estOpportuniste");
    }

    /// Marque la nuit d'un import **mono-nuit** si la case est cochée.
    void appliquer(ResultatImport resultat) {
        if (estOpportuniste.getAsBoolean()) {
            marquerSiPresent(resultat);
        }
    }

    /// Marque **chaque** nuit d'un import multi-nuits si la case est cochée.
    void appliquer(ResultatImportMultiNuits resultats) {
        if (estOpportuniste.getAsBoolean()) {
            resultats.parNuit().forEach(this::marquerSiPresent);
        }
    }

    private void marquerSiPresent(ResultatImport resultat) {
        Passage passage = resultat.passage();
        if (passage != null && passage.id() != null) {
            marquage.definir(passage.id(), true);
        }
    }
}
