package fr.univ_amu.iut.commun.view;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/// Porteur **injectable** du choix, exact pendant de [ConfirmateurModifiable], [NotificateurModifiable]
/// et [SelecteurFichierModifiable] : par défaut le vrai dialogue (fourni à la construction), remplaçable
/// par un double en test ([#definir]) - un `showAndWait` natif figerait TestFX headless.
///
/// L'écran en détient une instance `final` et l'expose à ses tests, comme il le fait déjà de ses autres
/// porteurs de dialogue.
///
/// @param <T> ce parmi quoi l'utilisateur choisit
public final class DemandeurDeChoixModifiable<T> implements DemandeurDeChoix<T> {

    private DemandeurDeChoix<T> delegue;

    /// Porteur au dialogue **réel** fourni ([ChoixDansListe] ou [ChoixParBoutons], selon que les options
    /// soient des données ou des décisions).
    public DemandeurDeChoixModifiable(DemandeurDeChoix<T> initial) {
        this.delegue = Objects.requireNonNull(initial, "demandeur");
    }

    @Override
    public Optional<T> choisir(String entete, String question, List<T> options, Function<T, String> libelle) {
        return delegue.choisir(entete, question, options, libelle);
    }

    /// Remplace la stratégie de choix (double répondant dans les tests).
    public void definir(DemandeurDeChoix<T> demandeur) {
        this.delegue = Objects.requireNonNull(demandeur, "demandeur");
    }
}
