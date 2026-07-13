package fr.univ_amu.iut.commun.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.EtatTraitement;
import fr.univ_amu.iut.commun.api.Traitement;
import fr.univ_amu.iut.commun.api.TraitementVigieChiro;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.ReleveTraitementDao;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// **Point de relevé unique** de l'état du traitement (#1259) : interroger le serveur *et* s'en souvenir.
/// Client et DAO mockés, aucun réseau ni base.
class SuiviTraitementTest {

    private static final Long ID_PASSAGE = 42L;
    private static final String PARTICIPATION = "part-1";

    private TraitementVigieChiro traitement;
    private LienVigieChiroDao liens;
    private ReleveTraitementDao releves;
    private SuiviTraitement suivi;

    @BeforeEach
    void preparer() {
        traitement = mock(TraitementVigieChiro.class);
        liens = mock(LienVigieChiroDao.class);
        releves = mock(ReleveTraitementDao.class);
        suivi = new SuiviTraitement(traitement, liens, releves, Horloge.figeeAu(LocalDate.of(2026, 7, 13)));
    }

    @Test
    @DisplayName("relever : interroge le serveur ET enregistre le relevé (le cache se remplit tout seul)")
    void relever_interroge_et_memorise() {
        armerLien();
        Traitement fini = new Traitement(EtatTraitement.FINI, null, null, "2026-07-13T10:00:00+00:00", null, null);
        when(traitement.etat(PARTICIPATION)).thenReturn(fini);

        assertThat(suivi.relever(ID_PASSAGE)).isEqualTo(fini);

        verify(releves)
                .enregistrer(new ReleveTraitement(
                        ID_PASSAGE,
                        PARTICIPATION,
                        fini,
                        Horloge.figeeAu(LocalDate.of(2026, 7, 13)).maintenant().toString()));
    }

    @Test
    @DisplayName("relever : une participation jamais calculée se relève aussi (« le serveur ne dit rien » est un fait)")
    void relever_un_traitement_absent() {
        armerLien();
        when(traitement.etat(PARTICIPATION)).thenReturn(Traitement.absent());

        assertThat(suivi.relever(ID_PASSAGE).estInconnu()).isTrue();

        verify(releves).enregistrer(org.mockito.ArgumentMatchers.any(ReleveTraitement.class));
    }

    @Test
    @DisplayName("relever : passage sans participation liée → refus explicite, sans toucher au réseau")
    void relever_sans_participation_refuse() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> suivi.relever(ID_PASSAGE))
                .isInstanceOf(RegleMetierException.class)
                .hasMessageContaining("déposez d'abord");

        verify(traitement, never()).etat(org.mockito.ArgumentMatchers.anyString());
        verify(releves, never()).enregistrer(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("dernierReleve : lit le cache, sans le moindre appel réseau (utile hors connexion)")
    void dernier_releve_ne_touche_pas_au_reseau() {
        ReleveTraitement memoire = new ReleveTraitement(
                ID_PASSAGE,
                PARTICIPATION,
                new Traitement(EtatTraitement.EN_COURS, null, "2026-07-13T09:00:00+00:00", null, null, null),
                "2026-07-13T09:05:00");
        when(releves.pour(ID_PASSAGE)).thenReturn(Optional.of(memoire));

        assertThat(suivi.dernierReleve(ID_PASSAGE)).contains(memoire);

        verify(traitement, never()).etat(org.mockito.ArgumentMatchers.anyString());
    }

    private void armerLien() {
        when(liens.objectidPour(LienVigieChiro.ENTITE_PASSAGE, "42")).thenReturn(Optional.of(PARTICIPATION));
    }
}
