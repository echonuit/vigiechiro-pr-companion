package fr.univ_amu.iut.audio.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ParticipationVigieChiro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.commun.model.OperationAnnuleeException;
import fr.univ_amu.iut.commun.model.Progression;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Tests unitaires de [ImportVigieChiroViewModel] (axe 4.2) : disponibilité / rattachement, délégation de
/// l'import et cycle d'état IHM (en cours → bilan / échec). [ImportVigieChiro] mocké, aucun réseau.
@ExtendWith(MockitoExtension.class)
class ImportVigieChiroViewModelTest {

    private static final long ID_PASSAGE = 42L;

    @Mock
    private ImportVigieChiro importateur;

    @Test
    @DisplayName("disponible() / rattache() reflètent la présence de l'import et du lien participation")
    void disponible_et_rattache() {
        assertThat(new ImportVigieChiroViewModel(Optional.of(importateur)).disponible())
                .isTrue();
        assertThat(new ImportVigieChiroViewModel(Optional.empty()).disponible()).isFalse();

        when(importateur.estRattache(ID_PASSAGE)).thenReturn(true);
        assertThat(new ImportVigieChiroViewModel(Optional.of(importateur)).rattache(ID_PASSAGE))
                .isTrue();
        assertThat(new ImportVigieChiroViewModel(Optional.empty()).rattache(ID_PASSAGE))
                .isFalse();
    }

    @Test
    @DisplayName("importer délègue au service et renvoie son bilan")
    void importer_delegue() {
        BilanImport bilan = new BilanImport(null, 3, 0, 0);
        when(importateur.importer(ID_PASSAGE, false)).thenReturn(bilan);

        assertThat(new ImportVigieChiroViewModel(Optional.of(importateur)).importer(ID_PASSAGE, false))
                .isSameAs(bilan);
    }

    @Test
    @DisplayName("importer suivi : délègue, émet la progression par page, et lève à l'annulation (#1622)")
    void importer_suivi_progression_et_annulation() {
        BilanImport bilan = new BilanImport(null, 3, 0, 0);
        ArgumentCaptor<SuiviPagination> suiviCaptor = ArgumentCaptor.forClass(SuiviPagination.class);
        when(importateur.importerRapide(eq(ID_PASSAGE), eq(false), suiviCaptor.capture()))
                .thenReturn(bilan);
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(Optional.of(importateur));
        List<Progression> points = new ArrayList<>();
        JetonAnnulation jeton = new JetonAnnulation();

        assertThat(vm.importer(ID_PASSAGE, false, points::add, jeton)).isSameAs(bilan);

        // Le suivi transmis au service émet un point d'avancement par page (fraction + libellé « page k/n »).
        SuiviPagination suivi = suiviCaptor.getValue();
        suivi.surPage(1, 4);
        assertThat(points).singleElement().satisfies(point -> {
            assertThat(point.fraction()).isEqualTo(0.25);
            assertThat(point.libelle()).contains("page 1/4");
        });

        // Dès que l'utilisateur a demandé l'annulation, la page suivante lève : le service s'arrête net.
        jeton.annuler();
        assertThatThrownBy(() -> suivi.surPage(2, 4)).isInstanceOf(OperationAnnuleeException.class);
    }

    @Test
    @DisplayName("importer indisponible (Optional vide, contexte de capture) → refus dur")
    void importer_indisponible_leve() {
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(Optional.empty());

        assertThatThrownBy(() -> vm.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("indisponible");
    }

    @Test
    @DisplayName("participations() / rattacher() délèguent (liste vide / no-op si indisponible)")
    void participations_et_rattachement() {
        List<ParticipationVigieChiro> parts = List.of(new ParticipationVigieChiro("6a49", "Z41", "2026-07-03", "S"));
        when(importateur.participationsDisponibles()).thenReturn(ReponseApi.succes(parts));

        ImportVigieChiroViewModel present = new ImportVigieChiroViewModel(Optional.of(importateur));
        assertThat(present.participations()).isEqualTo(ReponseApi.succes(parts));
        present.rattacher(ID_PASSAGE, "6a49");
        verify(importateur).rattacher(ID_PASSAGE, "6a49");

        // #1370 : import indisponible (injecteur sans connexion) = NonConnecte, pas un faux « vide ».
        ImportVigieChiroViewModel absent = new ImportVigieChiroViewModel(Optional.empty());
        assertThat(absent.participations()).isInstanceOf(ReponseApi.NonConnecte.class);
        absent.rattacher(ID_PASSAGE, "6a49"); // sans effet, ne lève pas
    }

    @Test
    @DisplayName("cycle d'état IHM : en cours → bilan entier / échec, chaque canal se taisant à son tour")
    void cycle_etat_ihm() {
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(Optional.of(importateur));
        assertThat(vm.enCoursProperty().get()).isFalse();

        vm.marquerEnCours();
        assertThat(vm.enCoursProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).contains("Récupération");
        assertThat(vm.bilanProperty().get()).isNull();

        BilanImport bilan = new BilanImport(null, 7, 0, 0);
        vm.appliquerBilan(bilan);
        assertThat(vm.enCoursProperty().get()).isFalse();
        // Le bilan ENTIER (#2651) : la phrase n'en disait qu'un des sept nombres, et la surface le
        // traduit désormais en compte rendu chiffré.
        assertThat(vm.bilanProperty().get()).isSameAs(bilan);
        assertThat(vm.messageProperty().get())
                .as("le message d'avancement s'efface : le laisser redirait en phrase ce que la bande dit")
                .isEmpty();

        vm.echec("Token expiré");
        assertThat(vm.messageProperty().get()).isEqualTo("Token expiré");
        assertThat(vm.bilanProperty().get())
                .as("un compte rendu périmé sous un message d'erreur ferait croire à un résultat frais")
                .isNull();
    }

    @Test
    @DisplayName("#2651 : relancer un import efface le compte rendu précédent avant de travailler")
    void relancer_efface_le_compte_rendu_precedent() {
        ImportVigieChiroViewModel vm = new ImportVigieChiroViewModel(Optional.of(importateur));
        vm.appliquerBilan(new BilanImport(null, 7, 0, 0));

        vm.marquerEnCours();

        assertThat(vm.bilanProperty().get())
                .as("sans cet effacement, la bande du précédent import resterait sous l'avancement du suivant")
                .isNull();
    }
}
