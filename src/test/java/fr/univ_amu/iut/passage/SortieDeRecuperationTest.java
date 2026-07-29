package fr.univ_amu.iut.passage;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.MoteurWorkflowPassage;
import fr.univ_amu.iut.passage.model.SortieDeRecuperation;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// Le moment où une nuit cesse d'être « Récupérée » (#2581) : quand la réactivation lui a rendu son
/// audio, et **seulement** alors.
class SortieDeRecuperationTest {

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private PassageDao passageDao;
    private SortieDeRecuperation sortie;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        passageDao = new PassageDao(source);
        sortie = new SortieDeRecuperation(passageDao, new MoteurWorkflowPassage());
    }

    private long semer(StatutWorkflow statut, int numero) {
        return JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(numero, 2026, "2026-04-2" + numero)
                .statut(statut)
                .semerSquelette()
                .idPassage();
    }

    @Test
    @DisplayName("#2581 : l'audio revenu fait passer une nuit récupérée à « Déposé »")
    void audio_revenu_promeut() {
        long idPassage = semer(StatutWorkflow.RECUPERE, 1);

        sortie.promouvoirSiRecuperee(idPassage, true);

        assertThat(passageDao.findById(idPassage).orElseThrow().statutWorkflow())
                .as("elle a son son : elle n'est plus le squelette que la synchro avait rapatrié")
                .isEqualTo(StatutWorkflow.DEPOSE);
    }

    @Test
    @DisplayName("#2581 : une réactivation qui n'a rien rebranché laisse la nuit où elle était")
    void sans_audio_rien_ne_bouge() {
        long idPassage = semer(StatutWorkflow.RECUPERE, 2);

        sortie.promouvoirSiRecuperee(idPassage, false);

        assertThat(passageDao.findById(idPassage).orElseThrow().statutWorkflow())
                .as("promouvoir ici dirait « c'est réglé » d'une nuit toujours sans son, et lui retirerait"
                        + " la recommandation qui la désignait")
                .isEqualTo(StatutWorkflow.RECUPERE);
    }

    @Test
    @DisplayName("#2581 : une nuit qui n'était pas récupérée ne change pas de statut parce qu'on la réactive")
    void nuit_ordinaire_intouchee() {
        long idPassage = semer(StatutWorkflow.VERIFIE, 3);

        sortie.promouvoirSiRecuperee(idPassage, true);

        assertThat(passageDao.findById(idPassage).orElseThrow().statutWorkflow())
                .as("réactiver une nuit ordinaire ne la fait pas avancer dans le workflow")
                .isEqualTo(StatutWorkflow.VERIFIE);
    }
}
