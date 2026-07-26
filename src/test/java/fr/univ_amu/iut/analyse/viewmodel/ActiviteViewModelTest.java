package fr.univ_amu.iut.analyse.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.analyse.model.ContactHoraire;
import fr.univ_amu.iut.analyse.model.CourbeEspece;
import fr.univ_amu.iut.analyse.model.LargeurTranche;
import fr.univ_amu.iut.analyse.model.ServiceActivite;
import fr.univ_amu.iut.commun.model.PlageNuit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/// Vérifie la réactivité de l'[ActiviteViewModel] : liste des espèces triée, présélection des cinq plus
/// contactées, ré-agrégation au changement de tranche (sélection préservée), (dé)sélection, et fenêtre
/// nocturne exposée ou absente.
class ActiviteViewModelTest {

    private static final long PASSAGE = 1L;

    private final ServiceActivite service = mock(ServiceActivite.class);
    private final ActiviteViewModel vm = new ActiviteViewModel(service);

    @Test
    void charger_liste_les_especes_par_total_decroissant() {
        stubContacts(concat(nContacts("BARBAR", 2), nContacts("PIPKUH", 5)));

        vm.chargerPassage(PASSAGE);

        assertThat(vm.especes())
                .extracting(CourbeEspece::taxon)
                .as("l'espèce la plus contactée en tête")
                .containsExactly("PIPKUH", "BARBAR");
    }

    @Test
    void selectionne_les_cinq_plus_contactees_par_defaut() {
        List<ContactHoraire> contacts = new ArrayList<>();
        for (int total = 1; total <= 6; total++) {
            contacts.addAll(nContacts("ESP" + total, total));
        }
        stubContacts(contacts);

        vm.chargerPassage(PASSAGE);

        assertThat(vm.especesSelectionnees())
                .as("cinq espèces cochées, les plus contactées ; la sixième (ESP1) reste dehors")
                .containsExactlyInAnyOrder("ESP6", "ESP5", "ESP4", "ESP3", "ESP2");
        assertThat(vm.courbesAffichees()).hasSize(5);
    }

