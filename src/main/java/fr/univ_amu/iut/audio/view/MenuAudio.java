package fr.univ_amu.iut.audio.view;

import fr.univ_amu.iut.audio.viewmodel.AudioViewModel;
import fr.univ_amu.iut.audio.viewmodel.ImportVigieChiroViewModel;
import fr.univ_amu.iut.audio.viewmodel.OngletReglagesAudio;
import fr.univ_amu.iut.audio.viewmodel.PublicationCorrectionsViewModel;
import fr.univ_amu.iut.commun.viewmodel.ReglagesReactifs;
import fr.univ_amu.iut.commun.viewmodel.SourceObservations;
import javafx.beans.binding.Bindings;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;

/// Câblage des items du menu « ☰ » de « Sons & validation » **propres au workflow et à la source**
/// (#1194) : libellés dynamiques Importer/Réimporter, activation des exports, case « inclure
/// validation_mode » persistée, et visibilité par source. Extrait de [SonsValidationController]
/// (unité cohésive) pour garder le contrôleur sous le plafond de concentration (`NcssCount`). Les
/// items restent injectés par le FXML dans le contrôleur, qui les passe ici regroupés.
final class MenuAudio {

    /// Items du ☰ pilotés par le workflow / la source, regroupés en objet-paramètre (patron
    /// [ColonnesAudio.Colonnes]). Les items indépendants de la source (fiche d'espèce, voir sur la
    /// carte, colonnes…) ne passent pas par ici.
    record Items(
            MenuItem importer,
            MenuItem importerVigieChiro,
            Label lblImportVigieChiro,
            MenuItem publierCorrections,
            VBox zonePublierCorrections,
            CheckMenuItem inclureMode,
            MenuItem exporterVu,
            MenuItem exporterObservations,
            MenuItem exporterBiblio,
            MenuItem ouvrirVigieChiro) {}

    private MenuAudio() {
        // Câblage statique : jamais instanciée.
    }

    /// Bindings **une fois pour toutes** (à l'`initialize` du contrôleur) : les items suivent l'état du
    /// view-model quelle que soit la source ouverte ensuite.
    static void cabler(
            Items items,
            AudioViewModel viewModel,
            ImportVigieChiroViewModel importVigieChiro,
            PublicationCorrectionsViewModel publicationCorrections,
            ReglagesReactifs reactifs) {
        // Workflow Tadarida (source ParPassage) : « Importer » tant qu'aucun résultat, « Réimporter »
        // (remplacement après confirmation) une fois un jeu chargé.
        items.importer()
                .textProperty()
                .bind(Bindings.when(viewModel.resultatsDisponiblesProperty())
                        .then("Réimporter un CSV Tadarida…")
                        .otherwise("Importer un CSV Tadarida…"));
        // Import VigieChiro (axe 4.2) : câblage (libellé Importer/Réimporter, désactivation, restitution)
        // délégué à ImportVigieChiroUI. Sa visibilité (workflow + connexion) est gérée dans [#adapter].
        ImportVigieChiroUI.cabler(items.importerVigieChiro(), items.lblImportVigieChiro(), importVigieChiro, viewModel);
        // Publication des corrections (#723) : item désactivé pendant l'envoi et, proactivement, quand le
        // passage n'a aucun ancrage plateforme (#1596). Restitution dédiée.
        PublicationCorrectionsUI.cabler(
                items.publierCorrections(),
                items.zonePublierCorrections(),
                publicationCorrections,
                viewModel.publicationImpossibleProperty());
        items.exporterVu()
                .disableProperty()
                .bind(viewModel.resultatsDisponiblesProperty().not());
        // Export des observations affichées : possible dès qu'il y a au moins une ligne (toutes sources).
        items.exporterObservations().disableProperty().bind(Bindings.isEmpty(viewModel.observationsFiltrees()));
        // Un MenuItem désactivé n'accueille pas de tooltip : pour cet item toujours visible, on surface la
        // cause du grisage dans son libellé (#789), qui n'apparaît que lorsqu'il est effectivement grisé
        // (aucune observation à exporter). L'item « Exporter _Vu » est, lui, masqué hors workflow Tadarida
        // (le menu montre alors « Importer un CSV Tadarida »), donc pas de libellé dynamique dessus.
        items.exporterObservations()
                .textProperty()
                .bind(Bindings.when(Bindings.isEmpty(viewModel.observationsFiltrees()))
                        .then("Exporter les observations (CSV)… (aucune observation à exporter)")
                        .otherwise("Exporter les observations (CSV)…"));
        // Inclure (ou non) la colonne validation_mode dans l'export _Vu (R24). Persisté (#1006) : le VM
        // (recréé à chaque chargement) suit le réglage partagé avec l'onglet « Audio », puis la case du ☰
        // suit le VM. Ordre important pour l'initialisation depuis la valeur persistée.
        viewModel
                .inclureModeProperty()
                .bindBidirectional(reactifs.proprieteBooleen(
                        OngletReglagesAudio.CLE_INCLURE_MODE, OngletReglagesAudio.DEFAUT_INCLURE_MODE));
        items.inclureMode().selectedProperty().bindBidirectional(viewModel.inclureModeProperty());
    }

    /// Visibilité des items **selon la source ouverte** (à chaque `ouvrirSur`) : les actions du workflow
    /// Tadarida (import / export `_Vu` / mode) pour un passage unique, l'export bibliothèque pour le
    /// corpus de référence. Le menu ☰ lui-même reste toujours affiché : il porte aussi le choix des
    /// colonnes, pertinent pour toutes les sources.
    static void adapter(
            Items items,
            SourceObservations source,
            ImportVigieChiroViewModel importVigieChiro,
            PublicationCorrectionsViewModel publicationCorrections,
            ActionDonneesVigieChiro donneesVigieChiro) {
        boolean workflow = source.permetWorkflowTadarida();
        items.importer().setVisible(workflow);
        // Import VigieChiro : workflow Tadarida **et** application connectée (indisponible en capture).
        items.importerVigieChiro().setVisible(workflow && importVigieChiro.disponible());
        // Publication des corrections (#723) : mêmes conditions (workflow d'un passage + connexion).
        items.publierCorrections().setVisible(workflow && publicationCorrections.disponible());
        items.inclureMode().setVisible(workflow);
        items.exporterVu().setVisible(workflow);
        items.exporterBiblio().setVisible(source.permetExportBibliotheque());
        // « Ouvrir les données sur Vigie-Chiro » (#1124) : détail dans ActionDonneesVigieChiro.
        donneesVigieChiro.adapter(items.ouvrirVigieChiro(), source);
    }
}
