package fr.univ_amu.iut.validation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import fr.univ_amu.iut.commun.api.ClientVigieChiro;
import fr.univ_amu.iut.commun.api.RapportSynchro;
import fr.univ_amu.iut.commun.api.ReponseApi;
import fr.univ_amu.iut.commun.api.TaxonVigieChiro;
import fr.univ_amu.iut.commun.model.LienVigieChiro;
import fr.univ_amu.iut.commun.model.Workspace;
import fr.univ_amu.iut.commun.model.dao.LienVigieChiroDao;
import fr.univ_amu.iut.commun.persistence.MigrationSchema;
import fr.univ_amu.iut.commun.persistence.SourceDeDonnees;
import fr.univ_amu.iut.validation.model.dao.TaxonDao;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Synchronisation des taxons (#717, axe 2), **API mockée**, DAO taxon et DAO de liens **réels** (base
/// migrée jetable) : on vérifie qu'à la connexion la table `taxon` est enrichie du référentiel officiel
/// et que tous les taxons officiels sont reliés à leur `objectid` ; et qu'une réponse vide (hors-ligne)
/// ne touche ni la table ni les liens.
@ExtendWith(MockitoExtension.class)
class RapprochementTaxonsTest {

    @TempDir
    Path dossier;

    @Mock
    private ClientVigieChiro client;

    private TaxonDao taxonDao;
    private LienVigieChiroDao liens;
    private RapprochementTaxons rapprochement;

    @BeforeEach
    void preparer() {
        SourceDeDonnees source = new SourceDeDonnees(new Workspace(dossier));
        new MigrationSchema(source).migrer();
        taxonDao = new TaxonDao(source);
        liens = new LienVigieChiroDao(source);
        rapprochement = new RapprochementTaxons(taxonDao, liens);
    }

    @Test
    @DisplayName("enrichit la table du référentiel officiel et relie chaque taxon à son objectid")
    void synchronise_table_et_liens() {
        // Codes volontairement hors seed (Zzz…) pour isoler l'effet de la synchro.
        when(client.taxons())
                .thenReturn(ReponseApi.succes(List.of(
                        new TaxonVigieChiro("obj1", "Zzz001", "Latinus unus"),
                        new TaxonVigieChiro("obj2", "Zzz002", "Latinus duo"))));

        Optional<RapportSynchro> rapport = rapprochement.synchroniser(client);

        assertThat(rapport).contains(new RapportSynchro("taxons", 2));
        assertThat(taxonDao.findById("Zzz001").orElseThrow().nomLatin()).isEqualTo("Latinus unus");
        assertThat(taxonDao.findById("Zzz002")).isPresent();
        assertThat(liens.tous(LienVigieChiro.ENTITE_TAXON))
                .containsOnly(Map.entry("Zzz001", "obj1"), Map.entry("Zzz002", "obj2"));
    }

    @Test
    @DisplayName("hors-ligne (aucun taxon renvoyé) : rapport vide, ni ajout en table, ni purge des liens")
    void hors_ligne_ne_touche_rien() {
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_TAXON, "Zzz001", "obj1"));
        when(client.taxons()).thenReturn(ReponseApi.succes(List.of()));

        assertThat(rapprochement.synchroniser(client)).isEmpty();

        assertThat(taxonDao.findById("Zzz001")).isEmpty();
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_TAXON, "Zzz001")).contains("obj1");
    }

    @Test
    @DisplayName("#1284 : injoignable/refusé → la garde anti-purge tient ET la cause remonte au bandeau")
    void issue_non_succes_ne_touche_rien_mais_se_dit() {
        liens.upsert(new LienVigieChiro(LienVigieChiro.ENTITE_TAXON, "Zzz001", "obj1"));
        when(client.taxons()).thenReturn(ReponseApi.injoignable("délai d'attente dépassé"));

        assertThat(rapprochement.synchroniser(client))
                .get()
                .satisfies(rapport -> assertThat(rapport.enClair())
                        .contains("taxons non synchronisés")
                        .contains("injoignable"));
        assertThat(liens.objectidPour(LienVigieChiro.ENTITE_TAXON, "Zzz001"))
                .as("garde anti-purge : le mapping acquis reste intact")
                .contains("obj1");

        when(client.taxons()).thenReturn(ReponseApi.refuse(500, "boom"));
        assertThat(rapprochement.synchroniser(client))
                .get()
                .satisfies(rapport -> assertThat(rapport.enClair())
                        .contains("non synchronisés")
                        .contains("HTTP 500"));

        when(client.taxons()).thenReturn(ReponseApi.nonConnecte());
        assertThat(rapprochement.synchroniser(client))
                .as("non connecté : le silence reste légitime")
                .isEmpty();
    }
}
