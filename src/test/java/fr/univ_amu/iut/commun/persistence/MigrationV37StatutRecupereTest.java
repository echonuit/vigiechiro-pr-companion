package fr.univ_amu.iut.commun.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.StatutWorkflow;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.model.dao.NuitRecupereeDao;
import fr.univ_amu.iut.fixture.JeuDeDonneesPassage;
import fr.univ_amu.iut.passage.model.dao.PassageDao;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/// La migration **V37** rattrape les nuits déjà rapatriées, et elle doit dire exactement la même chose
/// que le code qui les reconnaît (ADR 2581).
///
/// C'est le risque propre à cette décision : le critère existe **à deux endroits** - la requête de
/// [NuitRecupereeDao], et le `WHERE` de la migration. Deux critères qui divergeraient rendraient la base
/// incohérente avec le code qui la lit, et l'écart ne se verrait qu'au moment où il fait mal.
///
/// Ce test ne relit donc pas le SQL : il **exécute la migration livrée**, puis compare son verdict, nuit
/// par nuit, à celui de `NuitRecupereeDao`. Toute divergence future le fait virer au rouge.
class MigrationV37StatutRecupereTest {

    private static final String PARTICIPATION = "6a53f5faae21902a597394d3";
    private static final String MIGRATION = "/db/migration/V37__statut_recupere.sql";

    @TempDir
    Path dossier;

    private SourceDeDonnees source;
    private PassageDao passageDao;
    private NuitRecupereeDao nuitsRecuperees;

    @BeforeEach
    void preparer() {
        source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        passageDao = new PassageDao(source);
        nuitsRecuperees = new NuitRecupereeDao(source);
    }

    /// Rejoue le fichier de migration **tel qu'il est livré**. Le relire depuis les ressources plutôt que
    /// recopier son SQL est ce qui rend ce test capable de voir une divergence : une copie ne
    /// vieillirait pas avec l'original.
    private void rejouerV37() throws IOException, SQLException {
        try (InputStream flux = Objects.requireNonNull(getClass().getResourceAsStream(MIGRATION), MIGRATION)) {
            String sql = new String(flux.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection cx = source.getConnection();
                    Statement st = cx.createStatement()) {
                st.executeUpdate(sql);
            }
        }
    }

    private void rattacher(long idPassage) {
        new LienVigieChiroDao(source)
                .upsert(new LienVigieChiro(LienVigieChiro.ENTITE_PASSAGE, String.valueOf(idPassage), PARTICIPATION));
    }

    @Test
    @DisplayName("#2581 : V37 convertit exactement les nuits que NuitRecupereeDao reconnaît, et elles seules")
    void v37_dit_la_meme_chose_que_le_code() throws IOException, SQLException {
        // Une nuit rapatriée : rattachée, aucun WAV jamais posé.
        long recuperee = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(1, 2026, "2026-04-22")
                .statut(StatutWorkflow.DEPOSE)
                .semerSquelette()
                .idPassage();
        rattacher(recuperee);

        // Une nuit que nous avons produite puis déposée : rattachée AUSSI, mais elle a ses originaux.
        long deposeeParNous = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(2, 2026, "2026-04-23")
                .statut(StatutWorkflow.DEPOSE)
                .semer()
                .idPassage();
        rattacher(deposeeParNous);

        // Une nuit sans audio mais NON rattachée : l'absence de fichiers ne prouve rien à elle seule.
        long sansAudioNiLien = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(3, 2026, "2026-04-24")
                .statut(StatutWorkflow.DEPOSE)
                .semerSquelette()
                .idPassage();

        // Une nuit encore en cours d'import : la migration ne doit toucher que des « Déposé ».
        long enCours = JeuDeDonneesPassage.dans(source)
                .carre("130711")
                .point("Z41")
                .nuit(4, 2026, "2026-04-25")
                .statut(StatutWorkflow.TRANSFORME)
                .semerSquelette()
                .idPassage();
        rattacher(enCours);

        rejouerV37();

        for (long id : new long[] {recuperee, deposeeParNous, sansAudioNiLien}) {
            StatutWorkflow apres = passageDao.findById(id).orElseThrow().statutWorkflow();
            assertThat(apres == StatutWorkflow.RECUPERE)
                    .as("passage %d : la migration et NuitRecupereeDao doivent dire la même chose", id)
                    .isEqualTo(nuitsRecuperees.estRecuperee(id));
        }
        // Contrôle explicite du sens, pour qu'un critère devenu toujours-faux des deux côtés ne passe
        // pas pour un accord : la comparaison ci-dessus serait alors vraie partout, et vide de sens.
        assertThat(passageDao.findById(recuperee).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.RECUPERE);
        assertThat(passageDao.findById(deposeeParNous).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(passageDao.findById(sansAudioNiLien).orElseThrow().statutWorkflow())
                .isEqualTo(StatutWorkflow.DEPOSE);
        assertThat(passageDao.findById(enCours).orElseThrow().statutWorkflow())
                .as("la migration ne convertit que des « Déposé »")
                .isEqualTo(StatutWorkflow.TRANSFORME);
    }
}