    @Test
    void changer_la_tranche_reagrege_et_preserve_la_selection() {
        stubContacts(List.of(
                new ContactHoraire(
                        "PIPKUH", "Pipistrelle de Kuhl", "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, 0)),
                new ContactHoraire(
                        "PIPKUH", "Pipistrelle de Kuhl", "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, 40))));
        vm.chargerPassage(PASSAGE);
        assertThat(vm.courbesAffichees().get(0).points())
                .as("à la demi-heure, 22:00 et 22:30 font deux points")
                .hasSize(2);

        vm.trancheProperty().set(LargeurTranche.HEURE);

        assertThat(vm.especesSelectionnees())
                .as("changer la tranche ne touche pas la sélection")
                .containsExactly("PIPKUH");
        assertThat(vm.courbesAffichees().get(0).points())
                .as("à l'heure pleine, les deux contacts tombent dans une seule tranche")
                .hasSize(1);
    }

    @Test
    void cocher_une_espece_l_ajoute_aux_courbes_affichees() {
        stubContacts(concat(nContacts("PIPKUH", 5), nContacts("BARBAR", 2)));
        vm.chargerPassage(PASSAGE);
        vm.especesSelectionnees().clear();
        assertThat(vm.courbesAffichees()).isEmpty();

        vm.especesSelectionnees().add("BARBAR");

        assertThat(vm.courbesAffichees())
                .singleElement()
                .extracting(CourbeEspece::taxon)
                .isEqualTo("BARBAR");
    }

    @Test
    void decocher_une_espece_la_retire_des_courbes() {
        stubContacts(nContacts("PIPKUH", 5));
        vm.chargerPassage(PASSAGE);
        assertThat(vm.courbesAffichees()).hasSize(1);

        vm.especesSelectionnees().remove("PIPKUH");

        assertThat(vm.courbesAffichees()).isEmpty();
    }

    @Test
    void la_plage_nuit_absente_donne_null() {
        stubContacts(nContacts("PIPKUH", 1));
        when(service.plageNuit(PASSAGE)).thenReturn(Optional.empty());

        vm.chargerPassage(PASSAGE);

        assertThat(vm.plageNuitProperty().get())
                .as("sans fenêtre calculable, la vue trace sans aplat")
                .isNull();
    }

    @Test
    void la_plage_nuit_presente_est_exposee() {
        PlageNuit nuit = new PlageNuit(21, 6);
        stubContacts(nContacts("PIPKUH", 1));
        when(service.plageNuit(PASSAGE)).thenReturn(Optional.of(nuit));

        vm.chargerPassage(PASSAGE);

        assertThat(vm.plageNuitProperty().get()).isEqualTo(nuit);
    }

    @Test
    void charge_l_utilisateur_agrege_tous_ses_passages_sans_aplat_unique() {
        when(service.contactsDeLUtilisateur("u-1")).thenReturn(concat(nContacts("PIPKUH", 4), nContacts("BARBAR", 2)));

        vm.chargerUtilisateur("u-1");

        assertThat(vm.especes()).extracting(CourbeEspece::taxon).containsExactly("PIPKUH", "BARBAR");
        assertThat(vm.plageNuitProperty().get())
                .as("plusieurs nuits : aucun aplat coucher/lever unique")
                .isNull();
    }

    @Test
    void un_filtre_geo_restreint_la_courbe_puis_se_relache() {
        List<ContactHoraire> contacts = new ArrayList<>();
        contacts.addAll(geoContacts("PIPKUH", 5, "640380"));
        contacts.addAll(geoContacts("BARBAR", 3, "770123"));
        when(service.contactsDeLUtilisateur("u-1")).thenReturn(contacts);
        vm.chargerUtilisateur("u-1");
        assertThat(vm.especes()).hasSize(2);

        vm.filtres().definir("carre", contact -> "640380".equals(contact.numeroCarre()));

        assertThat(vm.especes())
                .as("le filtre carré ne laisse que l'espèce de ce carré, ré-agrégée")
                .extracting(CourbeEspece::taxon)
                .containsExactly("PIPKUH");

        vm.filtres().reinitialiser();
        assertThat(vm.especes())
                .as("filtre retiré : les deux espèces reviennent")
                .hasSize(2);
    }

    @Test
    void les_valeurs_disponibles_listent_points_et_nuits_distincts() {
        when(service.contactsDeLUtilisateur("u-1"))
                .thenReturn(List.of(
                        new ContactHoraire(
                                "PIPKUH", "n", "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, 0), "640380", "A1", 1L),
                        new ContactHoraire(
                                "PIPKUH", "n", "Chiroptères", LocalDateTime.of(2026, 6, 21, 2, 0), "640380", "A1", 1L),
                        new ContactHoraire(
                                "BARBAR",
                                "n",
                                "Chiroptères",
                                LocalDateTime.of(2026, 6, 25, 23, 0),
                                "640380",
                                "B2",
                                2L)));

        vm.chargerUtilisateur("u-1");

        assertThat(vm.pointsDisponibles()).containsExactly("A1", "B2");
        assertThat(vm.nuitsDisponibles())
                .as("02:00 le 21 reste la nuit du 20 (bascule à midi) : deux nuits distinctes")
                .containsExactly("2026-06-20", "2026-06-25");
    }

    @Test
    void le_message_d_etat_vide_nomme_la_cause() {
        assertThat(vm.messageEtatVide()).as("rien chargé").contains("Aucune espèce détectée");

        when(service.contactsDeLUtilisateur("u-1")).thenReturn(nContacts("PIPKUH", 3));
        vm.chargerUtilisateur("u-1");
        vm.filtres().definir("carre", contact -> "absent".equals(contact.numeroCarre()));
        assertThat(vm.messageEtatVide()).as("tout filtré").contains("ne correspond aux filtres");

        vm.filtres().reinitialiser();
        vm.especesSelectionnees().clear();
        assertThat(vm.messageEtatVide()).as("aucune espèce cochée").contains("Aucune espèce sélectionnée");

        vm.especesSelectionnees().add("PIPKUH");
        assertThat(vm.messageEtatVide())
                .as("une courbe tracée : pas de message")
                .isEmpty();
    }

    private void stubContacts(List<ContactHoraire> contacts) {
        when(service.contactsDuPassage(PASSAGE)).thenReturn(contacts);
    }

    private static List<ContactHoraire> geoContacts(String taxon, int nombre, String carre) {
        List<ContactHoraire> contacts = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            contacts.add(new ContactHoraire(
                    taxon, taxon + " (nom)", "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, i), carre, "A1", 1L));
        }
        return contacts;
    }

    private static List<ContactHoraire> nContacts(String taxon, int nombre) {
        List<ContactHoraire> contacts = new ArrayList<>();
        for (int i = 0; i < nombre; i++) {
            contacts.add(
                    new ContactHoraire(taxon, taxon + " (nom)", "Chiroptères", LocalDateTime.of(2026, 6, 20, 22, i)));
        }
        return contacts;
    }

    private static List<ContactHoraire> concat(List<ContactHoraire> a, List<ContactHoraire> b) {
        List<ContactHoraire> tous = new ArrayList<>(a);
        tous.addAll(b);
        return tous;
    }
}
