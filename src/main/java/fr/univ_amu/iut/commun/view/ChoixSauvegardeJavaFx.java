package fr.univ_amu.iut.commun.view;

import fr.univ_amu.iut.commun.persistence.InventaireSauvegardes;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/// Fenêtre réelle de choix d'une sauvegarde (#3197) : la liste inventoriée, et « Parcourir… » qui rend
/// la main au sélecteur natif pour une sauvegarde rangée ailleurs.
///
/// C'est ici que vit le `showAndWait()`, et c'est pourquoi le contrat [ChoixSauvegarde] existe : en
/// test, il est remplacé par un double qui répond, faute de quoi le geste entier serait figé. Le
/// contenu, lui, s'éprouve à part ([ContenuChoixSauvegarde]) - même partage que
/// [SuiviProgression] / [DialogueProgression].
public final class ChoixSauvegardeJavaFx implements ChoixSauvegarde {

    private final Supplier<Window> fenetre;

    public ChoixSauvegardeJavaFx(Supplier<Window> fenetre) {
        this.fenetre = Objects.requireNonNull(fenetre, "fenetre");
    }

    @Override
    public Optional<Path> choisir(
            String titre, Path dossier, List<InventaireSauvegardes.Entree> entrees, Supplier<Optional<Path>> repli) {
        Stage modale = new Stage();
        modale.initOwner(fenetre.get());
        Modales.centrerSur(modale, fenetre.get());
        modale.initModality(Modality.WINDOW_MODAL);
        modale.setTitle(titre);

        AtomicReference<Path> choisi = new AtomicReference<>();
        ContenuChoixSauvegarde contenu = new ContenuChoixSauvegarde(
                entrees,
                entree -> {
                    choisi.set(dossier.resolve(entree.nom()));
                    modale.close();
                },
                () -> {
                    // La fenêtre se ferme AVANT le sélecteur natif : deux modales empilées pour un seul
                    // geste, c'est ce que le socle refuse (#2642).
                    modale.close();
                    repli.get().ifPresent(choisi::set);
                },
                modale::close);

        modale.setScene(Habillage.scene(contenu.racine()));
        modale.showAndWait();
        return Optional.ofNullable(choisi.get());
    }
}
