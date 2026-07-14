package fr.univ_amu.iut.commun.view;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/// Implémentation de [DemandeurDeChoix] par **un bouton par option**, pour choisir entre des
/// **décisions** : « ces positions déplacées : les **enregistrer**, ou les **abandonner** ? ».
///
/// Une liste déroulante serait ici un recul : deux ou trois décisions se lisent d'un coup d'œil sur des
/// boutons, alors qu'un menu oblige à ouvrir puis valider. C'est pourquoi le contrat [DemandeurDeChoix]
/// laisse la **présentation** à l'appelant.
///
/// Le bouton « Annuler » est ajouté d'office : il n'est pas une option, il est le **renoncement** - la
/// méthode rend alors [Optional#empty()], comme un sélecteur de fichier qu'on ferme.
///
/// @param <T> ce parmi quoi l'utilisateur choisit
public final class ChoixParBoutons<T> implements DemandeurDeChoix<T> {

    /// Fenêtre propriétaire, évaluée **au moment de demander**. Peut rendre `null` : JavaFX l'accepte.
    private final Supplier<Window> fenetre;

    /// Titre de la fenêtre du dialogue (« Positions modifiées »).
    private final String titre;

    public ChoixParBoutons(String titre, Supplier<Window> fenetre) {
        this.titre = Objects.requireNonNull(titre, "titre");
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
    }

    @Override
    public Optional<T> choisir(String entete, String question, List<T> options, Function<T, String> libelle) {
        Alert alerte = new Alert(AlertType.CONFIRMATION);
        alerte.setTitle(titre);
        alerte.setHeaderText(entete);
        alerte.setContentText(question);
        alerte.initOwner(fenetre.get());

        Map<ButtonType, T> parBouton = new LinkedHashMap<>();
        for (T option : options) {
            parBouton.put(new ButtonType(libelle.apply(option), ButtonBar.ButtonData.OTHER), option);
        }
        ButtonType annuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        alerte.getButtonTypes().setAll(parBouton.keySet().stream().toList());
        alerte.getButtonTypes().add(annuler);

        // Fermer la fenêtre (la croix) équivaut à « Annuler » : dans les deux cas, on renonce.
        return alerte.showAndWait().map(parBouton::get);
    }
}
