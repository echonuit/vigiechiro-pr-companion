package fr.univ_amu.iut.commun.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Window;

/// Implémentation de [DemandeurDeChoix] par **liste déroulante** (`ChoiceDialog`), pour choisir parmi des
/// **données** : les participations d'un compte VigieChiro, par exemple, dont on ne sait pas combien il y
/// en aura.
///
/// Jumelle de [NotificationDialogue] et de [SelecteurFichierJavaFx] : c'est le seul endroit du socle qui
/// construise un `ChoiceDialog`.
///
/// @param <T> ce parmi quoi l'utilisateur choisit
public final class ChoixDansListe<T> implements DemandeurDeChoix<T> {

    /// Fenêtre propriétaire, évaluée **au moment de demander** (l'écran peut ne pas encore être attaché à
    /// une fenêtre quand l'action est construite). Peut rendre `null` : JavaFX l'accepte.
    private final Supplier<Window> fenetre;

    public ChoixDansListe(Supplier<Window> fenetre) {
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
    }

    @Override
    public Optional<T> choisir(String entete, String question, List<T> options, Function<T, String> libelle) {
        // La liste déroulante travaille sur des libellés : on garde le lien vers l'option d'origine, en
        // conservant l'ordre d'affichage voulu par l'appelant.
        Map<String, T> parLibelle = new LinkedHashMap<>();
        for (T option : options) {
            parLibelle.put(libelle.apply(option), option);
        }
        String premier = parLibelle.keySet().iterator().next();
        ChoiceDialog<String> dialogue = new ChoiceDialog<>(premier, parLibelle.keySet());
        dialogue.setHeaderText(entete);
        dialogue.setContentText(question);
        dialogue.initOwner(fenetre.get());
        return dialogue.showAndWait().map(parLibelle::get);
    }
}
