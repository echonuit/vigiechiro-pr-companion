package fr.univ_amu.iut.qualification.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ProfilVigieChiro;
import fr.univ_amu.iut.commun.view.ExecuteurTacheSynchrone;
import fr.univ_amu.iut.commun.view.FiltreFichier;
import fr.univ_amu.iut.commun.view.IndicateurOccupation;
import fr.univ_amu.iut.commun.view.NiveauNotification;
import fr.univ_amu.iut.commun.view.SelecteurFichier;
import fr.univ_amu.iut.commun.view.SelecteursDeTest;
import fr.univ_amu.iut.connexion.model.StockageConnexion;
import fr.univ_amu.iut.qualification.model.ServiceEmport;
import fr.univ_amu.iut.qualification.viewmodel.SelectionEcouteViewModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/// Les gestes de l'emport, pris seuls : ce qu'ils disent sans connexion, et quand ils font relire
/// la sélection affichée.
@ExtendWith(ApplicationExtension.class)
class GestesEmportQualificationTest {

    private static final long NUIT = 7L;
    private static final ProfilVigieChiro RELECTEUR =
            new ProfilVigieChiro("507f1f77bcf86cd799439011", "chiro-pierre", "Observateur");

    @TempDir
    private Path dossier;

    private ServiceEmport service;
    private StockageConnexion connexion;
    private SelectionEcouteViewModel selectionVm;
    private GestesEmportQualification gestes;
    private final List<NiveauNotification> niveaux = new ArrayList<>();
    private final List<String> comptesRendus = new ArrayList<>();

    @Start
    void start(Stage stage) {
        service = mock(ServiceEmport.class);
        connexion = mock(StockageConnexion.class);
        selectionVm = mock(SelectionEcouteViewModel.class);
        gestes = new GestesEmportQualification(service, connexion, () -> stage, SelecteursDeTest.auDefaut());
        gestes.relierAux(
                (niveau, entete, message) -> {
                    niveaux.add(niveau);
                    comptesRendus.add(entete + " | " + message);
                },
                message -> true);
        gestes.rechargerPar(new IndicateurOccupation(new StackPane(), new ExecuteurTacheSynchrone()), selectionVm);
        gestes.surNuit(NUIT);
    }

    @Test
    @DisplayName("Sans connexion, « Renvoyer mon avis… » le dit par un avertissement et n'écrit rien")
    void sans_connexion_renvoyer_son_avis_le_dit_et_n_ecrit_rien() {
        when(connexion.profil()).thenReturn(Optional.empty());
        Path avis = dossier.resolve("avis.zip");
        gestes.selecteur().definir(selecteur(avis));

        gestes.gestes().renvoyerAvis().run();

        assertThat(niveaux)
                .as("un geste qui ne fait rien doit le dire : sans compte rendu, on croit l'avis parti")
                .containsExactly(NiveauNotification.AVERTISSEMENT);
        assertThat(comptesRendus)
                .as("dans les mots de la commande jumelle, qui dit quoi faire")
                .singleElement()
                .satisfies(compte -> assertThat(compte).contains("reconnectez-vous"));
        assertThat(Files.exists(avis)).as("et aucun fichier n'est écrit").isFalse();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Un paquet ouvert fait relire la sélection de la nuit affichée")
    void un_paquet_ouvert_fait_relire_la_selection() throws IOException {
        when(connexion.profil()).thenReturn(Optional.of(RELECTEUR));
        when(service.reprendre(any(), any())).thenReturn(new ServiceEmport.BilanReprise(NUIT, 3L, 2, "chiro-pierre"));
        gestes.selecteur().definir(selecteur(dossier.resolve("nuit.zip")));

        gestes.gestes().ouvrirPaquetRecu().run();

        verify(selectionVm).charger(NUIT);
    }

    @Test
    @DisplayName("Un paquet refusé ne fait rien relire : l'écran n'a pas changé de vérité")
    void un_paquet_refuse_ne_fait_rien_relire() throws IOException {
        when(connexion.profil()).thenReturn(Optional.of(RELECTEUR));
        when(service.reprendre(any(), any())).thenThrow(new IllegalStateException("La nuit est inconnue ici."));
        gestes.selecteur().definir(selecteur(dossier.resolve("nuit.zip")));

        gestes.gestes().ouvrirPaquetRecu().run();

        assertThat(niveaux).as("le refus est dit").containsExactly(NiveauNotification.AVERTISSEMENT);
        verify(selectionVm, never()).charger(any());
    }

    @Test
    @DisplayName("Un avis refusé ne fait rien relire non plus")
    void un_avis_refuse_ne_fait_rien_relire() throws IOException {
        when(service.preparerImport(any())).thenThrow(new IllegalStateException("Ce paquet ne dit pas qui a jugé."));
        gestes.selecteur().definir(selecteur(dossier.resolve("avis.zip")));

        gestes.gestes().reprendreAvis().run();

        assertThat(niveaux).containsExactly(NiveauNotification.AVERTISSEMENT);
        verify(selectionVm, never()).charger(any());
    }

    private static SelecteurFichier selecteur(Path chemin) {
        return new SelecteurFichier() {
            @Override
            public Optional<Path> choisirDossier(String titre, Optional<Path> initial) {
                return Optional.of(chemin);
            }

            @Override
            public Optional<Path> choisirFichier(String titre, Optional<Path> initial, FiltreFichier filtre) {
                return Optional.of(chemin);
            }

            @Override
            public Optional<Path> enregistrerFichier(String titre, String nom, FiltreFichier filtre) {
                return Optional.of(chemin);
            }
        };
    }
}
