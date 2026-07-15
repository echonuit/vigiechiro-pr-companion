package fr.univ_amu.iut.passage.model;

import java.util.List;
import java.util.function.Consumer;

/// Les observations à reconstruire, quelle que soit leur source (CSV #1565 ou pagination `donnees`) : les
/// **noms de fichiers** (pour recréer les séquences), le **nombre d'observations** (pour le rapport) et le
/// **geste d'import** (rattache les observations aux séquences une fois celles-ci recréées). Produite par
/// [PlateformeReconstruction], consommée par [ServiceReconstructionPassages].
record ObservationsAReconstruire(List<String> nomsFichiers, int nbObservations, Consumer<Long> geste) {

    /// Exécute le geste d'import concret (CSV ou donnees, déjà choisi) sur le passage.
    void importer(Long idPassage) {
        geste.accept(idPassage);
    }
}
