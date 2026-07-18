package fr.univ_amu.iut.commun.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;

/// Action de ligne **« Ouvrir sur Vigie-Chiro »** (#1799) : ouvre dans le navigateur la page de la
/// **participation** liée au passage de la ligne sélectionnée, pour comparer le local et la plateforme
/// sans passer par l'écran du passage.
///
/// Quand le passage n'est **pas lié** à la plateforme, l'item est désactivé et le motif passe dans le
/// **libellé** : un [MenuItem] désactivé n'affiche pas d'infobulle (patron « Fiche de l'espèce », #789).
public final class ActionVigieChiroPassage {

    private static final String LIBELLE = "Ouvrir sur Vigie-Chiro";
    private static final String SUFFIXE_NON_LIE = " (passage non lié)";

    private final PortailVigieChiro portail;
    private final OuvreurDeLien ouvreurDeLien;

    @Inject
    public ActionVigieChiroPassage(PortailVigieChiro portail, OuvreurDeLien ouvreurDeLien) {
        this.portail = Objects.requireNonNull(portail, "portail");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
    }

    /// Item de menu contextuel suivant la sélection de `table` ; `idPassage` extrait l'identifiant du
    /// passage porté par la ligne.
    public <T> MenuItem item(TableView<T> table, Function<T, Long> idPassage) {
        MenuItem item = new MenuItem(LIBELLE);
        table.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, ancienne, ligne) -> adapter(item, ligne == null ? null : idPassage.apply(ligne)));
        adapter(item, null);
        item.setOnAction(evenement -> {
            T selection = table.getSelectionModel().getSelectedItem();
            if (selection != null) {
                portail.pageParticipation(idPassage.apply(selection)).ifPresent(ouvreurDeLien::ouvrir);
            }
        });
        return item;
    }

    private void adapter(MenuItem item, Long idPassage) {
        if (idPassage == null) {
            item.setDisable(true);
            item.setText(LIBELLE);
            return;
        }
        Optional<String> lien = portail.pageParticipation(idPassage);
        item.setDisable(lien.isEmpty());
        item.setText(lien.isEmpty() ? LIBELLE + SUFFIXE_NON_LIE : LIBELLE);
    }
}
