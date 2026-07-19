package fr.univ_amu.iut.audio.view;

import com.google.inject.Inject;
import fr.univ_amu.iut.commun.model.PortailVigieChiro;
import fr.univ_amu.iut.commun.view.OuvreurDeLien;
import fr.univ_amu.iut.commun.viewmodel.ContextePassage;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.control.MenuItem;

/// Action « Ouvrir les données sur Vigie-Chiro » du menu ☰ (#1124) : ouvre dans le navigateur la
/// page des **données de la participation** liée au passage courant, pour comparer le local et la
/// plateforme. Item visible seulement quand la source cible un passage, et **désactivé avec le
/// motif dans le libellé** quand le passage n’est pas lié (un `MenuItem` n’a pas de tooltip, patron
/// « Fiche de l’espèce »). Collaborateur extrait de [SonsValidationController] (plafond NcssCount).
final class ActionDonneesVigieChiro {

    private static final String LIBELLE = "Ouvrir les données sur Vigie-Chiro";

    private final PortailVigieChiro portail;
    private final OuvreurDeLien ouvreurDeLien;

    @Inject
    ActionDonneesVigieChiro(PortailVigieChiro portail, OuvreurDeLien ouvreurDeLien) {
        this.portail = Objects.requireNonNull(portail, "portail");
        this.ouvreurDeLien = Objects.requireNonNull(ouvreurDeLien, "ouvreurDeLien");
    }

    /// Adapte l’item à la source affichée (visibilité, activation, libellé).
    void adapter(MenuItem item, SourceObservations source) {
        ContextePassage contexte = source.contexteDuPassage();
        Optional<String> lien = contexte == null ? Optional.empty() : portail.pageDonnees(contexte.idPassage());
        item.setVisible(contexte != null);
        item.setDisable(lien.isEmpty());
        item.setText(lien.isEmpty() ? LIBELLE + " (passage non lié)" : LIBELLE);
    }

    /// Ouvre la page des données de la participation liée (onAction de l’item).
    void ouvrir(SourceObservations source) {
        ContextePassage contexte = source.contexteDuPassage();
        if (contexte != null) {
            portail.pageDonnees(contexte.idPassage()).ifPresent(ouvreurDeLien::ouvrir);
        }
    }
}
