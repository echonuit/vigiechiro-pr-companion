package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.DonneeVigieChiro;
import fr.univ_amu.iut.commun.api.ObservationVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.RegleMetierException;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.validation.model.BilanImport;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.ServiceValidation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Orchestration de l'**import VigieChiro** ([ImportVigieChiro], axe 4.2) : résolution de la participation
/// rattachée au passage → récupération des `donnees` → import, sur client + DAO + service mockés.
@ExtendWith(MockitoExtension.class)
class ImportVigieChiroTest {

    private static final long ID_PASSAGE = 42L;
    private static final String PARTICIPATION = "6a4961f587bc8dba39481180";

    @Mock
    ClientVigieChiro client;

    @Mock
    LienVigieChiroDao liens;

    @Mock
    ServiceValidation service;

    private ImportVigieChiro importateur;

    @BeforeEach
    void preparer() {
        importateur = new ImportVigieChiro(client, liens, service);
    }

    @Test
    @DisplayName("importer : résout la participation rattachée, récupère les donnees et importe")
    void importer_de_bout_en_bout() {
        List<DonneeVigieChiro> donnees = List.of(new DonneeVigieChiro("Car-Z41_000", List.of(observation())));
        BilanImport attendu = new BilanImport(null, 1, 0, 0);
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(PARTICIPATION)).thenReturn(donnees);
        when(service.importerDepuisVigieChiro(ID_PASSAGE, donnees, false)).thenReturn(attendu);

        assertThat(importateur.importer(ID_PASSAGE, false)).isSameAs(attendu);
    }

    @Test
    @DisplayName("estRattache : reflète la présence d'un lien participation pour le passage")
    void est_rattache() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42"))
                .thenReturn(Optional.of(PARTICIPATION), Optional.empty());

        assertThat(importateur.estRattache(ID_PASSAGE)).isTrue();
        assertThat(importateur.estRattache(ID_PASSAGE)).isFalse();
    }

    @Test
    @DisplayName("passage non rattaché → refus dur, aucun appel réseau")
    void non_rattache_leve() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("rattaché");
        verify(client, never()).donnees(eq(PARTICIPATION));
    }

    @Test
    @DisplayName("aucun résultat disponible (Tadarida non terminé / hors ligne) → refus dur, aucun import")
    void aucun_resultat_leve() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
        when(client.donnees(PARTICIPATION)).thenReturn(List.of());

        assertThatThrownBy(() -> importateur.importer(ID_PASSAGE, false))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("Aucun résultat");
        verify(service, never())
                .importerDepuisVigieChiro(eq(ID_PASSAGE), org.mockito.ArgumentMatchers.any(), eq(false));
    }

    private static ObservationVigieChiro observation() {
        return new ObservationVigieChiro("Pipkuh", 0.99, 44.0, 0.8, 4.7, null, null, null);
    }
}
