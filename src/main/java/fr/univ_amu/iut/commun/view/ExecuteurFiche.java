package fr.univ_amu.iut.commun.view;

import com.google.inject.ImplementedBy;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Enchaîne la **résolution** de l'URL de fiche (potentiellement réseau, ex. GBIF #922) puis son
/// **ouverture**, en maîtrisant le fil d'exécution : en production la résolution tourne **hors du fil
/// JavaFX** (l'UI ne gèle pas) et l'ouverture est reprogrammée sur le fil JavaFX ; en test tout est
/// **synchrone** (déterministe).
///
/// [ImplementedBy] fixe l'exécution synchrone comme défaut (tests) ; l'application complète surcharge par
/// [ExecuteurFicheAsynchrone] (`CommunModule`).
@ImplementedBy(ExecuteurFicheSynchrone.class)
public interface ExecuteurFiche {

    /// Calcule l'URL via `resolution`, puis la remet à `ouverture`. `resolution` ne doit jamais lever (le
    /// repli est assuré en amont). `ouverture` s'exécute sur le fil adapté à l'ouverture d'un lien.
    void resoudrePuisOuvrir(Supplier<String> resolution, Consumer<String> ouverture);
}
