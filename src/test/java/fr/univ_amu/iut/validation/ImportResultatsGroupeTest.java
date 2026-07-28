package fr.univ_amu.iut.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.SuiviPagination;
import fr.univ_amu.iut.commun.model.CiblePassage;
import fr.univ_amu.iut.commun.model.JetonAnnulation;
import fr.univ_amu.iut.validation.model.ImportResultatsGroupe;
import fr.univ_amu.iut.validation.model.ImportVigieChiro;
import fr.univ_amu.iut.validation.model.ResultatsIdentification;
import fr.univ_amu.iut.validation.model.dao.ResultatsIdentificationDao;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Tests de [ImportResultatsGroupe] (#2357, lot 3, PR 4/5).
///
/// Ce qui est éprouvé ici, ce sont les **refus** : hors connexion, non déposé, résultats déjà là. Et
/// surtout que l'action **ne remplace jamais** en lot - un lot qui écraserait vingt jeux de résultats
/// parce qu'on a coché vingt lignes serait un piège.
class ImportResultatsGroupeTest {

    private static final CiblePassage CIBLE = new CiblePassage(42L, "640380 / A1 / 2026 n°1");

    private ImportVigieChiro importateur;
    private ResultatsIdentificationDao resultats;

    @BeforeEach
    void preparer() {
        importateur = mock(ImportVigieChiro.class);
        resultats = mock(ResultatsIdentificationDao.class);
    }

    private ImportResultatsGroupe action() {
        return new ImportResultatsGroupe(Optional.of(importateur), resultats);
    }

    @Test
    @DisplayName("un passage déposé et sans résultats est éligible")
    void depose_sans_resultats_est_eligible() {
        when(importateur.estRattache(42L)).thenReturn(true);
        when(resultats.findByPassage(42L)).thenReturn(Optional.empty());

        assertThat(action().motifNonEligible(CIBLE)).isEmpty();
    }

    @Test
    @DisplayName("hors connexion, tout est écarté sans rien interroger")
    void hors_connexion_tout_est_ecarte() {
        ImportResultatsGroupe horsLigne = new ImportResultatsGroupe(Optional.empty(), resultats);

        assertThat(horsLigne.motifNonEligible(CIBLE)).contains("hors connexion à Vigie-Chiro");
        verifyNoInteractions(resultats);
    }

    @Test
    @DisplayName("un passage non déposé est écarté : l'import n'aurait aucune cible")
    void non_depose_est_ecarte() {
        when(importateur.estRattache(42L)).thenReturn(false);

        assertThat(action().motifNonEligible(CIBLE)).contains("pas encore déposé sur Vigie-Chiro");
        verifyNoInteractions(resultats);
    }

    @Test
    @DisplayName("un passage qui a déjà ses résultats est écarté : réimporter se décide nuit par nuit")
    void deja_importe_est_ecarte() {
        when(importateur.estRattache(42L)).thenReturn(true);
        when(resultats.findByPassage(42L)).thenReturn(Optional.of(mock(ResultatsIdentification.class)));

        assertThat(action().motifNonEligible(CIBLE)).contains("résultats déjà importés");
    }

    @Test
    @DisplayName("l'exécution n'active JAMAIS le remplacement")
    void n_ecrase_jamais() {
        action().executer(CIBLE, new JetonAnnulation());

        // `false` est le cœur de cette action : réimporter détruirait l'avis du validateur MNHN et les
        // fils de discussion, et se décide depuis l'écran de validation, pas en cochant vingt lignes.
        verify(importateur).importerRapide(eq(42L), eq(false), any(SuiviPagination.class));
    }
}
